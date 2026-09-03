# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 7/8 (87.5%)
- **Function parity:** 165/230 matched (target 251) — 71.7%
- **Class/type parity:** 20/30 matched (target 43) — 66.7%
- **Combined symbol parity:** 185/260 matched (target 294) — 71.2%
- **Average inline-code cosine:** 0.61 (function body across 7 matched files)
- **Average documentation cosine:** 0.72 (doc text across 7 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 3 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. entry_type

- **Target:** `tar.EntryType`
- **Similarity:** 0.68
- **Dependents:** 2
- **Priority Score:** 2002403.2
- **Functions:** 23/23 matched
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 15)
- **Missing types:** _none_

### 2. header

- **Target:** `tar.Header`
- **Similarity:** 0.39
- **Dependents:** 0
- **Priority Score:** 210506.1
- **Functions:** 78/96 matched (target 118)
- **Missing functions:** `set_metadata`, `set_metadata_in_mode`, `fill_from`, `fill_platform_from`, `debug_fields`, `fmt`, `cast`, `cast_mut`, `fullname_lossy`, `sparse`, `sparse_mut`, `default`, `copy_path_into_inner`, `copy`, `ends_with_slash`, `path2bytes`, `not_unicode`, `invalid_utf8`
- **Types:** 7/9 matched (target 8)
- **Missing types:** `DebugAsOctal`, `DebugSparseHeaders`

### 3. error

- **Target:** `tar.Error`
- **Similarity:** 0.48
- **Dependents:** 0
- **Priority Score:** 20605.2
- **Functions:** 3/5 matched (target 10)
- **Missing functions:** `fmt`, `from`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 4. archive

- **Target:** `tar.Archive`
- **Similarity:** 0.75
- **Dependents:** 0
- **Priority Score:** 12702.5
- **Functions:** 21/21 matched (target 27)
- **Missing functions:** _none_
- **Types:** 5/6 matched (target 5)
- **Missing types:** `Item`

### 5. pax

- **Target:** `tar.Pax`
- **Similarity:** 0.55
- **Dependents:** 0
- **Priority Score:** 11204.5
- **Functions:** 9/9 matched (target 17)
- **Missing functions:** _none_
- **Types:** 2/3 matched (target 4)
- **Missing types:** `Item`

### 6. entry

- **Target:** `tar.Entry`
- **Similarity:** 0.65
- **Dependents:** 0
- **Priority Score:** 3403.5
- **Functions:** 30/30 matched (target 55)
- **Missing functions:** _none_
- **Types:** 4/4 matched (target 9)
- **Missing types:** _none_
- **Lint issues:** 13

### 7. lib

- **Target:** `tar.Lib`
- **Similarity:** 0.78
- **Dependents:** 0
- **Priority Score:** 102.2
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

