# NMEARouter Refactoring - Quick Reference Guide

A compact, at-a-glance reference for the refactoring plan.

---

## 🚀 TL;DR - What to Do This Week

### WEEK 1 (MUST DO)

| Task | File | Line | Change | Time |
|------|------|------|--------|------|
| 🔴 Fix STW copy bug | Conf.kt | 23 | `c.bSYT` → `c.bSTW` | 5 min |
| 🔴 Cancel timer | MainActivity.kt | 114 | Add `timer.cancel()` | 2 min |
| 🔴 Fix offset assign | Data.kt | 191 | Add `offset =` | 2 min |
| ✅ Test fixes | All | - | Run tests | 10 min |
| 🟡 Remove param | BLEThing.kt | 428 | `(e: ...)` → `(_: ...)` | 2 min |
| 🟡 Remove semicolon | Conf.kt | 40 | Delete `;` | 1 min |
| ✅ Verify | - | - | `./gradlew build` | 5 min |

**Total:** ~30 minutes  
**Impact:** Fixes 3 critical bugs + 2 warnings

---

## 📋 Quick Command Reference

```bash
# Build & check
./gradlew assembleDebug          # Compile
./gradlew lintDebug              # Find warnings
./gradlew testDebugUnitTest      # Run tests

# Check specific file
./gradlew lintDebug --info | grep "Conf.kt"

# Full validation
./gradlew clean
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

---

## 🎯 Phase Overview

```
Phase 1: Critical Fixes (Week 1)
├─ Conf copy bug (c.bSYT → c.bSTW)
├─ Timer leak (add cancel)
└─ Data offset (assign offset)

Phase 2: Lint (Week 1)
├─ Remove unused parameter
└─ Remove semicolon

Phase 3: Safety (Week 2-3)
├─ Data validation
├─ BLE sync
├─ Error logging
└─ Null safety

Phase 4: Quality (Week 3-4)
├─ Extract constants
├─ Refactor parsing
├─ Remove unused code
└─ Config consistency
```

---

## 🐛 Critical Bugs at a Glance

| Bug | Impact | Fix | Time |
|-----|--------|-----|------|
| **Conf copy** | Settings lost | Change line 23 | 5 min |
| **Timer leak** | Background thread | Add cancel | 2 min |
| **Data offset** | Parsing broken | Assign return | 2 min |

---

## 📊 Effort Estimate

| Phase | Hours | Priority | Status |
|-------|-------|----------|--------|
| 1 | 4-6 | URGENT | TBD |
| 2 | 1-2 | HIGH | TBD |
| 3 | 8-10 | HIGH | TBD |
| 4 | 10-12 | MEDIUM | TBD |
| **Total** | **40-50** | - | - |

---

## ✅ Verification Steps

### After Phase 1 (Critical)
```bash
✓ Code compiles: ./gradlew assembleDebug
✓ Manual test: Device connects
✓ Manual test: Data displays
✓ Manual test: Settings save
```

### After Phase 2 (Lint)
```bash
✓ Zero warnings: ./gradlew lintDebug
```

### After Phase 3 (Safety)
```bash
✓ Tests pass: ./gradlew testDebugUnitTest
✓ No crashes with invalid data
✓ Thread safety validated
```

### After Phase 4 (Quality)
```bash
✓ Full build: ./gradlew build
✓ No regressions
✓ Code review approved
```

---

## 📁 Key Files to Touch

| File | Issues | Phases |
|------|--------|--------|
| **Conf.kt** | 3 issues | 1, 2, 4 |
| **MainActivity.kt** | 1 issue | 1 |
| **Data.kt** | 3 issues | 1, 3, 4 |
| **BLEThing.kt** | 2 issues | 2, 3, 4 |
| **N2KDataView.kt** | 1 issue | 3 |

---

## 🔍 Code Review Findings Summary

```
CRITICAL ........... 3 bugs
HIGH ............... 3 issues  
MEDIUM ............. 5 issues
LOW ................ 5 issues
GOOD ............... 7 strengths

Quality Score: 7/10 → 9+/10 (target)
```

---

## 📝 Test Coverage Targets

| Phase | Class | Target |
|-------|-------|--------|
| 1 | Conf | 100% |
| 1 | Data | 95% |
| 3 | Data | 100% |
| 3 | BLE | 80% |
| 4 | All | 65%+ |

---

## 🚨 Risk Mitigation

- **Risk Level:** LOW
- **Strategy:** Phase at a time, test after each
- **Rollback:** Easy - each phase independent
- **Testing:** Unit tests for each critical change

---

## 📞 When You're Stuck

1. **Can't compile?** → Read CODE_REVIEW_NMEARouter.md
2. **How to fix?** → Read CODE_FIXES_NMEARouter.md  
3. **What lines?** → Read REFACTORING_FILE_IMPACT_MAP.md
4. **Full plan?** → Read REFACTORING_PLAN_NMEARouter.md
5. **Tests?** → Read REFACTORING_TEST_PLAN.md

---

## 🏁 Definition of Done

- [ ] All critical bugs fixed (Phase 1)
- [ ] Zero compiler warnings (Phase 2)
- [ ] All unit tests passing (Phase 3)
- [ ] Code review approved (Phase 4)
- [ ] Manual testing complete
- [ ] Documentation updated
- [ ] Merged to main branch

---

## 📅 Sample Sprint Plan

```
Sprint 1 (Week 1):
  Day 1: Phase 1 (bugs) + Phase 2 (lint)
  Day 2-3: Code review + testing
  Day 4-5: Phase 3 start (safety)

Sprint 2 (Week 2-3):
  Phase 3: Complete (safety + error handling)
  Code review + testing

Sprint 3 (Week 3-4):
  Phase 4: Code quality
  Final testing + release
```

---

## 💡 Pro Tips

1. **Commit often:** One phase = one commit
2. **Test early:** Don't wait for end of phase
3. **Document as you go:** Add code comments
4. **Use branch:** Create feature/refactor-phase-X branches
5. **Review first:** Understand code before changing

---

## 📈 Success Metrics

| Metric | Before | After | Target |
|--------|--------|-------|--------|
| Compiler Warnings | 2 | 0 | 0 ✅ |
| Critical Bugs | 3 | 0 | 0 ✅ |
| Test Coverage | ~20% | ~60% | 60%+ ✅ |
| Code Quality | 7/10 | 9/10 | 9/10 ✅ |
| Build Time | Normal | +5% | -5% |

---

## Related Documents

📄 **CODE_REVIEW_NMEARouter.md** - Full code review findings  
📄 **CODE_FIXES_NMEARouter.md** - Before/after code examples  
📄 **REFACTORING_PLAN_NMEARouter.md** - Detailed 4-phase plan  
📄 **REFACTORING_FILE_IMPACT_MAP.md** - Exact line numbers  
📄 **REFACTORING_TEST_PLAN.md** - Comprehensive test cases  
📄 **REVIEW_SUMMARY_NMEARouter.md** - Executive summary  
📄 **AGENTS.md** - Architecture guidelines  

---

## Quick Git Workflow

```bash
# Create branch
git checkout -b refactor/phase-1-critical

# Make Phase 1 changes
# (Fix Conf, MainActivity, Data)

# Test
./gradlew assembleDebug
./gradlew testDebugUnitTest

# Commit
git add .
git commit -m "Phase 1: Fix critical bugs (Conf copy, timer leak, data offset)"

# Push
git push origin refactor/phase-1-critical

# Create PR for review
# ... merge after approval ...

# Next phase
git checkout -b refactor/phase-2-lint
# ... repeat ...
```

---

## FAQ Quick Answers

**Q: How long will this take?**  
A: 40-50 hours total, can be spread over 4-6 weeks

**Q: Can we do it faster?**  
A: Phase 1 (bugs) should be done immediately (0.5 hours), others follow

**Q: What if we skip Phase 4?**  
A: Phases 1-3 are critical; Phase 4 is optional refactoring

**Q: Will this break anything?**  
A: Low risk - each phase has tests and can be rolled back independently

**Q: Do we need to release between phases?**  
A: No - can batch all 4 phases into one release

**Q: What about the unused toByteArray() function?**  
A: Decide: remove it or add tests and document it. See Phase 4.3

---

**Created:** March 21, 2026  
**For:** NMEARouter Android Project  
**Status:** Ready for implementation

