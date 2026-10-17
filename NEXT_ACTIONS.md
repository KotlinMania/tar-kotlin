# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 7/8 (87.5%)
- **Function parity:** 259/293 matched (target 316) — 88.4%
- **Class/type parity:** 26/30 matched (target 51) — 86.7%
- **Combined symbol parity:** 285/323 matched (target 367) — 88.2%
- **Average inline-code cosine:** 0.14 (function body across 7 matched files)
- **Average documentation cosine:** 0.75 (doc text across 7 matched files)
- **Cheat-zeroed Files:** 1
- **Critical Issues:** 7 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 0 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. entry_type

- **Target:** `tar.EntryType`
- **Similarity:** 0.20
- **Dependents:** 2
- **Priority Score:** 2002408.0
- **Functions:** 23/23 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 15)
- **Missing types:** _none_

### 2. header

- **Target:** `tar.Header`
- **Similarity:** 0.10
- **Dependents:** 0
- **Priority Score:** 274909.0
- **Functions:** 116/140 matched (target 132)
- **Missing functions:** `_set_link_name`, `_set_link_name_literal`, `fill_platform_from`, `entry_type`, `fill_platform_from`, `debug_fields`, `fmt`, `cast`, `cast_mut`, `fmt`, `fmt`, `path_lossy`, `_set_path`, `fmt`, `fmt`, `fmt`, `fmt`, `default`, `copy`, `ends_with_slash`, `ends_with_slash`, `path2bytes`, `bytes2path`, `bytes2path`
- **Types:** 7/9 matched
- **Missing types:** `DebugAsOctal`, `DebugSparseHeaders`

### 3. entry

- **Target:** `tar.Entry [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 65110.0
- **Functions:** 41/47 matched (target 57)
- **Missing functions:** `symlink`, `symlink`, `_set_ownerships`, `_set_perms`, `_set_perms`, `set_xattrs`
- **Types:** 4/4 matched (target 9)
- **Missing types:** _none_
- **Lint issues:** 17

### 4. builder

- **Target:** `tar.Builder`
- **Similarity:** 0.24
- **Dependents:** 0
- **Priority Score:** 25107.6
- **Functions:** 43/45 matched (target 48)
- **Missing functions:** `test_find_sparse_entries`, `loose_check_sparse_entries`
- **Types:** 6/6 matched (target 7)
- **Missing types:** _none_
- **Tests:** 0/2 matched

### 5. error

- **Target:** `tar.Error`
- **Similarity:** 0.07
- **Dependents:** 0
- **Priority Score:** 20609.3
- **Functions:** 3/5 matched (target 11)
- **Missing functions:** `fmt`, `from`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 6. archive

- **Target:** `tar.Archive`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 13007.2
- **Functions:** 24/24 matched (target 28)
- **Missing functions:** _none_
- **Types:** 5/6 matched (target 5)
- **Missing types:** `Item`

### 7. pax

- **Target:** `tar.Pax`
- **Similarity:** 0.07
- **Dependents:** 0
- **Priority Score:** 11209.3
- **Functions:** 9/9 matched (target 17)
- **Missing functions:** _none_
- **Types:** 2/3 matched (target 4)
- **Missing types:** `Item`

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |

