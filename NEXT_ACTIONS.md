# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 3/8 (37.5%)
- **Function parity:** 27/294 matched (target 34) — 9.2%
- **Class/type parity:** 2/30 matched (target 17) — 6.7%
- **Combined symbol parity:** 29/324 matched (target 51) — 9.0%
- **Average inline-code cosine:** 0.65 (function body across 3 matched files)
- **Average documentation cosine:** 0.54 (doc text across 3 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 1 files with <0.60 function similarity

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

### 2. error

- **Target:** `tar.Error`
- **Similarity:** 0.48
- **Dependents:** 0
- **Priority Score:** 20605.2
- **Functions:** 3/5 matched (target 10)
- **Missing functions:** `fmt`, `from`
- **Types:** 1/1 matched (target 2)
- **Missing types:** _none_

### 3. lib

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

