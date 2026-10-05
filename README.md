# idempotent-order-service

[![Java CI](https://github.com/lilsawe/idempotent-order-service/actions/workflows/ci.yml/badge.svg)](https://github.com/lilsawe/idempotent-order-service/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

一个用 Spring Boot 写的**订单服务实践项目**，聚焦后端工程里最容易出事故的三件事：**接口幂等**、**状态流转**、**对账**。

> 说明：这是个人实践项目（非生产系统），目的是把真实业务里反复用到的模式抽成可运行、可测试的最小实现。

## 为什么做这个

- 下单/支付接口被重复调用（用户连点、网络重试、消息重投）会直接造成**重复下单、重复扣款**
- 订单状态散落在各处 if-else 里维护，改一处崩一处
- 本地订单与渠道账单不一致时，需要**自动化对账**把差异分类出来

这个项目把这三件事各做成一个可独立测试的模块。

## 核心设计

### 1. 接口幂等：三层防线

| 层 | 手段 | 作用 |
|---|---|---|
| 第一层 | IdempotencyStore（Redis SETNX + TTL / 内存 CAS） | 并发请求只有第一个能抢到幂等键 |
| 第二层 | 数据库唯一索引 uk_orders_idempotency_key | 存储层兜底，进程重启、缓存丢失也不会写入重复订单 |
| 第三层 | 重放返回首次创建的订单 | 调用方拿到**完全相同**的结果，天然可重试 |

请求头携带 Idempotency-Key，同一个 key 重复调用只落库一次（单元测试用 Mockito 验证 save 只被调用 1 次）。

### 2. 状态机

```text
CREATED ──▶ PAID ──▶ SHIPPED
   │          │
   └──────────┴──▶ CANCELLED
```

OrderStateMachine 集中定义合法流转，非法流转抛 IllegalStateException（HTTP 409）。实体带 @Version 乐观锁，防止并发覆盖。

### 3. 对账

ReconcileService 把本地订单与渠道结算记录按订单号双向比对，输出四类结果：

| 类型 | 含义 |
|---|---|
| 一致（matched） | 两侧订单号与金额都相同 |
| MISSING_IN_CHANNEL | 本地有、渠道无 —— 可能漏推送 |
| MISSING_IN_LOCAL | 渠道有、本地无 —— 可能漏记或重复扣款 |
| AMOUNT_MISMATCH | 两侧都有但金额不一致 |

## 快速开始

### 方式一：H2 内存库直接跑（零依赖）

```bash
mvn spring-boot:run
```

启动后访问 http://localhost:8080 ，H2 控制台在 /h2-console （JDBC URL：jdbc:h2:mem:orders）。

### 方式二：MySQL + Redis（Docker）

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=mysql,redis
```

redis profile 会把幂等键存储从内存切换成 Redis。

## API 示例

```bash
# 创建订单（带幂等键）
curl -i -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -H "Idempotency-Key: demo-key-001" -d "{\"amountCent\": 9900}"

# 用同一个幂等键再调一次：返回同一笔订单，不会重复创建
curl -i -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -H "Idempotency-Key: demo-key-001" -d "{\"amountCent\": 9900}"

# 查询订单
curl http://localhost:8080/api/orders/{orderNo}

# 状态流转
curl -X POST http://localhost:8080/api/orders/{orderNo}/status -H "Content-Type: application/json" -d "{\"status\": \"PAID\"}"

# 触发对账（演示渠道数据会造出 1 笔金额差异 + 1 笔渠道多单）
curl http://localhost:8080/api/reconcile
```

## 测试与 CI

```bash
mvn -B -ntp verify
```

- OrderStateMachineTest：合法/非法状态流转、终态、幂等流转
- OrderServiceIdempotencyTest：同 key 只落库一次、不同 key 不同订单、唯一索引兜底、参数校验
- ReconcileServiceTest：四类差异分类、完全一致场景
- OrderDemoApplicationTests：@SpringBootTest 端到端冒烟（真实 Spring 上下文 + H2）

CI：GitHub Actions（Temurin JDK 17）执行 mvn -B -ntp verify。

## 项目结构

```text
src/main/java/com/lilsawe/orderdemo
├── api/                 # Controller、DTO、全局异常处理
├── domain/              # 实体、Repository、状态机
├── idempotency/         # 幂等键存储抽象 + Redis / 内存实现
├── reconcile/           # 对账服务、差异模型、渠道网关
└── service/             # 订单服务、订单号生成
```

## 后续可以扩展

- 幂等键存储换成 Redisson 看门狗续期，覆盖长事务场景
- 对账结果落库 + 定时任务（Spring Scheduling）做 T+1 自动对账
- 引入 Testcontainers，在 CI 里跑真实 MySQL / Redis
- 增加 Micrometer 指标：幂等命中率、对账差异数

## License

[MIT](LICENSE)
