# A02 driving lifecycle evidence — 2026-11-15 KST

This is an actual **Pleos 2.1.0/API34 emulator** experiment, not real-car execution or policy approval.
[Method, exact outcomes, contracts and grades](../../A02_DRIVING_LIFECYCLE.md).

Dedicated AVD `Chiilkkabwa_Pleos_2_1_0_A02_Lifecycle`, serial emulator-5558, user10, guest hardware ranchu.
Base dev01eff12 + opt-in diagnostic-only source diff; final source/HEAD and integration are identified in the linked PR/verification log.
Test APK SHA256: `6FD8228174FC8583B8353CD5B475EBC7B6B3E0E375D7ECCE33A40B36A6830FE6`.
Only A02 enabled; A01/A03 disabled. Default-off restoration has a different APK, not this test hash.

- `app-lifecycle-speed.log`: chronological selected tags from our continuous local logcat capture. Includes every A02SpeedTrace line and MainActivity OS lifecycle event in the capture. Other packages/OS chatter removed. The full original tag stream is preserved locally at `.gradle/a02-lifecycle/lifecycle-runtime.log`. Original values/time/source/session/status are unchanged. Natural gaps are not filled.
- `01-park-state.txt`: first P/resumed state; app PID12030.
- `02-drive-zero-state.txt`: D at0; actual UX=true/80 and driving state1. No pause follows.
- `03-motion-state.txt`, `03-motion-app.png`: actual moving state2, nonzero callbacks and platform driving-block screen. The image's1km/h is a capture instant; the whole run maximum is3km/h. A blocked screen does not prove onPause.
- `04-park-return-state.txt`: actual UX=false/0 and state0 after P; same session because no lifecycle pause occurred.
- `05-home-before-state.txt`, `06-home-after-state.txt`, `07-reentry-state.txt`: HOME did not stop this Activity. Not counted as a successful pause/re-entry.
- `08-settings-before-state.txt`, `09-settings-after-state.txt`: opening standard system Settings in P actually pauses/stops MainActivity. Process CPU counters are preserved; no setting changed.
- `10-settings-reentry-state.txt`: actual hot re-entry; new speed session and Baseline.
- `11-*`: standard debug CAR_SPEED revoke, PID17603 denied/null/rejected and permission activity. No user Allow/Deny interaction is claimed.
- `12-*`: test permission regrant + explicit force-stop/cold restart; PID18172 and new session/Baseline. Process force-stop is not a graceful onPause test.
- `13-*`: defaultOFF reinstalled, CAR_SPEED revoked; new PID15536's A02-only capture has **0 lines** over5s. The empty `.log` is intentional. All three opt-ins false; `13-default-flags.txt` is generated BuildConfig field excerpt.
- `environment-fingerprint.txt`: guest image and public Car service version output.
- `verification-summary.json`: local regression counts, experimental assumptions, exact pause window/CPU sample and blocking product limitations. No APK/SDK/AVD/caches included.

Time domains: logcat prefix is guest epoch seconds, lifecycle marker elapsedNs/speed propertyNs/receiptNs are boot monotonic ns, phase headers are hostUTC, service change history uses guest local time. The guest's initial boot-date history precedes clock synchronization. Do not put logcat epoch in SpeedEventBridge or treat resumed subscription sessionId as the product trip session.

Observed pause interval `(1794671236.158,1794671258.151)` has0 A02 observation/consumed lines. Re-entry session is `8a4d2d92-445a-4da3-9b88-8fa2003b2194`, distinct from pre-pause `01312755-c46c-4970-aa90-2815439337a7`. Deliberate late Binder callback injection was not performed; that race is tested with synthetic fake clients.

The entire test contains3416 OK speed observations (maximum0.8333334m/s) and0 RAPID_SPEED candidates. Thus this run proves callback/evaluation while restricted, not new sharp-change event emission or accuracy. Prior A02 event evidence remains separate.

Reproduction in an exclusively assigned fresh Pleos device: build `-PenableAaosSpeed=true :app:assembleVehicleDebug`, verify user/serial and declared CAR_SPEED dangerous permission; use normal debug grant for the test user; forward own host28088 to guest8088. Use official console P→D→short ACCEL→BRAKE→P. Read `dumpsys car_service --services CarUxRestrictionsManagerService CarDrivingStateService`, OS `wm_on_*` events, A02 logs and `dumpsys activity activities` at each phase. Then in P open `android.settings.SETTINGS`, wait, bring MainActivity back; separately test debug revoke/regrant+cold restart. Restore default APK, revoke test permission and remove only own forward/AVD. Never use VHAL, DO/FGS or touch blocked driving UI.

The AVD and raw local captures are preserved. Only our emulator-5558/logcatPID14092/forward28088 were stopped. Another emulator-5562 was never installed into, granted/revoked, stopped or reset. Runtime callbacks do not authorize collecting/storing/coaching while driving; policy and product enablement remain blocked/unverified as detailed in the report.
