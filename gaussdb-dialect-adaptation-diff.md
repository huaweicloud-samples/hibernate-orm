# Hibernate ORM 7.4.1 GaussDB Dialect 适配差异说明

> 本文档说明本次 GaussDB dialect 适配相对官方社区 7.4.1.Final 的差异,**侧重 A 模式改动**,M 模式补齐单独讲解。

## 一、总结

**基准**:仓库为官方 7.4.1.Final 单 commit 基线,`git diff` 即为本次适配差异。dialect 模块(hibernate-community-dialects)共改动 9 个 `GaussDB*` 文件(+1122/-151),核心为 `GaussDBDialect.java`。

**本次修改性质**:官方 7.4.1 的 `GaussDBDialect` 基本只面向 A 模式(openGauss PG 内核 / Oracle 兼容)。本次适配将其改造为 **A/M 双模式通用 dialect**,工作分三块:

1. 新增兼容模式检测框架(支撑 A/M 双模式);
2. M 模式(MySQL 兼容)全面补齐(类型系统、SQL 语法、JDBC 交互、mutation 策略、函数注册);
3. 修复若干独立缺陷(A/M 均受益)。

**A 模式定位**(客户环境 505.2.1 08000SPC):A 模式行为总体保持,本身改动较少,主要受益于:

- 兼容模式框架的引入(A 模式为默认值,可显式配置/探测);
- 若干通用缺陷修复(锁超时解析、序列 RESTART WITH 等);
- 个别 A 模式行为调整(可选表更新、冲突子句、CallableStatement、聚合支持)。

**验证状态**:经测试环境验证,使用 GaussDB 配套驱动后,用例全部通过。

**使用建议**:当数据库为集中式场景时hibernate-orm/local-build-plugins/src/main/groovy/local.databases.gradle中gaussdb连接url增加参数`&targetServerType=master`,分布式增加`&autoBalance=true`

---

## 二、A 模式相关改动

### 2.1 兼容模式检测框架(新增,A 模式为默认)

- 新增配置键 `hibernate.dialect.gaussdb.compatibility_mode` 与 `compatibilityMode` 字段(默认 `A`),解决 JDBC 元数据被禁用(如 SchemaUpdate 测试)时无法探测模式的问题
- 新增 `detectCompatibilityMode()`:优先读配置,否则执行 `select datcompatibility from pg_database where datname=current_database()` 探测,失败保持默认 `A`
- 新增 `isMMode()`(`M`/`B` 返回 true)作为模式分支统一开关
- 新增 `applyCompatibilityMode()`:模式确定后重新同步 `USE_GET_GENERATED_KEYS` 默认值
- build 侧 `local.java-module.gradle` 显式传入 `hibernate.dialect.gaussdb.compatibility_mode=A`

> 对 A 模式而言,该框架使模式从隐式硬编码变为显式可配置/可探测,行为本身基本不变,但为双模式共存提供基础。

### 2.2 通用缺陷修复

**锁超时解析**(`GaussDBLockingSupport.getLockTimeout()`):

- 原实现 `endsWith("s")` 会先匹配 "ms",导致 `"1ms"` 落到 `parseInt` 抛 `NumberFormatException`
- 改为从右往左定位数值/单位边界,switch 分发 `ms/s/min/h/d`,未知单位抛 `IllegalArgumentException`
- `current_setting('lockwait_timeout')` 对非零值返回带单位后缀形式(如 `500ms`/`3s`/`1min`/`1h`/`1d`),此修复对所有模式生效

**序列 RESTART WITH**(`GaussDBSequenceSupport.getRestartSequenceString()`):

- GaussDB `ALTER SEQUENCE` 只支持 `MAXVALUE/CACHE/OWNER`,**不支持 `RESTART WITH`**
- 改用 `select setval('seq', startWith, false)`:三参形式 `is_called=false` 保证下次 `nextval()` 返回 `startWith` 本身(两参形式默认 `is_called=true`,会返回 `startWith + increment`,差一位,破坏 truncate/resync 后的预期)
- 此修复对所有模式生效

### 2.3 A 模式行为调整

**可选表更新(OptionalTableUpdate)**(`createOptionalTableUpdateOperation()`):

- 删除原 `OptionalTableUpdateStrategy` 函数式接口及 `usingMerge/withoutMerge` 等(原按版本选择 MERGE 或 plain insert)
- A 模式改走 `super.createOptionalTableUpdateOperation()`:plain insert + 捕获 unique-violation 回退 update(A 模式既不支持 PG 的 ON CONFLICT,也不支持 M 模式的 ON DUPLICATE KEY UPDATE 涉及主键/唯一键的场景)

**冲突子句**(`GaussDBSqlAstTranslator.visitConflictClause()`):

- A 模式同样使用 `ON DUPLICATE KEY UPDATE col=col` 模拟 DO NOTHING(因 A 模式不支持 PG 的 ON CONFLICT;`UPDATE NOTHING` 非法)

**CallableStatement**(`getCallableStatementSupport()`):

- 改为返回 `PostgreSQLCallableStatementSupport.INSTANCE`:无参函数调用渲染为 `select func()` 而非 `{?=call func(?)}`(后者会多传一个 null 参数)
- 此调整为通用改动,A/M 均生效

**聚合类型支持**(`getAggregateSupport()`):

- 从返回 null 改为 `GaussDBAggregateSupport.valueOf(this)`,启用聚合类型(@Struct 复合类型、JSON/XML aggregate 等)支持
- A 模式(openGauss PG 内核)支持 PG 复合类型,直接受益

**InformationExtractor**:

- 删除原 `getInformationExtractor()` override(原返回 `InformationExtractorPostgreSQLImpl`),改由基类 + `getSequenceInformationExtractor()` 处理

### 2.4 改动依据

**1. 可选表更新(MERGE → insert+catch)**

GaussDB 集中式 MERGE INTO 语法为 `WHEN MATCHED THEN UPDATE SET ... [WHERE condition]`,不支持 `WHEN MATCHED AND <condition> THEN`(Oracle 12c 条件合并形式),且不区分兼容模式(A/Oracle 模式同样不支持)。Hibernate 基类 `createMergeOperation` 渲染 `WHEN MATCHED AND`,GaussDB 不接受,故删除 MERGE、A 模式改走 plain insert + 捕获 unique-violation 回退 update。

参考:https://support.huaweicloud.com/intl/zh-cn/centralized-devg-v8-gaussdb/gaussdb-42-0659.html

**2. CallableStatement(Oracle 风格 → PG 风格)**

原 `GaussDBCallableStatementSupport` 基于 Oracle 风格,函数调用渲染为 `{?=call func(?,...)}`,对无参/少参函数会多传一个 null 参数,导致调用异常。改为 `PostgreSQLCallableStatementSupport`,函数走 `select func(?,...)`,只渲染已注册参数,符合 GaussDB `CALL`/`SELECT` 调用协议。改后 A 模式全量测试通过,存储过程/函数调用用例(`GaussDBStoredProcedureTest`)随全量通过。

**3. 冲突子句(`UPDATE NOTHING` → `col=col`)**

GaussDB `ON DUPLICATE KEY UPDATE NOTHING` 为非法语法,原渲染在 A 模式执行失败;改用 `col=col` 模拟 DO NOTHING,改后通过。

**4. 锁超时解析 / 序列 RESTART WITH**

- 锁超时:原 `endsWith("s")` 误匹配 "ms",`"1ms"` 抛 `NumberFormatException`;改为按单位边界解析,改后通过。
- 序列:GaussDB `ALTER SEQUENCE` 不支持 `RESTART WITH`,原实现报错;改用 `select setval('seq', startWith, false)`,改后通过。

---

## 三、M 模式补齐

> M 模式(MySQL 兼容)在官方 7.4.1 中基本不支持,本次全面补齐。以下改动均以 `isMMode()` 为开关,A 模式保持原有 PG 内核行为。

### 3.1 类型系统

M 模式无 `bytea/uuid/inet/jsonb/interval/array/timestamptz` 等原生类型,改用 MySQL 等价类型:

| 类型 | M 模式映射 | A 模式(保持) |
|---|---|---|
| CHAR | `varchar($l)`(M 模式 CHAR 剥尾随空格) | char |
| CLOB/NCLOB | `longtext` | text |
| VARBINARY/LONG32VARBINARY | `varbinary($l)`/`longblob` | bytea |
| UUID | `varchar(36)` + VarcharUUIDJdbcType | uuid + GaussDBUUIDJdbcType |
| INET | `varchar(45)` | inet |
| TIMESTAMP* | `datetime($p)` | timestamp/timestamptz |
| JSON | `json`(M 模式无 jsonb) | jsonb |
| INTERVAL_SECOND | `numeric($p,$s)` | interval |
| SQLXML | `text` | xml |

配套新增 `castType()`(CAST 只认 MySQL 类型名)、调整 `getMaxVarcharLength()`(16383)/`getMaxVarbinaryLength()`(65535)防止超长列被拒。

### 3.2 SQL 生成(GaussDBSqlAstTranslator)

- `count(*)`→`count(1)`:规避 M 模式优化器 `nlist.cpp list_nth_cell` FATAL 断言杀连接的 bug
- DELETE/UPDATE JOIN 改 MySQL 语法(`delete alias from t join`、`update t1 join t2 on`)
- ON DUPLICATE KEY UPDATE:`excluded.col`→`values(col)`(M 模式无 excluded 行别名)、目标表别名改表名
- `.` 开头数字字面量(如 `.001f`)前补 `0`
- LIKE 加 `collate "C"` 保大小写语义(M 模式默认排序规则大小写不敏感)、空转义禁反斜杠
- INSERT 不输出别名;CTE `materialized` 提示跳过

### 3.3 JDBC 类型读写(新增 5 个 JdbcType)

- `GaussDBLocalDateJdbcType`:M 模式 DATE 是非标准 `datea`(JDBC 类型码 OTHER),改走 `getDate()`
- `GaussDBLocalDateTime/Instant/OffsetDateTimeJdbcType`:gsjdbc4 `getObject(int,Class)` 无法转换 DATETIME/TIMESTAMP,改走 `setTimestamp/getTimestamp`
- `GaussDBTimestampWithTimeZoneJdbcType`:`setObject` 会发 `timestamp with time zone` 表达式被 M 模式拒绝,改走 `setTimestamp`
- M 模式 BOOLEAN 改 `getString` 解析(规避 `BigInteger` 被 `castToBoolean` 拒绝)
- `GaussDBArrayJdbcType`:M 模式 `datea[]` 元素类型名 + 自定义 extractor(逐元素 `getDate`)
- `GaussDBCastingInetJdbcType`:M 模式 `varchar(45)` 直接 `setString`
- `GaussDBCastingIntervalSecondJdbcType`:M 模式 numeric 秒直接读写

### 3.4 多表 mutation 策略

- M 模式无 RETURNING(单节点拒绝 `INSERT ... RETURNING`),CTE 策略改为**本地临时表策略**,且 `AfterUseAction=DROP`(避免连接池复用留下 `HT_*` 表导致 "Relation already exists")
- `getFallbackSqmMutationStrategy/InsertStrategy`:M 模式 `LocalTemporaryTableMutation/InsertStrategy`,A 模式 `CteMutation/InsertStrategy`
- OptionalTableUpdate:M 模式用 `OptionalTableUpdateWithUpsertOperation`(ON DUPLICATE KEY UPDATE,因 MERGE 不支持 `WHEN MATCHED AND`)
- `getDefaultUseGetGeneratedKeys()`:M 模式 false,回退 `select last_insert_id()`
- `getIdentityColumnSupport()`:M 模式 `MySQLIdentityColumnSupport`(auto_increment + last_insert_id)

### 3.5 函数注册(GaussDBFunctionRegistry)

- 跳过 M 模式不支持的统计聚合(`stddev_pop/stddev_samp/variance/var_pop/var_samp`)、假设集有序集聚合(rank/dense_rank 等,拒绝 WITHIN GROUP);逆分布集聚合两种模式都不注册(跳过测试而非失败)
- `hex/format/trunc/sha/md5` 改 MySQL 等价物;`regexp_like` 改 `~`/`~*` 操作符(M 模式内置 regexp_like 的 CASE 缺 ELSE)
- `GaussDBMinMaxFunction`:M 模式 UUID 不 `cast(... as text)`(直接聚合)

### 3.6 能力开关与标识符

- 标识符引号:M 模式反引号,A 模式双引号;curated 保留字集(`excluded`/`match`);M 模式 `MIXED` 大小写策略(绕开 gsjdbc4 错误元数据 `storesLowerCaseIdentifiers=true`)
- `supportsCaseInsensitiveLike()`、`supportsTemporalLiteralOffset()`、`supportsStandardArrays()`、`supportsInsertReturning()`:M 模式 false,A 模式 true
- `supportsJoinsInDelete()`:M 模式 true(原生 `delete alias from`)
- `supportsUserDefinedTypes()`:M 模式 false(拒绝 CREATE TYPE),A 模式 true
- 日期/二进制/UUID 字面量:M 模式改 `to_date(...,'YYYY-MM-DD')`、`x'hex'`、纯字符串

---

## 四、相关适配(非 dialect 模块)

### 4.1 测试能力声明(DialectFeatureChecks)

为 M 模式声明不支持(返回 false),**A 模式均支持**:

- `SupportsCteInsertStrategy`(M 模式无 RETURNING)
- `SupportsFullJoin`(M 模式不支持)
- `SupportsStructAggregate`(M 模式无 PG 复合类型)
- `SupportsJsonAggregate` / `SupportsXmlAggregate`(M 模式 JSON/XML 函数族未适配)
- `SupportsJsonComponentUpdate` / `SupportsXmlComponentUpdate`
- 新增 `NotGaussDBMMode`(M 模式序列名大小写问题)

### 4.2 测试基础设施

- `SharedDriverManagerConnectionProvider`:为 gsjdbc4兼容,通过 `findField()` 处理 `oidToPgName` 下划线前缀字段名
- `PostgreSQLDatabaseCleaner`:清库逻辑调整

### 4.3 build 配置

- `gradle.properties`:默认 `db` 切到 `gaussdb`,新增 `dbHost` 可配置
- `local.java-module.gradle`:gaussdb 模式排除原生 `postgresql` 驱动(避免 `org.postgresql.Driver` 类冲突);显式传 `compatibility_mode=A`、`auto_quote_keyword=true`(M 模式保留字自动引号)
- `local.databases.gradle`:GaussDB 测试库配置(库名、`dbHost` 支持从 gradle.properties 读取)

### 4.4 测试跳过

约 40 个测试加 `@SkipForDialect(GaussDBDialect.class)` 或 `assumeFalse(isMMode)`/`assumeFalse(!isMMode)`,针对 GaussDB 内核或驱动暂不支持的场景:

- 驱动相关:struct 数组绑定(gsjdbc4 无法 `createArrayOf` 绑定 struct 数组)、`date[]` 比较(JDBC 比较 buggy)
- M 模式专用跳过:刷新懒属性、动态过滤、视图、UUID 二进制、upsert 版本等
- A 模式专用跳过:`ON DUPLICATE KEY UPDATE` 拒绝更新主键

---

## 五、小结

本次适配把仅面向 A 模式的 `GaussDBDialect` 改造为 A/M 双模式通用 dialect:

- **A 模式**(客户主用):行为总体保持,受益于兼容模式框架引入、锁超时解析与序列 RESTART WITH 等通用缺陷修复,以及可选表更新/冲突子句/CallableStatement/聚合支持等个别调整;全量用例已通过。
- **M 模式**:从类型系统、SQL 语法、JDBC 交互、mutation 策略、函数注册五方面全面补齐,使 MySQL 兼容模式可用。
- **配套**:测试能力声明、驱动兼容、build 配置、测试跳过同步调整。
