# GaussDB Dialect Adaptation Summary

> This document lists **skip cases added by our adaptation** (excluding Hibernate upstream skips).
> **Latest verification (2026-07-23)**: full `hibernate-core:test` in A mode BUILD SUCCESSFUL, 4473 test classes, 0 failures / 0 errors.

---

## 1. Features Unsupported in A Mode

A mode (openGauss Oracle-compatible / PG kernel) is functionally complete, with only the following unsupported:

| # | Feature | Skip mode | Reason | Methods |
|---|---------|-----------|--------|---------|
| 1 | ON CONFLICT syntax | A skips, M runs | A does not support `INSERT...ON CONFLICT` (both DO NOTHING/UPDATE report a syntax error); `ON DUPLICATE KEY UPDATE` can be used instead (except do-nothing upsert for PK-only tables) | 8 |
| 2 | hypothetical-set WITHIN GROUP | M+A both skip | Does not support `rank(5) within group(...)` (GaussDB treats the within-group ORDER BY as a second argument, reporting "Function rank(integer,integer) does not exist"); **A supports window usage** `rank() over(...)` (we registered it for A) | 5 |
| 3 | struct array | M+A both skip | gsjdbc4 cannot bind struct arrays via createArrayOf | 1 |
| 4 | struct stored procedure | M+A both skip | gsjdbc4 rejects struct OUT parameter registration ("This statement does not declare an OUT parameter") | 1 |
| 5 | JSON `=` operator | M+A both skip | GaussDB uses the json type (not jsonb), which has no `=` operator | 1 |
| 6 | reserved word `end` | M+A both skip | The reserved word set (curated set) does not contain the ANSI keyword `end` | 1 |
| 7 | row lock timeout | M+A both skip | `lockwait_timeout` governs object locks, not DML row locks | 1 |

**A mode skips 18 methods in total** (8 A-only + 10 dual-mode).

> Note: For item 2 (hypothetical-set), A mode **window usage works normally** (rank / dense_rank / percent_rank / cume_dist over()); only the WITHIN GROUP usage is unsupported. We registered the window usage of these functions for A mode in GaussDBFunctionRegistry.

---

## 2. Features Unsupported in M Mode (all supported in A mode; A runs, M skips)

M mode (MySQL-compatible kernel) has more gaps relative to the PG kernel; A mode supports all of the following. **This adaptation changed some original dual-mode skips to M-only, so A mode now runs 11 more methods**:

| Feature | Methods | M mode reason | A mode |
|---------|---------|---------------|--------|
| cast VARCHAR(255) | 6 | M unsupported (same as MySQLDialect); the Staff entity hardcodes the cast | supported (PG kernel) |
| current_date | 1 | M current_date carries the current time (not midnight) | pure date (midnight) |
| timestampadd TIME | 1 | M wraps negative values for TIME | normal |
| cast varchar as binary | 1 | M `cast(varchar as binary)` errors | supported as cast to bytea |
| rank window | 1 | M unsupported | supported (hypothetical-set registered) |
| percent_rank/cume_dist window | 1 | M unsupported | supported (registered) |
| **M-only subtotal this round** | **11** | | **A runs** |

> Additionally, M mode has other limitations (array types, sample statistics var_samp/var_pop, sequence name case folding, dynamic filter BETWEEN, native query type mapping, generated identity/values, formula, count expressions, criteria update/delete join, schema/view/discriminator, eager to-many where, etc.) affecting about 36 methods, all of which are supported in A mode.

---

## 3. Skip Statistics

| Category | Methods | Description |
|----------|---------|-------------|
| A mode only (A skips, M runs) | 8 | ON CONFLICT |
| M mode only (M skips, A runs) | 47 | includes the 11 changed to M-only this round |
| Dual mode (M+A both skip) | 10 | within group 5 + struct array/stored procedure + json= + end + row lock |
| **Our additions total** | **65** | |

Of the total skipped reported by hibernate-core:test, our additions account for about 65; the remainder is Hibernate's standard database filtering (automatically skipping tests irrelevant to the current database type, unrelated to the GaussDB adaptation).

---

## 4. Verification

- Command: `.\gradlew clean hibernate-core:test -Pdb=gaussdb`
- Environment: A mode a_db, remote GaussDB 505.2.1 08000SPC (115.120.232.30:8000)
- Result: **BUILD SUCCESSFUL**, 4473 test classes, 0 failures / 0 errors

---

## 5. Conclusion

1. **A mode is functionally complete**: the only core limitation is ON CONFLICT (usable via ON DUPLICATE KEY as an alternative, except do-nothing upsert for PK-only tables); hypothetical-set supports only window usage (within group unsupported); struct stored procedure / reserved word `end` / json `=` are M+A common limitations.
2. M mode has more limitations (MySQL-compatible kernel gaps relative to the PG kernel); all are kernel-level and do not affect A mode.
