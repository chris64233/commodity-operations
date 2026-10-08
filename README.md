# commodity-operations

用于管理大宗商品合同、库存批次、物流作业与结算资料的后端服务。

当前已实现「海运大宗商品装货计划 —— 船舶提报与码头能力预留」模块。

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

## 船舶提报与码头能力预留

### 业务模型

- 装货合同 `LoadingContract`：合同编号、合同方、允许装运数量、装期窗口（起止日期）。
- 码头每日能力 `DailyCapacity`：每个自然日的可用装货能力与已预留能力，按日期唯一。
- 船舶提报 `VesselNomination`：合同、船舶、预计到港时间（ETA）、计划装货量，状态为
  `PENDING`（待确认）/ `CONFIRMED`（已确认）/ `WITHDRAWN`（已撤回）。
- 变更历史 `NominationHistory`：提报、确认、撤回、改期均在同一事务内留痕。

### 关键规则

- 提报校验：ETA 必须落在合同装期窗口内；所有未撤回提报（含待确认与已确认）占用的数量之和
  不得超过合同允许数量，超窗口或超剩余量的申请直接拒绝，不能进入确认流程。
- 一船一计划：同一船舶任意时刻只能存在一个有效计划，提报与并发提报均通过船舶串行锁
  （`vessel_guard` 行级悲观锁）保证。
- 原子确认：确认时对当日能力行加悲观写锁，校验「已预留 + 本次 ≤ 日能力上限」后再扣减；
  校验与扣减、状态变更在同一个事务内，确认失败整体回滚，不残留能力扣减。
- 撤回：仅 `PENDING` 提报可由合同方撤回；已确认提报拒绝直接撤回。
- 改期：仅已确认计划可改期。事务内先锁定并占用新日期能力，成功后才释放旧日期能力；
  新日期不在装期窗口、未登记能力或能力不足时整体回滚，原计划（日期、状态、能力占用）
  保持不变，不会出现船舶既没有旧计划也没有新计划的中间状态。
- 幂等：提报支持客户端 `requestId`，重复（含并发）请求返回同一条提报；对已确认提报
  重复确认、对已撤回提报重复撤回均为幂等操作。

### HTTP 接口

基础路径 `/api`，请求/响应均为 JSON。

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| POST | `/contracts` | 登记装货合同（编号、允许数量、装期窗口） |
| GET  | `/contracts/{contractCode}` | 查询合同，含已占用数量与剩余数量 |
| POST | `/capacities` | 登记某日期码头每日可用能力（每日唯一） |
| GET  | `/capacities` | 查询全部日期能力：上限、已预留、剩余 |
| POST | `/nominations` | 提交船舶提报（可带 `requestId` 幂等键） |
| POST | `/nominations/{id}/confirm` | 码头确认，原子占用当日能力 |
| POST | `/nominations/{id}/withdraw` | 确认前撤回提报 |
| POST | `/nominations/{id}/reschedule` | 确认后改期（请求体含新 ETA） |
| GET  | `/nominations/{id}/history` | 查询提报变更历史 |
| GET  | `/vessels/{vesselCode}/plans` | 查询船舶当前有效计划 |

业务规则冲突返回 `409`，响应体含机器可读 `code`，常见取值：
`OUTSIDE_WINDOW`、`QUANTITY_EXCEEDED`、`VESSEL_BUSY`、`CAPACITY_EXCEEDED`、
`CAPACITY_NOT_FOUND`、`ALREADY_CONFIRMED`、`NOMINATION_INACTIVE`、`NOT_CONFIRMED`。

### 调用示例

登记合同与能力：

    curl -X POST localhost:8080/api/contracts -H 'Content-Type: application/json' -d '{
      "contractCode":"C-001","counterparty":"某矿业","allowedQuantity":10000,
      "windowStart":"2026-10-10","windowEnd":"2026-10-20"}'

    curl -X POST localhost:8080/api/capacities -H 'Content-Type: application/json' -d '{
      "date":"2026-10-10","availableCapacity":8000}'

提报、确认、改期：

    curl -X POST localhost:8080/api/nominations -H 'Content-Type: application/json' -d '{
      "contractCode":"C-001","vesselCode":"MV-01","vesselName":"开拓轮",
      "estimatedArrival":"2026-10-10T08:00:00","plannedQuantity":5000,
      "requestId":"ship-001-retry-safe"}'

    curl -X POST localhost:8080/api/nominations/1/confirm

    curl -X POST localhost:8080/api/nominations/1/reschedule \
      -H 'Content-Type: application/json' \
      -d '{"newEstimatedArrival":"2026-10-11T09:30:00"}'

### 自动化测试

- `NominationServiceTest`：超量/超窗口拒绝、一船一计划、确认前撤回、确认原子扣减、
  改期成功与失败回滚、`requestId` 幂等、剩余量/能力/计划/历史查询。
- `NominationConcurrencyTest`：三个并发确认竞争同一日能力（只允许一个成功且预留量
  不超上限）、同一船舶并发提报只有一个有效计划、相同 `requestId` 并发重复请求只建一条。

运行：

    ./mvnw test -Dtest=NominationServiceTest,NominationConcurrencyTest
