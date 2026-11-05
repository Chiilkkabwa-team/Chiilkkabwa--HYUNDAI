# 03. Pleos SDK · AAOS API · 에뮬레이터 노트

> TL;DR — 표준 데이터(기어·주행 제한·페달)는 AAOS Car API, 차량 특화(벨트·브레이크 상태·방향지시등·핸들·트립·차체)는 Pleos Vehicle SDK. 모두 **읽기**만. 아래 '확인 상태'는 2026-11-05 기준 **문서 확인만** 됨 → Day1에 갱신.

## 1. 확인 상태 표 (Day1에 채울 것)
상태 단계: 문서 → 계정(My Project에서 사용 가능) → 에뮬레이터 수신 → 서비스 검증
| 신호 | API | 권한 | 문서 | 계정 | 에뮬레이터 수신 | 메모(값 타입·부호·갱신 주기) |
|---|---|---|---|---|---|---|
| 주행 제한 | CarUxRestrictionsManager | — | ✅ | — | ☐ | Virtual Console Motion 속도 변경으로 확인 |
| 기어 | AAOS GEAR_SELECTION | android.car.permission.CAR_POWERTRAIN | ✅ | — | ☐ | 숫자 대신 VehicleGear.GEAR_PARK 등 상수로 비교 |
| 주차 브레이크 | AAOS PARKING_BRAKE_ON / Pleos Brake | CAR_POWERTRAIN / pleos CAR_DRIVE | ✅ | ☐ | ☐ | Virtual Console Safety |
| 안전벨트 | Pleos Seat seat-belt-buckled-state | pleos.car.permission.CAR_SEATS | ✅ | ☐ | ☐ | 운전석 area 값 확인 |
| 조수석 착석 | Pleos Seat seat-occupancy | CAR_SEATS | ✅ | ☐ | ☐ | |
| 브레이크 페달 상태 | Pleos Brake current-brake-pedal-status(get/register/capability) | pleos CAR_DRIVE | ✅ | ☐ | ☐ | **on/off인지 연속값인지** |
| 페달 압력 | AAOS ACCELERATOR/BRAKE_PEDAL_COMPRESSION_PERCENTAGE | android.car.permission.READ_CAR_PEDALS(위험, 런타임) | ✅ | ☐(심사 승인) | ☐ | Float 0~100, CONTINUOUS |
| 방향지시등 | Pleos TurnSignal turn-signal-state | pleos CAR_LIGHTS | ✅ | ☐ | ☐ | 값 매핑(LEFT/RIGHT/HAZARD) |
| 와이퍼 | Pleos Wiper wiper-state/switch | pleos CAR_WINDOWS | ✅ | ☐ | ☐ | |
| 전조등 | Pleos Light low beam | pleos CAR_LIGHTS | ✅ | ☐ | ☐ | |
| 핸들 각도 | Pleos SteeringWheel steering-wheel-angle | **상수 미확인** | ✅ | ☐ | ☐ | 부호·단위·정차 갱신 |
| 트립 거리·시간 | Pleos Odometer trip current distance/time | pleos CAR_DRIVE | ✅ | ☐ | ☐ | 단위(km? m?), 주행 중 리셋 여부 |
| 차체 크기 | Pleos CarInfo | pleos CAR_INFO | ✅(외부 크기) | ☐ | ☐ | 필드명 확인 |

## 2. Pleos Vehicle SDK 요약 (v2.3.2 / Vehicle Service v2.3.21, 문서 기준)
- 구조: 3rd party 앱 → Vehicle SDK → Pleos Connect Vehicle Service(AAOS 위) → 차량
- 사용 패턴: `Vehicle(context)` → `initialize()` → `getXxx()`로 도메인 객체 → `checkXxxCapabilities` → `getXxx(onComplete, onFailed)` / `registerXxx(listener)` → `release()`(콜백 끝난 뒤)
- 콜백: get = onComplete(값)/onFailed, capability = onComplete(config, areaMap)
- 권한: 매니페스트 `pleos.car.permission.{상수}`, CAR_* = 조회, CONTROL_CAR_* = 제어(**쓰지 않음**)
  - Door: CAR_DOORS / Window·Wiper: CAR_WINDOWS / HVAC: CAR_CLIMATE / DrivingMode·Brake·Odometer: CAR_DRIVE / EvBattery·Fuel: CAR_ENERGY / CarInfo·Display: CAR_INFO / Light·TurnSignal: CAR_LIGHTS / SideMirror: CAR_MIRRORS / Safety·Tire: CAR_SAFETY / Seat: CAR_SEATS / Toll: CAR_TOLL
  - 누락 시 onFailed → VehicleException ERROR_SECURITY_PERMISSION
  - **App Market 배포 시 사전 협의·승인된 권한만** 사용 가능
- 도메인 메서드(G=get, S=set): Brake G current-brake-pedal-status / CarInfo G CRN-info, VIN-info(+외부 크기) / Door G·S / DrivingMode G cruise-control-state·target-speed·driving-mode / EvBattery / Fuel / HVAC / Light G·S / Odometer G distance-driven, trip cumulative·current·instantaneous distance·time / Safety G telltales / Seat G 위치·belt-buckled·occupancy / SteeringWheel G angle / TurnSignal G state / Wiper G state·switch
- 호환성: 모델별로 다름 → 도메인별 capability 확인 필수. AAOS 속성은 getPropertyList로 실제 지원 확인, **모델명 분기 금지**
- Gradle: 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`, `ai.pleos.playground:Vehicle:2.3.2` (NaviHelper 3.0.5, SpeechToText 3.3.1, TextToSpeech 3.3.1 추정, FusedLocation 1.1.3)
- SDK 상태(create-my-project 문서): Vehicle State(조회)·NaviHelper = 신청 없이 사용 가능 / FusedLocation·TTS·STT·Vehicle Control = 준비 중 → **TTS는 Android 기본 TextToSpeech로 대체**

## 3. 공식 문서 링크
- Vehicle SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- Brake API: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry
  - capability: …/brake/check-brake-pedal-status-capabilities · get: …/brake/get-current-brake-pedal-status · register: …/brake/register-brake-pedal-status
- Vehicle SDK 튜토리얼: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk · 샘플 앱: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
- 프로젝트 생성·SDK 신청: https://document.pleos.ai/docs/pleos-only/create-my-project
- 에뮬레이터 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide
- 앱 설계 가이드: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/intro
- 주행 안전 설계: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 주행 규제 개발: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- API 호환성·가용성: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 앱 카테고리: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- 앱 심사(주의 분산): https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements
- 개발 FAQ: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq
- Gleo Interface: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
- AAOS VehiclePropertyIds: https://developer.android.com/reference/android/car/VehiclePropertyIds
- 공모전 개발 환경: https://appdevchallenge.auto-hmg.io/guide/development

## 4. 에뮬레이터 설치 (Pleos Connect Emulator v2.1.0)
1. Android Studio > Tools > SDK Manager > SDK Update Sites > + 
   - Name: Pleos Connect System Image
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms > API 34 > Pleos Connect system Image 체크(Show Package Details)
3. Device Manager > Create device > Automotive > Pleos Connect v2.1.0 > Advanced: Cold boot, RAM 4GB
4. 이전 버전이 있으면 삭제 후 설치

## 5. Virtual Console (시연 핵심 도구)
- `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`
- Motion: 기어, 브레이크, 스티어링 각도, 주행거리 / ADAS: 크루즈 / Convenience: 도어·창문·시트·조명·와이퍼 / Safety: 주차브레이크, 안전벨트, 경고등 / Energy / Climate
- **콘솔에서 값을 바꿀 수 있다는 것 ≠ 앱이 그 필드를 받을 수 있다는 것.** 반드시 앱 로그로 확인

## 5-1. AAOS 권한 등급 (Android Car 레퍼런스, 2026-11-05 확인)
| 권한 | 등급 | 의미 |
|---|---|---|
| android.car.permission.CAR_POWERTRAIN | Normal | 기어·주차 브레이크 — 설치만으로 허용 |
| android.car.permission.READ_CAR_PEDALS | Dangerous | 페달 압력 — 사용자 허용 필요 |
| android.car.permission.CAR_SPEED | Dangerous | 속도 — 사용자 허용 필요 |
| android.car.permission.READ_ADAS_STATES / READ_ADAS_SETTINGS | **Signature\|Privileged** | 차선이탈·전방충돌·후측방 경고 상태 — **서드파티 앱 불가** |
| android.car.permission.READ_DRIVER_MONITORING_STATES | **Signature\|Privileged** | 운전자 모니터링 — 서드파티 앱 불가 |

## 6. 규정 요약(코드에 영향)
- DO(distractionOptimized) 미선언 앱 = 주행 중 사용 불가 → 선언하지 않는다. 주행 중에는 **시스템이 앱 화면을 가릴 가능성이 크다**(앱 잠금 화면이 보이는지 Day1 확인, B37)
- CarUxRestrictions 기준 제한, 속도 0 ≠ 주차
- 미디어·게임은 정차 중에만, 주행 시 일시정지·숨김
- 백그라운드: 갱신·애니메이션·센서 수집 중단, 복귀 시 상태 복원
- 성능: 비내비 앱 주행 중 CPU <5%, 정차 ≤25%, RAM ≤400MB / 입력 응답 0.25초 / 1~2초 안에 파악 / 한 손 조작
- 화면: Driving view(시스템 영역)는 가릴 수 없음, 세로 1창 / 가로 2창
- 개인정보: PII 최소, 운전 중 행동 데이터 마케팅 금지, 공유 기기(프로필 전환·로그아웃)
- 실차 판별: Build.DEVICE = CONNECT_S / CONNECT_L / CONNECT_W (실차만)
- 속도는 PERF_VEHICLE_SPEED(클러스터와 동일), GPS 속도 쓰지 말 것
