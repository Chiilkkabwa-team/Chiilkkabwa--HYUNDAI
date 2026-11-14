# decrease offline replay

EXPERIMENTAL. Cold excerpt; no truth labels. Receipt ns used as analysis processing clock.
Log timestamp year/timezone and earlier cooldown/episode state are unknown.
Observations: 15; recorded consumed lines: 15 (separate file).

| Path | Candidate times |
|---|---|
| Cold legacy | RAPID_SPEED_INCREASE@798204ms, RAPID_SPEED_DECREASE@799168ms |
| Window only | RAPID_SPEED_DECREASE@799404455800ns |
| Sustained + assumed 1km/h quantum | RAPID_SPEED_DECREASE@799779340800ns |

Legacy reasons: {FIRST_SAMPLE=1, EVENT=2, BELOW_THRESHOLD=5, COOLDOWN=1, ONGOING_EPISODE=6}
Quality reasons: {WARMING_UP=5, BELOW_THRESHOLD=5, NOT_SUSTAINED=3, CANDIDATE=1, ONGOING_EPISODE=1}
