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

## 暂定价、点价与最终结算

合同登记时按暂定单价形成暂定应收/应付；买卖双方随后可在约定点价窗口内，
引用允许的市场价格来源进行一次或多次部分点价；全部数量点价后方可生成最终结算。

### 金额与精度

- 金额、数量、价格全程使用 `BigDecimal`，不使用 `double/float`。
- 价格（含升贴水）保留 8 位小数，金额保留 6 位小数，舍入统一 `HALF_UP`，
  见 `MoneyCalculations`。JSON 中 `BigDecimal` 以普通十进制数字输出（无科学计数法）。

### 业务规则

- 登记合同：商品、方向（`BUY`/`SELL`）、合同数量、计价币种、暂定价格、
  点价窗口（起止时间）和允许引用的市场价格来源；登记同时生成第 1 版
  `PROVISIONAL` 暂定结算，暂定金额 = 暂定单价 × 合同数量。
- 点价：一次点价可覆盖部分未点价数量，固定市场价格版本、升贴水和点价时间；
  固定单价 = 市场价 + 升贴水，本次价差调整 =（固定单价 − 暂定价）× 点价数量。
  合同行级悲观锁串行化并发点价，累计点价数量绝不会超过合同数量，超额请求被拒绝
  （422）且合同余额不变。
- 版本可追溯：每次点价只追加一版 `PRICING_ADJUSTMENT`，历史版本永不覆盖；
  每版记录本次差额、累计调整与累计总额。
- 最终结算：仅当未点价数量为 0 时才能生成唯一的 `FINAL` 版本；重复请求幂等。
  最终结算后任何迟到点价一律拒绝；最终结算与迟到点价并发时由行锁保证唯一结果。
- 外部点价号幂等：同号同内容重复提交返回原结果（不新增点价、不新增版本）；
  数量、价格版本或升贴水变化返回 409 冲突。
- 点价校验（任一不满足即 422 拒绝，且不改变合同余额）：
  市场价格版本存在且在点价时间处于有效期内、币种与合同一致、商品一致、
  价格来源在合同允许列表中、点价时间落在点价窗口内。

### HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/market-prices` | 发布不可变的市场价格版本（来源、版本号、币种、有效区间） |
| GET | `/api/market-prices` | 查询全部市场价格版本 |
| POST | `/api/contracts` | 登记合同并生成暂定结算版本（201） |
| GET | `/api/contracts/{contractNo}` | 查询合同：未点价数量、各次价格快照、暂定金额、累计调整、最终金额、全部结算版本 |
| POST | `/api/contracts/{id}/pricings` | 提交点价（同号同内容幂等，内容变化 409） |
| POST | `/api/contracts/{id}/final-settlement` | 全部点价后生成最终结算（幂等） |

错误响应统一为 `{timestamp,status,error,message,path}`：参数错误 400、
业务规则冲突 409、其余业务拒绝 422、合同不存在 404。

### 示例

登记合同：

    POST /api/contracts
    {
      "contractNo": "CT-2026-001",
      "commodity": "COPPER",
      "direction": "BUY",
      "quantity": "100",
      "currency": "USD",
      "provisionalPrice": "10000.00",
      "pricingWindowStart": "2026-10-01T00:00:00Z",
      "pricingWindowEnd": "2026-11-30T23:59:59Z",
      "allowedSources": ["LME", "SHFE"]
    }

发布市场价格版本并点价：

    POST /api/market-prices
    {
      "source": "LME", "priceVersion": "2026-10-08",
      "commodity": "COPPER", "currency": "USD", "price": "10100.00",
      "validFrom": "2026-10-08T00:00:00Z",
      "validTo": "2026-10-09T00:00:00Z"
    }

    POST /api/contracts/1/pricings
    {
      "externalPricingNo": "EXT-P-0001",
      "quantity": "40",
      "priceSource": "LME",
      "priceVersion": "2026-10-08",
      "premiumDiscount": "5.50",
      "pricedAt": "2026-10-08T10:00:00Z"
    }

### 测试

    ./mvnw clean test

- `PricingFlowIntegrationTest`：登记精度、部分点价与版本追加、超额拒绝、
  幂等/冲突、过期价格/币种不符/窗口越界/来源不允许、最终结算与迟到点价。
- `PricingConcurrencyTest`：10 路并发点价只消耗未点价数量、
  同外部点价号并发只产生一笔、最终结算与迟到点价并发结果唯一。
- `ContractApiWebTest`：HTTP 全链路状态码与响应字段。
