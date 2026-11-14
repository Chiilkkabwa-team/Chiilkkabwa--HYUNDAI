# A02 speed bridge implementation plan

> Execute inline with superpowers:executing-plans and TDD. User explicitly authorized design, implementation and dev merge without additional confirmation. Preserve branches/worktrees.

Goal: Public read-only PERF_VEHICLE_SPEED to existing SpeedEventEngine; default OFF, debug positively identified EMULATOR opt-in only.
Architecture: Dedicated VehicleSpeedObservation preserves signed Float m/s, original elapsed ns, receipt elapsed ns and unique subscription session. Existing wall-clock Signal is unchanged. Pure SpeedEventBridge and SpeedSignalSession validate lifecycle/time/status and produce bounded callbacks. Android adapter uses public async Car lifecycle and property callback only; AaosVehicleSource delegates it. Mock has explicit injected speed; no UI/voice/store consumers.
Spec: User A02 instruction; resulting contract docs/A02_SPEED_BRIDGE.md.
Base: 1acc54036c5660ff05d572dd904d250762f0dad5. Branch feature/a02-aaos-speed-bridge. Dedicated managed worktree only.

## Constraints / review focus
- No engine thresholds/cooldown/hysteresis changes. No web, UI layout, B02/C01/C02, safety gate, GPS, VHAL/control API or persistent event storage.
- Engine timestamp and now are elapsed milliseconds only; validate exact ns before rounding. Future/stale/reversed/replayed/null/nonfinite/wrong type fail closed. Reverse is preserved but excluded and breaks continuity.
- Permission missing versus denied, config absent, service failure and registration(false) stay explicit. Synchronous register callback must not publish success if registration fails.
- Stop/disconnect revokes generation before closing; restart gets unique ID; old callback cannot touch new sink. Timeout expires missing continuous samples without manufacturing observations.

## Task 1: pure contract
- [x] Write SpeedSignalSessionTest and SpeedEventBridgeTest: baseline/events/units/reverse/status/type/time/timeout/source/session/permissions/failures/late callbacks/registration failure; run RED.
- [x] Implement observation, decoder, bridge/session; run GREEN; commit.

## Task 2: integration
- [x] Add tests for strict activation policy, explicit Mock path and repository stop invalidation; run RED.
- [x] Add public Android read client/source and delegate from AAOS, snapshot speed/invalidation, Mock injection, CAR_SPEED manifest and gated runtime request/default-off flag. Existing UI/ViewModel unchanged.
- [x] Both flavor JVM tests and debug builds + web regression; inspect manifest/flags; commit.

## Task 3: runtime and integration
- [ ] Pleos AVD permission/config/callback/time/consumption evidence, or explicit BLOCKED. Restore console and end our processes.
- [ ] A02_SPEED_BRIDGE.md, status/verification logs with S01/S02/S03/S05/S07/S09/S10 and G1-G5; fresh whole-branch review and regression fixes.
- [ ] Latest dev conflict check, CI, PR base dev merge and post-merge CI; main unchanged; no worktree/branch deletion.

