# Synthetic comparison

Assumed truth is specified per row; all input is synthetic EMULATOR-tagged data, not new emulator execution.
Each ramp spans 2s at 100ms unless stated. Times below are elapsed property milliseconds.

| Assumption | Legacy count@ms | Window count@ms | Sustained quantum count@ms | Quality reasons |
|---|---|---|---|---|
| constant truth 10mps; alternating 0 or +1kmh observation error | 1@100 | 0@none | 0@none | {WARMING_UP=6, BELOW_THRESHOLD=15} |
| gentle +1mps2 rounded to nearest 1kmh | 1@200 | 0@none | 0@none | {WARMING_UP=6, BELOW_THRESHOLD=15} |
| gentle -1mps2 rounded to nearest 1kmh | 0@none | 0@none | 0@none | {WARMING_UP=6, BELOW_THRESHOLD=15} |
| sustained +5mps2 | 1@100 | 1@600 | 1@600 | {WARMING_UP=6, CANDIDATE=1, ONGOING_EPISODE=14} |
| sustained -5mps2 | 1@100 | 1@600 | 1@600 | {WARMING_UP=6, CANDIDATE=1, ONGOING_EPISODE=14} |
| 100-200ms irregular +5mps2 | 1@300 | 1@700 | 1@700 | {WARMING_UP=4, CANDIDATE=1, ONGOING_EPISODE=14} |
| regular 200ms +5mps2 | 1@200 | 1@800 | 1@800 | {WARMING_UP=4, CANDIDATE=1, ONGOING_EPISODE=8} |
| single 100ms +1mps spike | 2@800,900 | 0@none | 0@none | {WARMING_UP=6, BELOW_THRESHOLD=15} |
| constant 20mps | 0@none | 0@none | 0@none | {WARMING_UP=6, BELOW_THRESHOLD=15} |
| one missing callback at 300ms in +5mps2 | 1@100 | 1@600 | 1@600 | {WARMING_UP=5, CANDIDATE=1, ONGOING_EPISODE=14} |
| near threshold +3mps2 limitation | 1@100 | 1@600 | 0@none | {WARMING_UP=6, NOT_SUSTAINED=15} |
