# A01 선택 센서 공개 SDK 연동

코드 커밋: 19e4ec3e8e4cad17012984a10b7c2124dc6efb3a

기준 dev01eff12a2ecdbd4ae3e0fdb78f6031504aa13540 / feature/a01-optional-vehicle-signals / 전용 Chiilkkabwa-Codex3-A01-OptionalSignals. 2026-11-15 KST.

## 구현 계획

Goal: 공개 Vehicle2.3.2에서 실제 확인한 읽기 신호만 기존 Snapshot/미션 계약에 연결한다.
Architecture: 기존 PleosReadClient/PleosSignalSession의 capability→subscribe/read·세대·timeout·draining을 재사용한다. raw enum/단위 검증은 순수 매핑에 둔다. 제어 API/새권한/공통UI/안전게이트/A02/A03/웹 변경 없음.
Tech: Kotlin/JUnit4/Gradle8.10.2/Vehicle2.3.2.
Spec: 사용자 A01 선택 센서 지시 및 AGENTS/SAFETY_RULES/COMPLIANCE_REGISTER.

- [x] S1 Wiper: FRONT_WINDSHIELD의 capability READ+ON/OFF 범위를 확인. getCurrentWiperState/register/unregisterWiperState, CAR_WINDOWS(이미 선언). ON=true/OFF=false/OTHER·SERVICE·null·타입오류 UNKNOWN. RED→GREEN 및 필수3종 회귀.
- [x] S2 Low beam: 실제 low-beam state 전용 capability/get/listener, CAR_LIGHTS(이미 선언). ON/OFF만 bool,DAYTIME_RUNNING은UNKNOWN. 동일생명주기/실패회귀.
- [x] S3 Steering: SDK의 좌음수 전륜각(degrees)과 기존 핸들회전각(가상주차에서/15) 불일치 확인. 임의 조향비/단위변환 없이 미연결/폴백 유지. 기존 CenterJudge의 입력중단/이전/미래/nonfinite 회귀는 보완.
- [x] S4 Odometer/CarInfo: TripCurrent m→km,Int분→Long초의 명시변환/음수·비유한·잘못된타입 차단. CarInfo public에는 VIN/CRN뿐이라 치수미연결,기존기본치수/isDefault유지. Passenger ROW_1_RIGHT Boolean 공개읽기도 기존필드에 연결(최소CAR_SEATS).
- [x] 선택 Toggle/Center 미션 중단시 부분진행초기화·복귀baseline·소스/신선관측·strict게이트 기존오류회귀. 실제SDK 세션대역→Snapshot→선택Judge 계약검증,Mock 전체워밍업 회귀.
- [x] 전체 양JVM/양Debug/웹·diff·안전등급을 기록하고 최신dev/CI 후 base=dev PR 자체통합. main/기존Worktree보존. AVD는 인계 전이므로 어떤 접근/설치/재시작/종료도 하지 않음. 실환경 실수신/미션완료 NOT_VERIFIED.

## 초기 SDK 조사 결론

Wiper/Light/Seat/Odometer 메서드·실제 enum/listener/unit을 로컬 sources와 javap로 확인. steering는 public CAR_DRIVE의 전륜각이며 핸들회전각 아님. 실제 public CarInfo에는 치수 메서드가 없고 PRIVATE_CAR_INFO의 내부capability조회는 사용금지. 정확한근거·최종실행결과는 구현후추가.

## 최종 지원·검증 등급

이 표의 코드/JVM PASS는 공개 API 호출 경로의 컴파일 및 Fake IPC 회귀다. 실제 차량/Emulator 수신을 뜻하지 않는다. 사용자 지시에 따라 AVD 인계 전에는 조회·접근·설치·재시작·종료를 모두 하지 않았다.

| 신호 | SDK API 확인 | 코드 연결 | JVM | Emulator 실수신 |
|---|---|---|---|---|
| Wiper | PASS | PASS | PASS | NOT_VERIFIED |
| Low beam | PASS | PASS | PASS | NOT_VERIFIED |
| Steering angle | PASS(전륜각) | BLOCKED(핸들각과 계약 다름) | PASS(미연결 유지/합성 Center 회귀) | NOT_VERIFIED |
| Trip distance/time | PASS | PASS | PASS | NOT_VERIFIED |
| Car dimensions | BLOCKED(해당 public API 없음) | BLOCKED | PASS(미연결 폴백 유지) | NOT_VERIFIED |
| Front passenger occupancy | PASS | PASS | PASS | NOT_VERIFIED |

벨트 true·실제 주차 브레이크 ON 입력과 전체 Emulator 워밍업의 기존 제약은 [A01_INPUT_BLOCKERS](A01_INPUT_BLOCKERS.md) 그대로다. 이번 작업으로 해소했다고 보고하지 않는다. 기존 Mock 전체 워밍업 6종 회귀와 실환경 완료를 구별한다.

## 실제 SDK 선언·권한·단위

설치된 Gradle 캐시 ai.pleos.playground:Vehicle:2.3.2 AAR의 classes.jar를 javap로 확인하고, 동일 버전 sources.jar의 api/listener/model 선언·KDoc을 대조했다. [선택한 public 시그니처 원본](evidence/a01-optional-2026-11-15/sdk-public-signatures.txt), [검증 요약·아티팩트](evidence/a01-optional-2026-11-15/verification.json). SDK 바이너리/전체 소스/APK/AVD는 커밋하지 않는다.

공식 원문(플랫폼 공개 API 근거): [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [Wiper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/entry), [Light](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/entry), [Steering](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/get-current-steering-wheel-angle), [Odometer](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry), [CarInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/entry). 웹 Reference 추출은 메서드 목록 위주여서 세부 시그니처·단위는 실물 선언과 실제 컴파일로 확인했다.

| 필드 | getter / capability / read / 구독·해제 | 선언된 타입·권한 |
|---|---|---|
| wiperOn | getWiper / checkWiperStateCapabilities / getCurrentWiperState / registerWiperState·unregisterWiperState | WindowArea(FRONT_WINDSHIELD), WiperStateListener.onWiperStateUpdated(WiperState?), CAR_WINDOWS |
| lowBeamOn | getLight / checkLowBeamLightStateCapabilities / getCurrentLowBeamLightState / registerLowBeamLightState·unregisterLowBeamLightState | LightStateListener.onLightStateUpdated(LightState?), CAR_LIGHTS |
| passengerSeated | getSeat / checkSeatOccupancyCapabilities / getCurrentSeatOccupancy / registerSeatOccupancy·unregisterSeatOccupancy | SeatArea(ROW_1_RIGHT), SeatOccupancyListener.onSeatOccupancyUpdated(Boolean?), CAR_SEATS |
| tripDistanceKm | getOdometer / checkTripCurrentDrivingDistanceCapabilities / getTripCurrentDrivingDistance / registerTripCurrentDrivingDistance·unregisterTripCurrentDrivingDistance | DistanceDrivenListener.onDistanceDrivenUpdated(Float?), **m → km /1000**, CAR_DRIVE |
| tripTimeSec | getOdometer / checkTripCurrentDrivingTimeCapabilities / getTripCurrentDrivingTime / registerTripCurrentDrivingTime·unregisterTripCurrentDrivingTime | DrivingTimeListener.onDrivingTimeUpdated(Int?), **분 → Long 초 ×60**, CAR_DRIVE |

모든 capability는 새 CapabilityConfig 형태를 사용하며 READ/READ_WRITE만 허용한다. 기존 Manifest의 CAR_WINDOWS/CAR_LIGHTS/CAR_SEATS/CAR_DRIVE를 재사용하고 새 권한은 0개다. 실제 기기 grant/영역/값 지원은 NOT_VERIFIED다.

- 와이퍼는 정확한 FRONT_WINDSHIELD 단일 영역과 ON/OFF 둘 다 지원되는 상태 목록이 필요하다. 후면/복합 영역을 전면으로 합치지 않는다. 실제 WiperState.ON/OFF만 bool, OTHER/SERVICE/null/잘못된 타입은 UNKNOWN이다. WiperSwitch.AUTO/LOW/HIGH를 실제 작동 ON으로 간주하지 않는다.
- 하향등은 **LowBeamLightState** 전용 API만 쓴다. ON/OFF만 bool이고 DAYTIME_RUNNING/null/다른 enum·타입은 UNKNOWN이다. DRL·상향등·Telltale을 대신 쓰지 않는다. ON/OFF 둘 다 없는 capability는 현재 어댑터의 안전한 bool/토글 계약을 지원하지 않는 것으로 처리한다.
- 조수석은 한국 LHD 앞 오른쪽 단일 좌석만 사용한다. 운전석/복합 좌석으로 대체하지 않는다. 착석 여부를 벨트 체결 증거로 쓰지 않는다.
- 트립 거리는 누적 odometer의 km getter가 아니라 **현재 트립 m getter/listener**를 사용한다. finite/nonnegative Float만 허용한다. 시간은 nonnegative Int만 허용하고 Long으로 넓힌 후 곱해 overflow를 방지한다. Double/문자열/음수/NaN/Infinity/null은 UNKNOWN. 실제 값 갱신 주기와 세션 리셋 시점은 NOT_VERIFIED다.

## 핸들각·치수의 미연결 이유와 UNSUPPORTED 구분

SteeringWheel.getCurrentSteeringWheelAngle, checkSteeringWheelAngleCapabilities(CapabilityConfig, AreaBounds<Float>), register/unregisterSteeringWheelAngle 및 SteeringWheelAngleListener.onSteeringWheelAngleUpdated(Float?)는 실제 public 선언에 있다. 실제 @RequiresPermission은 **CAR_DRIVE**이며 새 READ_CAR_STEERING 권한을 추측해서 추가하지 않았다. sources는 **전륜각(degrees), 좌측 음수, 핸들 회전각 아님**을 명시한다. 실제 normal bounds/갱신 주기는 아직 관측하지 않았다.

기존 VehicleSnapshot.steeringDeg와 화면은 핸들 회전각이고 ParkingSim은 그 값을 조향비15로 나눈다. 전륜각을 그대로 넣거나 임의 조향비로 곱하면 의미를 바꾸므로 **SDK steering 구독/매핑 자체를 추가하지 않았다**. 별도 신호 계약·차종 변환 근거가 생기기 전까지 안전 폴백과 기존 화면 슬라이더를 유지한다. CenterJudge의 합성 테스트는 이 public 전륜각을 기존 핸들각으로 승인한 증거가 아니다.

CarInfo의 public은 VIN/CRN 관련 메서드뿐이다. 외부 크기 getter가 없고, 내부 getCapabilityInfo는 RestrictTo+PRIVATE_CAR_INFO이므로 사용하지 않는다. VIN/CRN을 조회·로그로 남기지도 않는다. carDimensions를 임의 채우지 않고 기존 UI의 COMPACT_SEDAN_DEFAULT/isDefault=true 폴백을 보존했다.

**현재 어댑터가 만들 수 없는 steeringDeg/carDimensions의 UNSUPPORTED는 실제 차량 센서 capability=false와 다르다.** 이 둘에는 capability 요청을 보내지 않았고 하드웨어 미지원이라고 단정하지 않는다. 연결한 채널은 시작 시 UNKNOWN, capability/정확한 영역·상태 범위 불충족이면 UNSUPPORTED, 권한 실패는 PERMISSION_DENIED, 타입/null은 UNKNOWN, SDK 연결 실패/응답 timeout·stop은 DISCONNECTED로 분리한다. SDK의 실제 false capability와 어댑터 계약상 제한을 이 문서·시그니처·검증 표에서 구분하며 SignalStatus/공통 UI 계약을 임의 확장하지 않았다.

## 수명·미션과 공유 계약

- BELT→BRAKE→TURN 기존 capability 요청 순서를 보존하고 WIPER/LOW_BEAM/PASSENGER/TRIP_DISTANCE/TRIP_TIME를 뒤에 추가했다. 각 채널은 capability 통과 후 read/register한다. 선택 도메인은 lazy로 얻어 선택 getter 예외가 필수3종 생성을 막지 않도록 했다.
- source/receivedAtMs, 세대 격리, 초기 read보다 새 callback 우선, channel failure 차단, 동일 listener/area 해제, SDK release/draining/미응답 client 누적 방지를 기존 엔진으로 재사용한다. 미응답 선택 IPC도 release 안전 규칙상 draining을 끝내기 전 새 client를 생성하지 않으므로 resume가 DISCONNECTED로 차단될 수 있다. 이를 성공으로 숨기지 않는다.
- 등록 채널이 늘어 기존 1초 heartbeat당 read 요청도 증가한다. 기본OFF/debug EMULATOR opt-in/실차·release 차단을 유지하며 실제 서비스 부하·CPU/RAM·장시간 성능은 NOT_VERIFIED다.
- ToggleJudge는 알 수 없는 시작/중단/출처 변경 후 첫 유효 관측을 baseline으로만 사용하고 다음 새 변경을 성공으로 인정한다. 이전/미래 시각 값은 baseline/성공에 사용하지 않는다. 이미 유효한 시작 baseline에서의 시작 후 변화는 기존대로 인정한다.
- SteeringCenterJudge는 불명/중단/범위 이탈·과거/미래·NaN/Infinity에서 유지 시간을 초기화하고 최소 관측 시각을 갱신한다. 서로 다른 MOCK/EMULATOR 출처의 유지 시간을 합치지 않는다. 기존 ±5°/2초 연습 설정을 바꾸지 않았다.
- 기존 필수3종, MissionRunner/VehicleSnapshot 필드·단위·SafetyGate 및 B/C UI/저장·음성·A02/A03/웹/Manifest/Gradle은 변경하지 않았다. 기존 세션 테스트의 3개 고정 해제 개수만 전체 채널 수로 확장하고 필수3종 순서 검사를 유지했다.

## 오프라인 검증

Windows / Temurin17.0.20 / Gradle8.10.2 / Node24.22.1 / compileSdk35. 실제 AVD는 사용자 미인계 지시로 **미사용**.

- 기준 양JVM206/206. 새 선택 센서14 + 선택 미션6 = 신규20, 최종 양JVM226/226, 실패/오류0.
- TDD: 미구현 enum/helper 참조 compile RED → SDK 실제 연결 후 선택 센서11개 GREEN → capability/권한·등록예외·옛read/좌석영역까지14개. 선택 미션은 기존 코드에서6개 모두 assertion RED→보완 후6개 GREEN.
- .\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug — 양테스트/양빌드 PASS. 86 tasks 모두 실제 실행(최초전체62초); 최종 default 복원 결과는 검증 JSON/대장에 기록.
- .\gradlew.bat --offline -PenablePleosSignals=true :app:assembleVehicleDebug :app:generateVehicleReleaseBuildConfig — PASS(6초), debug flag=true/release flag=false 확인. **APK를 기기에 설치하거나 실행한 것은 아님**. 이후 기본false 빌드를 복원했다.
- node --test web/test/*.test.mjs —126/126, 실패/건너뜀0. git diff --check 및 보호 범위 diff0. 기존 A02 Shadow/A03 Probe JVM도 전체 회귀에 포함되지만 해당 장비 시험을 수행한 것으로 표시하지 않는다.
- 기존 MockSource/Repository/Runner의 필수3+선택3 워밍업, gate pause/resume, SDK 미지원/권한/연결·타입/null·stop/late/restart/timeout/draining 회귀를 포함했다. IPC 대역 값의 출처 EMULATOR는 배포 코드의 라벨 전파 시험일 뿐 **실제 Emulator 수신 증거가 아니다**.

## 안전·통합 게이트

공식/팀 근거는 SAFETY_RULES·COMPLIANCE_REGISTER의 O03(공모전 공개 SDK), P02/P03(플랫폼 UX), P04/P06(공개 API·지원), P07/P08(최소정보), P10(자원) 및 위 공식 SDK 문서다. O03 재열람은 도구 접근 오류로 실패해 기존 대장의 허용 범위만 참고했으며 새 승인/배포권리를 취득했다고 주장하지 않는다.

| 항목 | 변경 범위 판정 |
|---|---|
| S01 | PASS 정적/컴파일: 읽기·capability·구독/해제만, set/CONTROL/직접VHAL/비공개 호출0 |
| S02 | PASS 공개SDK/source/javap/컴파일·기존권한 재사용. O03 재조회/실기권한·새 플랫폼 승인 NOT_VERIFIED |
| S03 | PASS JVM: AAOS gate 필드 쓰기0·strict 게이트/잠금 회귀. 실제 정차 게이트/전체워밍업 NOT_VERIFIED/BLOCKED |
| S04 | PASS 변경범위: UI/DO/터치 변경0. 실제 주행 정책 검증 NOT_VERIFIED |
| S05/S06 | NOT_APPLICABLE: 주행 서비스/음성 변경0. 기존 미검증 유지 |
| S07 | PASS: typed enum/영역/단위·출처·시각 유지, 전륜각/핸들각·DRL 구별. 센서 정확도/실수신 NOT_VERIFIED |
| S08 | PASS JVM: 시작 후 새 조작·중단 폐기·필수 미션/게이트 회귀. 실차 조작 요구0 |
| S09 | PASS 변경범위: 새 저장/서버/위치/프로필/원본 음성·영상/VIN 수집0. 기존 debug 콜백의 선택 값만 진단 가능 |
| S10 | PASS 보고: 문서/실컴파일/합성 JVM/Emulator/실차 구분 |
| S11 | 새 자산/라이브러리0. 실제 장비 CPU/RAM·갱신 주기/부하 NOT_VERIFIED |
| S12 | NOT_APPLICABLE: 공식 제출/release APK 업로드·공개 전환0, main 보존 |

G1 PASS(배정/전용 브랜치/범위),G2 PASS(실물/공식 근거·단위·보류 사유),G3 PASS(최소읽기·strict게이트·출처/수명/기본차단),G4 JVM/빌드/웹만 PASS·실환경 NOT_VERIFIED,G5 최신dev/충돌/보호diff/CI 후 개발 통합만 허용. 실제 차량/주행 경로는 활성화하지 않는다. 최신 PR HEAD/병합 SHA·사후 CI는 PR/Actions로 추적한다.

## 인계 후 남은 시험

Codex1→Codex2→Codex3 순서의 명시 인계 후 전용 환경에서만 수행한다. public 콘솔 입력→SDK callback(원본)→VehicleSnapshot(변환값/status/source/receivedAtMs)→gate→선택 미션을 각각 기록하고 종료 시 초기 상태 복원·구독 정리를 확인한다. UI 조작만으로 앱 수신/완료 PASS를 선언하지 않는다. 버클/실제 PBON 입력이 없으면 전체 워밍업은 계속 BLOCKED다. 핸들 회전각 계약/치수 public API는 별도 공식 근거 없이는 추가하지 않는다.

최종 default 복원: 86/86 tasks 실제 실행, BUILD SUCCESSFUL42초, 양JVM226/226·실패오류0, 양Debug PASS. default debug/release 모두 PLEOS_SIGNALS_ENABLED=false, A02/A03 flag도false. 최종 문서 커밋은 위 코드SHA를 변경하지 않으며 PR/CI가 통합 결과의 정본이다.
