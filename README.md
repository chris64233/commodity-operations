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

## 船舶提报与码头能力预留

模块位于 `com.chris64233.commodityoperations.vesselnomination`，实现海运大宗商品装货计划中的
船舶提报（Nomination）与码头每日装货能力预留。

### 业务规则

- 登记装货合同（允许装运数量、装期窗口）与码头每日可用能力。
- 提交提报时校验：计划装货量不得超出合同剩余数量，预计到港时间（ETA）必须落在装期窗口内，
  否则不能进入确认流程。
- 码头确认提报时在同一事务内对当日能力行加悲观锁并原子扣减；并发确认不会突破码头上限，
  同一船舶同一时间只能存在一个有效（已确认）计划；确认失败事务回滚，不残留能力扣减。
- 合同方可在确认前撤回提报；已确认的提报不可撤回。
- 确认后改期在同一事务内先占用新时段能力、再释放旧时段能力；新时段不可用则整体回滚，
  原计划保持不变。
- 重复确认、重复撤回、改期到原日期均为幂等操作，不产生重复扣减或重复历史记录。

### API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/nominations/contracts` | 登记装货合同 |
| POST | `/api/nominations/capacities` | 登记码头每日能力 |
| POST | `/api/nominations` | 提交船舶提报 |
| POST | `/api/nominations/{id}/confirm` | 码头确认提报 |
| POST | `/api/nominations/{id}/withdraw` | 确认前撤回提报 |
| POST | `/api/nominations/{id}/reschedule` | 已确认提报改期 |
| GET | `/api/nominations/contracts/{id}/remaining` | 查询合同剩余量 |
| GET | `/api/nominations/capacities?date=YYYY-MM-DD` | 查询每日能力 |
| GET | `/api/nominations/vessels/{vesselName}/current` | 查询船舶当前计划 |
| GET | `/api/nominations/{id}/events` | 查询提报变更历史 |

业务校验失败返回 `409` 及错误信息。

### 测试

`NominationServiceTests` 覆盖：提交校验、并发确认竞争（不突破每日上限）、同一船舶重复占用、
确认失败无残留扣减、改期失败保持原计划、改期原子迁移能力、重复请求幂等。
