# commodity-operations

用于管理大宗商品合同、库存批次、物流作业与结算资料的后端服务。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 装卸时间与滞期费结算（laytime-demurrage）

散货船装卸作业的装卸时间（laytime）与滞期费（demurrage）结算模块，
包名 `com.chris64233.commodityoperations.laytime`。

### 时间计算口径

- **事件**：`BERTH`（靠泊）、`START`（开工）、`SUSPEND`（暂停）、`RESUME`（复工）、
  `COMPLETE`（完工）。时间线一律按**事件发生时间**（occurredAt）排序重建，
  接收时间（receivedAt）仅用于识别晚到事件，不参与计时。
- **起算点**（合同 `countingBasis`，即停算规则）：
  - `ON_BERTH`：自靠泊时刻起算（NOR 递交即起算）；
  - `ON_WORK`：自实际开工时刻起算。
  - 两种口径都以完工时刻为结束点。
- **有效占用时间（已用装卸时间）** = 起算点至完工的总跨度 − 停算时间。
  停算时间取所有 `SUSPEND→RESUME` 区间与计时间隔的**交集**；暂停区间先做并集合并，
  **重叠或嵌套的暂停只扣除一次**。暂停早于靠泊、晚于完工、零长度、缺少配对
  （只有 SUSPEND 没有 RESUME，或反之）均拒绝结算。
- **超出时间** = `max(0, 已用装卸时间 − 允许装卸时间)`。
- **滞期费** = 超出秒数 / 86400 × 合同日费率（一天按连续 24 小时计），
  四舍五入到分（2 位小数）；未超出允许时间时滞期费为 0。费率与币种在登记航次时固定。
- 时间一律使用 UTC `Instant` 存储，跨夏令时、节假日不做特殊处理（本模块为连续时间口径，
  非 SHINC/SHEXX 工作日历口径）。

### 事件幂等与冲突

- 同一航次下外部事件号（externalEventNo）唯一。
- 同号且类型、发生时间均相同的重放视为幂等，响应中 `replayed=true`，结果不变。
- 同号但类型或发生时间不同返回 `409 Conflict`，不覆盖已登记内容。

### 结算版本与差额

- 每次生成结算单都会固定**合同规则快照**与**事件版本哈希**（全部事件的
  外部号/类型/发生时间/接收时间的 SHA-256）。
- 当前版本为草稿时，基于新事件重算会产生新版本，旧草稿置为 `SUPERSEDED`。
- 当前版本已确认（`CONFIRMED`）后，迟到事件**不能覆盖原结算**：重算生成新的
  差额调整版本（草稿），原版本金额、状态、确认时间永久保留；新版本记录
  `previousVersionId`、`adjustmentDelta`（本版全额 − 上版全额，可为负）和
  `adjustmentReason`（含晚到事件号）。
- 时间线无法解释时（缺少 BERTH/START/COMPLETE、完工早于开工、暂停无法配对等）
  返回 `422` 并列出具体问题；整笔事务回滚，不产生半张结算单，也不改动已确认金额。
- 所有写操作对航次行加悲观锁串行化；结算确认与重算并发时，只允许基于最新完整
  事件版本的结果成为当前版本。

### HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/laytime/voyages` | 登记航次合同（允许装卸时间秒数、日费率、币种、起算口径） |
| POST | `/api/laytime/voyages/{code}/events` | 上报作业事件（幂等/冲突） |
| POST | `/api/laytime/voyages/{code}/settlements/generate` | 生成/重算结算版本 |
| POST | `/api/laytime/voyages/{code}/settlements/recalculate` | 重算（已确认时生成差额调整版本） |
| POST | `/api/laytime/voyages/{code}/settlements/confirm-current` | 确认当前版本 |
| POST | `/api/laytime/voyages/{code}/settlements/{id}/confirm` | 确认指定版本（非当前版本返回 409） |
| GET | `/api/laytime/voyages/{code}` | 原始事件、规范化时间线、停算区间、各版结算与差额来源 |

### 测试

- `TimelineNormalizerTest` / `LaytimeCalculatorTest`：时间线重建、重叠暂停去重、
  非法时间线拒绝、两种起算口径与滞期费金额。
- `LaytimeServiceIntegrationTest`：事件幂等与冲突、结算拒绝不产生半张单据、
  迟到事件差额调整、确认后金额冻结、失败重算不影响已确认版本。
- `LaytimeConcurrencyTest`：确认与重算并发的串行化、并发重复事件提交。
- `LaytimeControllerWebTest`：HTTP 端到端流程与 400/409/422 响应。
