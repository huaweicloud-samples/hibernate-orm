# GaussDB 方言适配总结

> 本文档列出**我方适配新增**的跳过用例（不含 Hibernate 上游自带）。
> **最新验证（2026-07-23）**：全量 `hibernate-core:test` A 模式 BUILD SUCCESSFUL，4473 测试类，0 failures / 0 errors。

---

## 一、A 模式不支持的功能（客户核心关注）

A 模式（openGauss Oracle 兼容 / PG 内核）功能基本完整，仅以下不支持：

| # | 功能 | 跳过模式 | 原因 | 方法数 |
|---|------|---------|------|--------|
| 1 | ON CONFLICT 语法 | A 跳 M 跑 | A 不支持 `INSERT...ON CONFLICT`（DO NOTHING/UPDATE 均报语法错误）；可用 `ON DUPLICATE KEY UPDATE` 替代（仅含主键表 do-nothing upsert 除外） | 8 |
| 2 | hypothetical-set WITHIN GROUP | M+A 均跳 | 不支持 `rank(5) within group(...)`（GaussDB 把 ORDER BY 当第二参数，报 "Function rank(integer,integer) does not exist"）；**A 支持窗口用法** `rank() over(...)`（我方已为 A 注册） | 5 |
| 3 | struct 数组 | M+A 均跳 | gsjdbc4 无法通过 createArrayOf 绑定 struct 数组 | 1 |
| 4 | struct 存储过程 | M+A 均跳 | gsjdbc4 拒绝 struct 类型 OUT 参数注册（"This statement does not declare an OUT parameter"） | 1 |
| 5 | JSON `=` 运算符 | M+A 均跳 | GaussDB 用 json 类型（非 jsonb），无 `=` 运算符 | 1 |
| 6 | 保留字 `end` | M+A 均跳 | 保留字集（curated set）不含 ANSI 关键字 `end` | 1 |
| 7 | 行锁超时 | M+A 均跳 | `lockwait_timeout` 管对象锁非 DML 行锁 | 1 |

**A 模式合计跳过 18 个方法**（8 A-only + 10 双模式）。

> 说明：第 2 项 hypothetical-set，A 模式**窗口用法正常**（rank / dense_rank / percent_rank / cume_dist over()），仅 WITHIN GROUP 用法不支持。我方在 GaussDBFunctionRegistry 为 A 模式注册了这些函数的窗口用法。

---

## 二、M 模式不支持的功能（A 模式均支持，A 跑 M 跳）

M 模式（MySQL 兼容内核）相对 PG 内核缺失较多，A 模式以下功能均正常。**本次适配将部分原双模式跳过改为仅 M 跳过，A 模式多跑 11 个方法**：

| 功能 | 方法数 | M 模式原因 | A 模式 |
|------|--------|-----------|--------|
| cast VARCHAR(255) | 6 | M 不支持（同 MySQLDialect），Staff 实体硬编码 cast | 支持（PG 内核） |
| current_date | 1 | M current_date 带当前时间（非午夜） | 纯日期（午夜） |
| timestampadd TIME | 1 | M 对 TIME 负值回绕 | 正常 |
| cast varchar as binary | 1 | M `cast(varchar as binary)` 报错 | 支持 cast as bytea |
| rank 窗口 | 1 | M 不支持 | 支持（已注册 hypothetical-set） |
| percent_rank/cume_dist 窗口 | 1 | M 不支持 | 支持（已注册） |
| **本次改 M-only 小计** | **11** | | **A 跑通** |

> 另有 M 模式其他限制（数组类型、样本统计 var_samp/var_pop、序列名大小写、dynamic filter BETWEEN、native query 类型映射、generated identity/values、formula、count 表达式、criteria update/delete join、schema/view/discriminator、eager to-many where 等）约 36 方法，A 模式均支持。

---

## 三、本次 A 模式适配改动明细

1. **GaussDBFunctionRegistry**：A 模式注册 hypothetical-set 聚合函数（rank / dense_rank / percent_rank / cume_dist），支持窗口（OVER）用法；M 模式不注册（M 不支持）。
2. **6 处双模式 skip → M-only**（`@SkipForDialect(GaussDBDialect)` → `@RequiresDialectFeature(NotGaussDBMMode)`），A 模式跑通：
   - `ColumnTransformerTest:43`（cast VARCHAR，类级 6 方法）
   - `FunctionTests:646`（current_date）、`:1148`（cast binary）、`:2025`（timestampadd）
   - `CriteriaWindowFunctionTest:239`（rank 窗口）、`:268`（percent_rank/cume_dist 窗口）
3. **5 处 WITHIN GROUP 新增双模式 skip**（M+A 均不支持 within group）：
   - `OrderedSetAggregateTest:169 / 180 / 191`
   - `CriteriaOrderedSetAggregateTest:285 / 301`
4. **3 处保持双模式**（A 也不支持，核实后未改）：
   - `StructAggregateEmbeddableInheritanceTest:166`（struct 存储过程，gsjdbc4 OUT 参数）
   - `KeywordRecognitionTests:27`（保留字 end，curated set）
   - `JsonMappingTests:298`（json = 运算符）

---

## 四、跳过统计

| 类别 | 方法数 | 说明 |
|------|--------|------|
| A 模式独有（A 跳 M 跑） | 8 | ON CONFLICT |
| M 模式独有（M 跳 A 跑） | 47 | 含本次改 M-only 的 11 |
| 双模式（M+A 均跳） | 10 | within group 5 + struct 数组/存储过程 + json= + end + 行锁 |
| **我方新增合计** | **65** | |

hibernate-core:test 报告总 skipped 中，我方新增约 65 个，其余为 Hibernate 标准数据库过滤（按数据库类型自动跳过不相关测试，与 GaussDB 适配无关）。

---

## 五、验证

- 命令：`.\gradlew clean hibernate-core:test -Pdb=gaussdb`
- 环境：A 模式 a_db，远程 GaussDB 505.2.1 08000SPC（115.120.232.30:8000）
- 结果：**BUILD SUCCESSFUL**，4473 测试类，0 failures / 0 errors

---

## 六、结论

1. **A 模式功能基本完整**：核心限制仅 ON CONFLICT（可用 ON DUPLICATE KEY 替代，仅含主键表 do-nothing upsert 除外）；hypothetical-set 仅窗口支持（within group 不支持）；struct 存储过程 / 保留字 end / json= 为 M+A 共同限制。
2. **本次适配让 A 模式多跑 11 个测试方法**（cast / current_date / timestampadd / 窗口函数等），A 模式覆盖更完整。
3. M 模式限制较多（MySQL 兼容内核相对 PG 内核缺失），均为内核限制，A 模式不受影响。
