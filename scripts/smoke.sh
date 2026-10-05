#!/usr/bin/env bash
#
# 一键冒烟：验证幂等 / 状态机 / 对账三条主线是否真的工作。
#
# 用法：
#   make run                 # 终端 1：启动服务
#   bash scripts/smoke.sh    # 终端 2：跑冒烟
#
# 退出码 0 = 全部通过；非 0 = 有失败项（可直接接进 CI）。

set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

ok()    { echo "  ✅ $1"; PASS=$((PASS + 1)); }
bad()   { echo "  ❌ $1"; FAIL=$((FAIL + 1)); }
check() { if [ "$2" = "$3" ]; then ok "$1 -> $2"; else bad "$1 -> 期望 $3，实际 $2"; fi; }
field() { echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"; }

echo "冒烟目标: $BASE"
echo

echo "[0/7] 健康检查"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health")
check "GET /actuator/health" "$code" "200"

echo "[1/7] 创建订单（期望 201）"
KEY="smoke-$(date +%s)"
code=$(curl -s -o /tmp/smoke_create.json -w '%{http_code}' -X POST "$BASE/api/orders" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d '{"amountCent": 9900}')
ORDER_NO=$(field "$(cat /tmp/smoke_create.json)" orderNo)
check "POST /api/orders" "$code" "201"
[ -n "$ORDER_NO" ] && ok "订单号 $ORDER_NO" || bad "未返回 orderNo"

echo "[2/7] 重复投递同一幂等键（期望同一订单号）"
code=$(curl -s -o /tmp/smoke_replay.json -w '%{http_code}' -X POST "$BASE/api/orders" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d '{"amountCent": 9900}')
REPLAY_NO=$(field "$(cat /tmp/smoke_replay.json)" orderNo)
check "重复投递状态码" "$code" "201"
check "订单号一致（幂等生效）" "$REPLAY_NO" "$ORDER_NO"

echo "[3/7] 缺少幂等键（期望 400）"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders" \
  -H "Content-Type: application/json" -d '{"amountCent": 100}')
check "POST 无 Idempotency-Key" "$code" "400"

echo "[4/7] 非法状态流转 CREATED -> SHIPPED（期望 409）"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders/$ORDER_NO/status" \
  -H "Content-Type: application/json" -d '{"status":"SHIPPED"}')
check "非法流转" "$code" "409"

echo "[5/7] 合法状态流转 CREATED -> PAID（期望 200 + PAID）"
code=$(curl -s -o /tmp/smoke_paid.json -w '%{http_code}' -X POST "$BASE/api/orders/$ORDER_NO/status" \
  -H "Content-Type: application/json" -d '{"status":"PAID"}')
STATUS=$(field "$(cat /tmp/smoke_paid.json)" status)
check "合法流转" "$code" "200"
check "订单状态" "$STATUS" "PAID"

echo "[6/7] 20 并发共用同一幂等键（期望只产生 1 笔订单）"
CONC_KEY="smoke-conc-$(date +%s)"
rm -f /tmp/smoke_conc.txt
for _ in $(seq 1 20); do
  ( curl -s -X POST "$BASE/api/orders" -H "Content-Type: application/json" \
      -H "Idempotency-Key: $CONC_KEY" -d '{"amountCent": 555}' \
    | sed -n 's/.*"orderNo":"\([^"]*\)".*/\1/p' >> /tmp/smoke_conc.txt ) &
done
wait
DISTINCT=$(sort -u /tmp/smoke_conc.txt | grep -c . || true)
check "不同订单号数量" "$DISTINCT" "1"

echo "[7/7] 触发对账（期望 200 且含 diffs）"
code=$(curl -s -o /tmp/smoke_reconcile.json -w '%{http_code}' "$BASE/api/reconcile")
check "GET /api/reconcile" "$code" "200"
if grep -q '"diffs"' /tmp/smoke_reconcile.json; then ok "返回差异明细"; else bad "缺少 diffs 字段"; fi

echo
echo "==================================="
echo "通过 $PASS 项，失败 $FAIL 项"
echo "==================================="
[ "$FAIL" -eq 0 ] || exit 1
