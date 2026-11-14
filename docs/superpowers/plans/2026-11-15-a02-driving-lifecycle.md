# A02 Driving Lifecycle Verification Plan

> Execute inline using executing-plans; final read-only review before integration.

Goal: establish observed foreground speed subscription behavior under real Pleos UX restrictions, without enabling a prohibited background route.
Architecture: existing MainActivity/repository/source/session chain remains authoritative. Add opt-in A02 diagnostic markers only; combine OS lifecycle events, read-only service/process snapshots and official Virtual Console input. Contract regressions use fake clients explicitly labeled synthetic.
Spec: user attachment f18131f9-632b-47c8-9a52-763b8c0721f3 (L1–L4).

Constraints: dedicated test/a02-driving-lifecycle from dev01eff12, preserve all prior worktrees/AVDs; no DO/service/control/VHAL/B/C/Shadow promotion/main changes. Never infer policy permission from callback success.

Review focus: OS restriction transitions versus merely D gear; heartbeat versus unrelated VM wakeups; lost callbacks and listener resource ACK distinction; new session baseline before events; granted permission versus valid observed data.

- [x] Audit required docs/code and current official P02/P03. Distinguish document permission, expected lifecycle, observed lifecycle.
- [x] Baseline Android206 each/both debug and web126. Add bounded A02 opt-in trace only if needed; add composite repository/session stop/re-entry/late callback tests, no public model change.
- [x] Create fresh dedicated Pleos AVD from installed official image, port5558, no other AVD mutation. P/D/actual-motion/UX/pause-stop/P/re-entry/restart/permission evidence using supported console only. If no actual transition occurs record NOT_VERIFIED rather than force UX/VHAL.
- [x] Record CPU/callback cessation with window and scope; never assert whole-process zero CPU because the existing VM has a200ms coroutine delay.
- [x] Document A→B/C contract, product constraints, S01–S12/G1–G5, commands/environment/SHA/evidence. Restore defaultOFF and our own test resources.
- [ ] Full regression, fresh final review, latestdev integration, PR dev/selfmerge and postmergeCI. Keep main, branches and worktrees.
