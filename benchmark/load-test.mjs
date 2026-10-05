#!/usr/bin/env node
/**
 * 可复现压测脚本（无需安装依赖，Node 18+ 自带 fetch）
 *
 * 用法：
 *   node benchmark/load-test.mjs
 *   BASE_URL=http://localhost:8080 CONCURRENCY=200 REQUESTS=2000 node benchmark/load-test.mjs
 *
 * 场景：
 *   A. 正常下单吞吐：N 个请求、C 并发、每个请求独立幂等键 -> 统计 QPS 与 P50/P95/P99
 *   B. 幂等压制：C 个并发请求共用同一个幂等键 -> 期望服务端只产生 1 笔订单
 */

const BASE_URL = process.env.BASE_URL ?? "http://localhost:8080";
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 200);
const REQUESTS = Number(process.env.REQUESTS ?? 2000);

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

async function runPool(total, concurrency, worker) {
  let next = 0;
  const latencies = [];
  let ok = 0;
  let failed = 0;
  const started = Date.now();

  async function runner() {
    while (true) {
      const i = next++;
      if (i >= total) return;
      const t0 = performance.now();
      try {
        const res = await worker(i);
        if (res.ok) ok++;
        else failed++;
      } catch {
        failed++;
      }
      latencies.push(performance.now() - t0);
    }
  }

  await Promise.all(Array.from({ length: concurrency }, runner));
  const elapsedMs = Date.now() - started;
  latencies.sort((a, b) => a - b);
  return {
    total,
    concurrency,
    ok,
    failed,
    elapsedMs,
    qps: (total / elapsedMs) * 1000,
    p50: percentile(latencies, 50),
    p95: percentile(latencies, 95),
    p99: percentile(latencies, 99),
  };
}

function report(title, r) {
  console.log("\n=== " + title + " ===");
  console.log("  请求数 " + r.total + " / 并发 " + r.concurrency);
  console.log("  成功 " + r.ok + "，失败 " + r.failed + "，耗时 " + r.elapsedMs + " ms");
  console.log("  QPS " + r.qps.toFixed(1) + "  |  P50 " + r.p50.toFixed(1) + " ms  |  P95 " + r.p95.toFixed(1) + " ms  |  P99 " + r.p99.toFixed(1) + " ms");
}

async function scenarioA() {
  const r = await runPool(REQUESTS, CONCURRENCY, async (i) =>
    fetch(BASE_URL + "/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": "bench-" + Date.now() + "-" + i },
      body: JSON.stringify({ amountCent: 1000 + i }),
    }),
  );
  report("场景 A：独立幂等键下单（吞吐）", r);
  return r;
}

async function scenarioB() {
  const sharedKey = "bench-shared-" + Date.now();
  const orderNos = new Set();
  const r = await runPool(CONCURRENCY, CONCURRENCY, async () => {
    const res = await fetch(BASE_URL + "/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": sharedKey },
      body: JSON.stringify({ amountCent: 9999 }),
    });
    if (res.ok) {
      const body = await res.json();
      orderNos.add(body.orderNo);
    }
    return res;
  });
  report("场景 B：同一幂等键并发 " + CONCURRENCY + " 次（幂等压制）", r);
  console.log("  服务端返回的不同订单号数量：" + orderNos.size + "（期望 1）");
  console.log(orderNos.size === 1 ? "  ✅ 幂等生效：并发同键只产生 1 笔订单" : "  ❌ 幂等失效：产生了 " + orderNos.size + " 笔订单");
  return { r, distinctOrders: orderNos.size };
}

(async () => {
  console.log("目标: " + BASE_URL + " | Node " + process.version);
  const a = await scenarioA();
  const b = await scenarioB();

  console.log("\n=== 汇总（可直接写入 README）===");
  console.log("  A. 独立键下单: QPS " + a.qps.toFixed(0) + "，P99 " + a.p99.toFixed(0) + " ms（" + a.total + " 请求 / " + a.concurrency + " 并发）");
  console.log("  B. 同键并发: " + CONCURRENCY + " 并发 -> " + b.distinctOrders + " 笔订单");
  process.exit(b.distinctOrders === 1 && a.failed === 0 ? 0 : 1);
})();
