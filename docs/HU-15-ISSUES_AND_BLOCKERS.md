# HU-15: Issues and Blockers Log

## Overview

This document tracks all bugs, blockers, and issues encountered during the implementation of HU-15 (AI Provider Migration).

**Last Updated**: 2026-10-06  
**Status**: In Progress

---

## Active Blockers

### None at this moment

---

## Resolved Issues

### None at this moment

---

## Known Limitations (Not blockers, but important context)

### 1. Testcontainers Requires Docker
**Severity**: Low  
**Affected Component**: Backend tests  
**Description**: 
- `JpaOfferingRepositoryTest` requires Docker to be running (uses Testcontainers for PostgreSQL)
- This is expected and correct behavior for integration tests
- Unit tests (Mockito-based) run without Docker

**Impact**: 
- Cannot run full test suite (`mvn test`) in environments without Docker
- Workaround: Use `mvn clean compile` to verify compilation, or run tests individually

**Resolution**: No action needed. This is by design. Integration tests should run in CI pipeline with Docker.

---

## Testing Strategy

### Current Test Status (2026-10-06)

| Test | Status | Notes |
|---|---|---|
| `BookingServiceTest` | ✅ Pass | Unit test with Mockito, no external dependencies |
| `OfferingServiceTest` | ✅ Pass | Unit test with Mockito, no external dependencies |
| `JpaOfferingRepositoryTest` | ⚠️ Skip in local | Requires Docker/Testcontainers |

### Unit Tests Without External Dependencies
- ✅ All unit tests pass
- ✅ Compilation successful with `-DskipTests`

---

## Items for Phase 4 (Provider Evaluation)

Once Phase 4 begins, the following must be documented:

- [ ] Candidate provider baseline metrics
- [ ] Latency comparison with Gemini
- [ ] Cost comparison with Gemini
- [ ] Quality assessment
- [ ] Integration complexity with Spring AI
- [ ] Any blockers found during candidate provider evaluation
- [ ] Any bugs discovered in the evaluation process

---

## Checklist: Before Phase 4

- [x] Compile successful
- [x] Unit tests pass
- [x] Configuration infrastructure in place
- [x] Error handling improved
- [x] Mock adapter created
- [x] Baseline documentation complete
- [ ] Phase 4: Evaluate candidate provider

---

## Notes for Implementation Team

### Important Reminders

1. **No API keys in code**: All API keys must be environment variables
2. **Timeout configuration**: Currently set to 10 seconds in `AiProviderProperties`
3. **Error types**: Use `AiProviderException.ErrorType` enum for consistent error categorization
4. **Mock for tests**: Always use `MockAiAdapter` in tests, never call real APIs
5. **Documentation**: Keep this file updated as issues are discovered

### Testing without External APIs

```java
// Good: Using mock
@MockBean
private AiRecommendationPort aiPort;

when(aiPort.recommend(anyString(), anyList()))
    .thenReturn("Mock recommendation");

// Bad: Calling real API in tests
var adapter = new GeminiAiAdapter(chatClientBuilder);
```

---

## Version History

| Date | Status | Changes |
|---|---|---|
| 2026-10-06 | Created | Initial log created before Phase 3 completion |

---

**Next Phase**: Phase 4 — Evaluate candidate AI provider  
**Expected Date**: After Phase 3 completion
