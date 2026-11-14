# A02 Speed Sample Quality Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline, with a fresh final reviewer.

**Goal:** Replay the existing A02 logs and compare conservative EXPERIMENTAL candidates without changing production behavior.
**Architecture:** A standalone shadow owns an unchanged SpeedEventBridge for validation and legacy comparison. A bounded window retains original observations and compares endpoint acceleration with sustained, assumed-quantization-adjusted acceleration. Offline JUnit tooling distinguishes observations from recorded consumed results.
**Tech Stack:** Existing Kotlin/JUnit4/Gradle; no dependencies.
**Spec:** User attachment f72bd64d-4853-4c3e-93ca-67aa43c1dd7c (A02-Q1 through Q4).

## Global Constraints

- No changes to existing engine/bridge contracts, thresholds 2.5 and 12/3.6, runtime source, gates, B/C/Web, MainActivity, manifest, Gradle configuration, or A02_SPEED_EVENTS.md.
- EXPERIMENTAL comparison only; raw signed Float m/s and elapsed ns retained. No real accuracy certification.
- Keep main and previous worktrees/branches/logs unchanged. Feature -> dev only after tests and CI.

## Review Focus

- Invalid timestamps, missing metadata and unknown prehistory cannot silently become valid evidence.
- Reversed receipt clocks, stale values and late sessions break continuity.
- A sustained window may miss near-threshold changes; report sensitivity instead of promoting defaults.
- An isolated spike must not become a sustained candidate across the midpoint.
- Replay receipt time is an explicitly assumed processing clock, not an observed processing timestamp.

### Task 1: Standalone quality shadow

Files: core/event/SpeedQualityShadow.kt and matching unit tests.
Interface: start(sessionId), stop(), accept(VehicleSpeedObservation, nowNanos): SpeedQualityResult?; independent candidate type, untouched legacy result and retained raw observations.
- [ ] Write failing tests for quantized steps, irregular intervals, gentle/strong changes, spike, constant, missing callbacks, reversed/duplicate time, invalid statuses, signed negatives, source/session changes and legacy equality.
- [ ] Run filtered tests and retain RED evidence.
- [ ] Implement bounded 600–1000ms window, minimum 5 samples, max gap 300ms, endpoint-only vs sustained/assumed quantum modes. Independent episode/cooldown; no live hookup.
- [ ] Run filtered tests GREEN.

### Task 2: Offline replay and comparison

Files: core/event/SpeedLogReplay.kt and SpeedLogReplayTest.kt under test sources; docs/evidence/a02-quality-2026-11-14 outputs.
Interface: strict parser of original observation fields and separate consumed lines, replay with receipt as explicit analysis clock; CSV and Markdown output via A02_REPLAY_REPORT_DIR environment variable.
- [ ] Write parser/replay tests including missing fields, Float preservation and cold-excerpt prehistory limitations.
- [ ] Verify RED then implement parser and reports without changing fixtures.
- [ ] Run fixture replay and synthetic comparison; preserve SHA256 hashes and every observation field, acceleration, rejection/suppression and candidate times.

### Task 3: Evidence and integration

Files: docs/A02_SAMPLE_QUALITY.md; append IMPLEMENTATION_STATUS.md and VERIFICATION_LOG.md.
- [ ] Record methods, assumptions, comparisons, S01–S12/G1–G5 and real-world NOT_VERIFIED limitations.
- [ ] Run both JVM flavors, both debug builds, web tests and diff checks. Review complete branch with one fresh reviewer; address findings.
- [ ] Fetch latest dev, incorporate conflicts if any and reverify affected tests. Commit/push/PR base dev, check CI, self-merge and check dev CI. Preserve main.
