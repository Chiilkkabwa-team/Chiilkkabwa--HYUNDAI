# A02 offline sample quality evidence

2026-11-14, base dev `2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8`.
This is **offline replay + explicit synthetic input**, not a new emulator session or real driving validation.
[Method, assumptions, grades and limitations](../../A02_SAMPLE_QUALITY.md).

- `increase.csv`, `decrease.csv`: one row per original observation; signed raw Float text and Float-to-Double expansion, original elapsed ns/receipt/status/source/session, adjacent exact-ns calculation, legacy ms calculation/event/suppression/rejection, both quality modes/reasons, and original log line.
- `increase.md`, `decrease.md`: generated cold replay count/time/reason summary.
- `*-consumed.log`: original recorded consumed lines, kept separate from recalculation. Never seeded into replay state.
- `synthetic.md`: generated 11-scenario comparison including regular 200ms coverage and conservative false-negative cost. Input generation and assertions live in `SpeedQualityShadowTest`.

No unknown year/timezone/processing time or historical engine state is inferred. Replay explicitly uses receipt ns as analysis processing time; each source fixture is a fresh cold engine. Exact-ns quality times and original legacy floored-ms times are labeled. No denominator/truth labels for a real false-positive rate exists.

Reproduction: from repository root, set `A02_REPLAY_REPORT_DIR` to this directory and run:

```powershell
.\gradlew.bat --offline :app:testMockDebugUnitTest --tests '*SpeedLogReplayTest' --tests '*SpeedQualityShadowTest' --rerun-tasks
```

Without the variable, tests do not write reports. No AAOS subscription/emulator/dependency download is needed with existing SDK/Gradle cache. Fixtures are under `../a02-2026-11-14/`; the original source logs remain unchanged:

| File | SHA256 |
|---|---|
| increase.log | 61EC7D6C52299319A28ED29693B1F567BC6FB388CD1A46EE19AF3D4D61827B74 |
| decrease.log | 203B82E6772F5DA34CB19E49B301E340C3FDD46519815C9715A49DE654AFE0B4 |

These contain test session/process identifiers and synthetic emulator speed, not a real VIN/GPS/user account. Source code/test/docs are tracked; APK/SDK/AVD/local caches are not included.
