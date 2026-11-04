# 02. 아키텍처 — 패키지·상태 기계·데이터 흐름·결정 사항

> TL;DR — 신호 공급자(AAOS·Pleos·Mock) → VehicleRepository(스냅샷 1개) → SafetyGate(순수) → SessionReducer(순수) → MissionRunner(순수) → ViewModel → Compose. `core/`는 안드로이드 의존이 없고 테스트로 지킨다.

## 1. 패키지
```
com.chiilkkabwa.app
├─ core/                      # 순수 Kotlin (단위 테스트 대상)
│  ├─ signal/Signals.kt       # Signal<T>(value,status,source,receivedAt), VehicleSnapshot, Gear, TurnSignalState, CarDimensions
│  ├─ safety/SafetyGate.kt    # GateState, LockReason, GatePolicy, SafetyGate.evaluate()
│  ├─ mission/Missions.kt     # MissionJudge 7종, EdgeTracker, MissionCatalog
│  ├─ mission/MissionRunner.kt# 순서 진행, pause/resume/skip, MissionResult
│  ├─ mission/ParkingSim.kt   # 가상 주차 자전거 모델, isParked
│  ├─ session/PracticeSession.kt # Phase, SessionReducer, TripMark, summarize()
│  ├─ achievement/Achievements.kt # Badge 9종, AchievementRules, level()
│  └─ review/Content.kt       # 목표·약속·복기 칩·칭찬 문구
├─ data/
│  ├─ vehicle/VehicleSignals.kt   # VehicleSource, SnapshotSink, VehicleRepository, realSourceLabel()
│  ├─ vehicle/AaosVehicleSource.kt# CarUxRestrictions, GEAR_SELECTION, PARKING_BRAKE_ON, 페달 압력 (실제 API)
│  ├─ vehicle/PleosVehicleSource.kt # Seat/Brake/TurnSignal/Wiper/Light/SteeringWheel/Odometer/CarInfo (TODO(sdk))
│  ├─ vehicle/MockVehicleSource.kt  # 디버그 패널 조작
│  └─ store/PracticeStore.kt   # 프로필·이력·배지 (SharedPreferences JSON)
└─ ui/
   ├─ ChiilViewModel.kt        # 단일 상태 출처(UiState), 200ms 틱
   ├─ screens/Screens.kt       # 화면 8개 + 배지 토스트 + 디버그 패널
   └─ theme/Theme.kt           # 디자인 토큰
```
플레이버: `mock`(MockVehicleSource만, 디버그 패널) / `vehicle`(Aaos + Pleos).

## 2. 데이터 흐름
```
[Car / Emulator] ─┬─ AaosVehicleSource ──┐
                  └─ PleosVehicleSource ─┼─▶ VehicleRepository.snapshot (StateFlow<VehicleSnapshot>)
[Debug panel] ──── MockVehicleSource ────┘            │ (변경 시 + 200ms 틱)
                                                      ▼
                         SafetyGate.evaluate(snapshot, policy) → GateState
                                                      ▼
                         SessionReducer.onGate(state, gate, snapshot, now) → SessionState(Phase)
                                                      ▼
                         PREPARING & Ready & WARMUP → MissionRunner.update(snapshot, now)
                                                      ▼
                         UiState → Compose (출처 라벨 = Signal.source)
```

## 3. 신호 필드 → 공급자 → 용도
| VehicleSnapshot 필드 | 공급자(우선) | 대체 | 쓰는 곳 |
|---|---|---|---|
| uxRestricted | AAOS CarUxRestrictionsManager | — | 게이트(최우선) |
| gear | AAOS GEAR_SELECTION | — | 게이트 |
| parkingBrakeOn | AAOS PARKING_BRAKE_ON | Pleos Brake 주차 브레이크 | 게이트 |
| driverBeltBuckled | Pleos Seat seat-belt-buckled-state | (AAOS SEAT_BELT_BUCKLED는 시스템 권한) | 미션 |
| brakePressed | Pleos Brake current-brake-pedal-status | brakePedalPct>5% | 미션 |
| brakePedalPct / accelPedalPct | AAOS *_PEDAL_COMPRESSION_PERCENTAGE | — | 페달 미션(조건부) |
| turnSignal | Pleos TurnSignal | (AAOS TURN_SIGNAL_STATE는 시스템 권한) | 미션 |
| wiperOn / lowBeamOn | Pleos Wiper / Light | — | 조건부 미션 |
| steeringDeg | Pleos SteeringWheel steering-wheel-angle | AAOS PERF_STEERING_ANGLE(권한 확인) | 핸들 미션, 가상 주차 |
| passengerSeated | Pleos Seat seat-occupancy | — | 멘토 모드 제안 |
| tripDistanceKm / tripTimeSec | Pleos Odometer trip | 앱 시계 | 복기 |
| carDimensions | Pleos CarInfo | 기본값 4700×1830 | 가상 주차 |

## 4. 세션 상태 기계 (core/session)
```
CHECKING ─(Ready)─▶ READY ─(연습 시작)─▶ PREPARING ─(DRIVING_RESTRICTED | NOT_IN_PARK)─▶ DRIVING_LOCK
   ▲  └─(Locked)─┘                         │(PARKING_BRAKE_OFF/UNKNOWN: 같은 Phase, 잠금 화면)        │(Ready)
   │                                                                                                ▼
   └──────────────────────────────── (onReviewDone → PREPARING) ◀──────────────────────────────── REVIEW
```
- PREPARING 내부 단계(UI 소유): GOAL → WARMUP → PARKING_SIM → (PEDAL) → (PACT) → READY_TO_GO
- 잠금 진입 시 MissionRunner.pause(), 해제 시 resume()(현재 미션을 새로 시작)

## 5. 핵심 설계 결정 (바꾸려면 팀 합의 + 이 표 갱신)
| 결정 | 이유 |
|---|---|
| 모르면 잠금(UNKNOWN → Locked) | 안전, 규정(속도 0 ≠ 주차) |
| 기어가 P가 아니면 준비 이후 DRIVING_LOCK | CarUxRestrictions가 늦게 바뀌는 환경 대비 |
| 미션 판정은 시작 이후 '변화'만 | 이미 밟힌 브레이크·이전 값 완료 방지 |
| 출처 라벨을 Signal에 내장 | 모의/에뮬레이터/실차 혼동 방지(공모전 허위 기재 리스크) |
| core를 순수 Kotlin으로 | 에뮬레이터 없이 테스트, 심사에서 기술 설명 쉬움 |
| SharedPreferences JSON | 의존성 최소, 데이터 작음. 커지면 DataStore로 |
| LLM·서버 없음 | 설명 가능성, 개인정보, 오프라인 |
| 주행 중에도 게이트 신호는 구독 유지 | 재주차 감지 필요. 미션 신호는 후속 최적화로 분리 가능(성능 측정 후) |

## 6. 알려진 한계·TODO
- PleosVehicleSource 전부 TODO(sdk) → Day1
- 핸들 각도 부호·단위 미확인(왼쪽 음수 가정)
- 가상 주차 조향비 15:1·최대 35° 근사 → 차종값 있으면 교체
- 백그라운드 진입 시 repo.stop()/start() 미구현(T12) → Lifecycle 연결 필요
- 가로 2창(landscape 2-window) 레이아웃 미구현
- 접근성(큰 글씨 설정·색 외 표시) 점검 필요(T11)
