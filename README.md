# idempotent-order-service

[![Java CI](https://github.com/lilsawe/idempotent-order-service/actions/workflows/ci.yml/badge.svg)](https://github.com/lilsawe/idempotent-order-service/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> A minimal, production-shaped Spring Boot service demonstrating **idempotent order creation**, an **order state machine**, and **two-way reconciliation** — 13 tests + CI.

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

## 面试考点（把项目讲成答案）

| 问题 | 答案要点 |
|---|---|
| **幂等有哪几种方案？怎么选？** | ①**唯一索引**：强一致必防重（下单/支付），需设计幂等键，冲突抛异常；②**幂等表**：需记录请求与结果映射，多一次写库；③**Redis SETNX + TTL**：高并发、可容忍极小概率漏防，缓存丢了靠数据库兜底；④**状态机前置校验**：只覆盖状态类操作；⑤**分布式锁**：串行化临界区，性能损耗大且不解决「重复请求返回什么」。本项目=③+①+「返回首次结果」 |
| **有幂等键存储了，为什么还要唯一索引？** | 缓存会丢（重启/过期/被清）。唯一索引是**存储层最后一道闸门**：即使幂等键失效，两个并发请求也绝不可能写入两行订单——**性能靠缓存，正确性靠数据库** |
| **重复请求应该返回什么？** | 返回**首次创建的那笔订单**（相同 orderNo 与内容），而不是报错。调用方拿到可预期结果，天然支持重试 |
| **幂等键 TTL 怎么定？** | 取 **24h**。原则：大于业务可能的重试窗口（客户端重试、MQ 重投、人工补单），又不能让键无限堆积；支付类常 24h~7d，配合唯一索引可永久防重 |
| **两个请求同时进来会怎样？** | SETNX 原子操作只有一个能成功；未抢到的直接**回查并返回同一笔订单**；极端情况下（键刚过期）唯一索引兜底 |
| **为什么把状态流转抽成状态机？** | ①合法流转集中一处，改规则不用满项目找 if-else；②非法流转统一抛异常（409），接口语义清晰；③状态机是纯函数，**可脱离数据库单测** |
| **并发改状态怎么防覆盖？** | JPA 的 `@Version` 乐观锁：更新带版本号，冲突抛 OptimisticLockingFailureException。订单属「读多写少、冲突概率低」，乐观锁比悲观锁吞吐更好 |
| **对账能对出哪几类差异？** | 四类：一致 / **本地有渠道无**（漏推送、渠道未结算）/ **渠道有本地无**（漏记账、重复扣款）/ **金额不一致**（费率、退款、部分结算）。定位顺序：订单号双向 diff → 按金额与状态分层 → 与渠道流水逐笔核对 |
| **金额为什么用 long（分）不用 double？** | 浮点有精度误差（0.1+0.2 != 0.3），资金场景必须用**最小货币单位的整数**（分）或 BigDecimal。本项目全链路用分 |
| **这个项目离生产还差什么？** | 见下节「已知限制」——面试时**主动说出差距**远好于被问出来 |

## 设计取舍 / 已知限制 / 下一步

### 设计取舍（为什么这么做）

| 决策 | 选择 | 为什么 |
|---|---|---|
| 幂等键存储 | 抽象 `IdempotencyStore`，默认内存、可切 Redis | 单元测试**不依赖外部组件**，同时保留生产形态 |
| 默认数据库 | H2 内存库 | 克隆后 `mvn spring-boot:run` 直接能跑，降低他人上手成本 |
| 对账数据源 | `ChannelGateway` 接口 + 演示实现 | 真实渠道账单不宜开源，用接口隔离并留可替换点 |
| 状态流转 | 独立状态机类，不引工作流引擎 | 四个状态用引擎属过度设计 |
| 冲突处理 | 乐观锁 `@Version` | 订单写冲突概率低，乐观锁吞吐更好 |

### 已知限制（诚实说明）

- **单库单表**：无分库分表、无分布式事务（真实资金链路需 TCC/Saga 或 Seata）
- **对账结果不落库**：只返回报告，没有 T+1 任务与差异工单流转
- **无鉴权 / 限流**：未接入认证、风控与接口限流
- **幂等键 TTL 固定 24h**，未按业务分级
- **演示渠道网关**按本地订单造数据，仅用于展示对账输出

### 下一步（如果继续做）

1. 幂等键换 Redisson（看门狗续期），覆盖长事务
2. 对账结果落库 + Spring Scheduling 做 T+1 自动对账与差异告警
3. Testcontainers 在 CI 里跑真实 MySQL / Redis
4. Micrometer 指标：幂等命中率、对账差异数、状态流转耗时
5. 接入 Seata，演示跨服务下单 + 扣款的分布式事务
## License

[MIT](LICENSE)
