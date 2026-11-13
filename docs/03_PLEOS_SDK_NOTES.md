# 03. Pleos SDK · AAOS API · 에뮬레이터 노트

**중요한 최신 정정(2026-11-13):** 이 노트는 11월 5~8일의 SDK 조사 기록이며, **주행 중 오디오 허용·SDK 제공 상태를 확정하는 최신 규정이 아니다**. 공식 공모전 범위는 [COMPLIANCE_REGISTER.md](COMPLIANCE_REGISTER.md) §4·§5·§10과 [SAFETY_RULES.md](SAFETY_RULES.md) S02·S05·S06을 우선한다. 아래 §5-0과 §6의 과거 허용 단정은 에뮬레이터/플랫폼 검증 전까지 `NOT_VERIFIED`다.

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

### A01 AAOS 신호 구독 내구성 작업 (2026-11-13 / 부분 구현)

- `AaosVehicleSource.kt`는 실제 표준 `GEAR_SELECTION`·`PARKING_BRAKE_ON`·`CarUxRestrictionsManager`의 **읽기 전용** 콜백 구독 경로를 사용한다. 공식 `CarPropertyManager.registerCallback` 문서에 따르면 첫 등록 시 현재 신호를 이벤트로 받는다. 상태가 `STATUS_AVAILABLE`일 때도 값 타입과 범위를 확인하고, 오류·권한 거부·등록 실패는 `UNKNOWN` / `DISCONNECTED` / `PERMISSION_DENIED`로 잠근다.
- 현재 SDK가 실제로 지원하는지, Virtual Console의 기어·주차 브레이크 변경이 앱까지 수신되는지 **에뮬레이터 증거는 아직 없음(NOT_VERIFIED)**. CI 성공은 이 항목을 PASS로 바꾸지 않는다.
- **`PleosVehicleSource.kt`의 Seat(벨트)/Brake(페달)/TurnSignal/Light/Wiper/Steering/Odometer 구현은 여전히 공식 SDK 아티팩트의 실제 클래스·리스너·area/value 타입 확인이 필요한 TODO**. 지원하지 않는 센서나 승인 불명 API를 호출하지 않는다. `PleosVehicleSource`가 발행하는 `UNSUPPORTED`는 현재 **연동 미구현 폴백** 표시이지 실차 자체가 센서를 지원하지 않는다는 실증이 아니다.
- Android JVM 회귀 테스트: `AaosSignalDecodeTest.kt`(정상 신호, 상태 오류, 타입 오류, 페달 범위). Day1에는 `adb logcat -s AaosVehicleSource PleosVehicleSource` 출력, 장비/SDK 버전, console 입력, 실제 앱 화면 게이트 상태를 [검증 기록](VERIFICATION_LOG.md)에 남길 것.
- 공식 AAOS API: [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager). 팀 기준 S01·S02·S03·S07·S10을 적용한다.

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
- SDK 상태(create-my-project 문서 당시 표기): Vehicle State(조회)·NaviHelper = 신청 없이 사용 가능 / FusedLocation·TTS·STT·Vehicle Control = 준비 중. **그러나 공모전 공식 공지 O03은 공개 문서의 Vehicle·NaviHelper·TTS·STT·FusedLocation을 에뮬레이터에서 사용 가능하다고 안내한다.** 포털 `준비 중`과 실제 SDK 가용성은 구분하고, Pleos ON_DEVICE TTS 또는 Android TTS 대체 여부는 구현·기기 검증 후 결정. Gleo App Schema 신청 불가, Pleos LLM SDK 미제공, 차량 마이크의 외부 STT 전송 불가.

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

## 4. 에뮬레이터 설치 (Pleos Connect Emulator v2.1.0) — 공식 문서 원문 기준
> 출처: document.pleos.ai › 개발 환경 구성 › *Pleos Connect Emulator 설치 및 실행* (2026-11-05 원문 재확인)

**소프트웨어 요구사항**: Android API **28 이상**(차량 사이버 보안 요건 — 사이닝 키 로테이션·리니지), Gradle **8.0 이상**, Java 또는 Kotlin, Emulator **v2.1.0**(Virtual Console 지원 최소 버전도 2.1.0).

**이전 버전이 설치돼 있으면 삭제 후 진행한다.**

1. Tools → SDK Manager → **SDK Update Sites** → `+`
   - Name: `Pleos Connect System Image`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. **SDK Platforms** 탭 → **Show Package Details** 체크 → API Level 34의 **Pleos Connect system Image** 체크 → OK → 설치 완료 후 **Android Studio 재시작**
3. Tools → **Device Manager** → Create device → Form Factor **Automotive** → **Pleos Connect v2.1.0** → Next
4. **Advanced Settings** → Startup > Default boot **Cold** / **RAM 4 GB** (구버전: Graphics Hardware + Cold boot)
5. **Device 탭으로 돌아가** Select system image > API **34** → 표의 **Pleos Connect v2.1.0 APIs** 선택 → Finish
6. Device Manager에서 생성한 AVD 실행

문제가 생기면 Android Emulator / Android Studio·AGP의 알려진 이슈 문서를 먼저 확인한다.

## 5. Virtual Console (시연 핵심 도구)
- `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/`
- Motion: 기어, 브레이크, 스티어링 각도, 주행거리 / ADAS: 크루즈 / Convenience: 도어·창문·시트·조명·와이퍼 / Safety: 주차브레이크, 안전벨트, 경고등 / Energy / Climate
- **콘솔에서 값을 바꿀 수 있다는 것 ≠ 앱이 그 필드를 받을 수 있다는 것.** 반드시 앱 로그로 확인

## 5-0. TTS·오디오 포커스 (F12 음성 길잡이 근거, 2026-11-08 확인)
> 출처: document.pleos.ai › 앱 기획 가이드 › *주행 안전을 고려한 설계* / 개발 가이드 › *오디오 포커스* / Connect SDK › TextToSpeech

**2026-11-13 주의:** 아래는 **과거에 수집한 원문 3개**로, 실제로 *DO 미선언 앱이 주행 중 계속 실행될 수 있다는 보증이 아니다.* 지역별 주행 규제 문서에서는 DO 미선언 Idling/Driving의 기본 동작을 `종료`로 안내한다. 따라서 앱 생명주기·백그라운드 구독·TTS/AudioFocus의 실행·허용 여부를 에뮬레이터에서 확인 전에는 `NOT_VERIFIED`다. 정책 적용에는 규정 대장 §5·§10을 우선한다.

**과거에 수집한 원문 3개(허용 증명 아님)**
| 원문 | 뜻 |
|---|---|
| "주행 규제 상태에서 **영상 재생**을 시작하거나 재개하지 않도록 처리하고, **오디오 재생은 오디오 포커스 개발 가이드에 정의된 정책을 준수**합니다." | 영상 금지, 오디오는 정책 준수 시 허용 |
| "터치스크린 조작을 최소화하고, **음성 명령이나 물리 버튼** 등 차량에서 제공하는 입력 방식과 연계한 인터랙션을 **지원합니다**." | 음성은 권장 |
| "운전에 중요한 **오디오 안내**가 재생될 때는 오디오 포커스 정책에 따라 미디어 재생을 일시 중지하거나 볼륨을 조정" | 주행 중 오디오 안내는 플랫폼이 인정하는 패턴 |

**TTS**
- `ai.pleos.playground.tts.TextToSpeech(context, Mode.ON_DEVICE)` — **ON_DEVICE는 앱 등록 신청 없이 사용 가능**(SERVER 모드는 신청 필요)
- `setAudioAttributes(...)`, `setAudioZoneId(...)` 제공 → 운전석 구역만 재생 가능 여부 Day1 확인
- 미지원 시 Android 기본 `android.speech.tts.TextToSpeech`로 대체

**오디오 포커스 (지키지 않으면 심사 탈락 사유)**
| 단계 | 할 일 |
|---|---|
| 재생 전 | `AudioAttributes`(usage = ASSISTANCE_NAVIGATION_GUIDANCE 또는 ASSISTANT) + `AudioFocusRequest` 요청 |
| 포커스 종류 | 음악과 공존해야 하므로 **`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`** (음악을 끄지 않고 볼륨만 낮춤) |
| `AUDIOFOCUS_LOSS` | 즉시 중단, 재시도 안 함 |
| `AUDIOFOCUS_LOSS_TRANSIENT` | 그 발화를 **버린다**(나중에 다시 말하지 않음) |
| `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | 그 발화 생략 |
| 재생 후 | `abandonAudioFocusRequest` 반드시 호출 |

- CarAudioContext 우선순위: MUSIC / NAVIGATION / VOICE_COMMAND / CALL / ALARM / NOTIFICATION / SYSTEM_SOUND / EMERGENCY / SAFETY / VEHICLE_STATUS / ANNOUNCEMENT — 우선순위는 **R(운전 관련) > E(긴급) > C(기타)**
- 내비·통화·경고음은 우리보다 상위 → 우리가 양보하는 쪽이다

**NaviHelper** (신청 없이 사용 가능)
- `onCurrentRoadSpeedLimit`(제한 속도), `onCameraAlert`, `onTBTInfo`, `requestRouteWithWaypoints`
- 제한 속도 초과 음성은 **기본 꺼짐**(내비가 이미 안내 → 중복·잔소리). 설정으로만 켤 수 있게 둔다

## 5-1. AAOS 권한 등급 (Android Car 레퍼런스, 2026-11-05 확인)
| 권한 | 등급 | 의미 |
|---|---|---|
| android.car.permission.CAR_POWERTRAIN | Normal | 기어·주차 브레이크 — 설치만으로 허용 |
| android.car.permission.READ_CAR_PEDALS | Dangerous | 페달 압력 — 사용자 허용 필요 |
| android.car.permission.CAR_SPEED | Dangerous | 속도 — 사용자 허용 필요 |
| android.car.permission.READ_ADAS_STATES / READ_ADAS_SETTINGS | **Signature\|Privileged** | 차선이탈·전방충돌·후측방 경고 상태 — **서드파티 앱 불가** |
| android.car.permission.READ_DRIVER_MONITORING_STATES | **Signature\|Privileged** | 운전자 모니터링 — 서드파티 앱 불가 |

## 5-1a. ⚠️ 페달 압력 상수가 공개 SDK stub에 없다 (2026-11-08, 실제 빌드로 확인)

공식 레퍼런스(developer.android.com/reference/android/car/VehiclePropertyIds)에는 API 35로 적혀 있는데, **실제 Android SDK 35의 `platforms/android-35/optional/android.car.jar`에는 두 상수가 export돼 있지 않다.**

```
$ javap -p android/car/VehiclePropertyIds.class | grep -i "PEDAL\|GEAR_SEL\|PARKING_BRAKE\|PERF_VEHICLE"
  public static final int GEAR_SELECTION;          ← 있음
  public static final int PARKING_BRAKE_AUTO_APPLY; ← 있음
  public static final int PARKING_BRAKE_ON;         ← 있음
  public static final int PERF_VEHICLE_SPEED;       ← 있음
  public static final int PERF_VEHICLE_SPEED_DISPLAY;
  (BRAKE_PEDAL_COMPRESSION_PERCENTAGE / ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE 없음)
```

이름으로 참조하면 `Unresolved reference`로 컴파일이 깨진다(이것이 11/8까지 앱이 한 번도 빌드되지 않은 원인 중 하나였다).

**대응(`AaosVehicleSource.kt` companion object)**
- 숫자 ID를 직접 적지 않는다 — 확인하지 않은 값을 쓰는 것이 되므로(CLAUDE.md §6)
- `VehiclePropertyIds::class.java.getField(name).getInt(null)`로 **실행 중에** 찾는다
- 못 찾으면 `brakePedalPct`·`accelPedalPct`를 `UNSUPPORTED`로 두고 페달 미션을 자동 생략한다(F5가 이미 가진 경로)
- 차량·에뮬레이터의 실제 `android.car` 구현에 상수가 있으면 그대로 동작한다

**Day1 할 일(B03)**: 에뮬레이터에서 `adb logcat -s AaosVehicleSource`로 `Supported AAOS properties:` 로그를 받아
① 페달 속성이 목록에 있는지 ② 있으면 리플렉션이 ID를 찾는지 확인하고 §1 표의 '에뮬레이터 수신' 열을 채운다.
없으면 **페달 미션의 기본은 Pleos Brake SDK `current-brake-pedal-status`**로 간다(이미 그렇게 바꿔 뒀다, docs/01 F5).

## 6. 규정 요약(코드에 영향)
- DO(distractionOptimized) 미선언 앱 = 주행 중 **화면** 사용 불가 → 선언하지 않는다. 주행 중에는 **시스템이 앱 화면을 가릴 가능성이 크다**(앱 잠금 화면이 보이는지 Day1 확인, B37)
- **[정정/NOT_VERIFIED]** DO 미선언 앱의 주행 중 사용은 지역별 정책의 기본표에서 제한/종료다. 오디오 포커스 준수는 재생 정책의 조건이지, Activity·서비스 유지나 주행 중 음성 길잡이 허용을 보증하지 않는다. SDK 문서/앱 카테고리와 에뮬레이터 관측 확인 전까지 주행 경로는 사용 가능 상태로 선언하지 않는다. [COMPLIANCE_REGISTER.md](COMPLIANCE_REGISTER.md) X01 참고
- CarUxRestrictions 기준 제한, 속도 0 ≠ 주차
- 미디어·게임은 정차 중에만, 주행 시 일시정지·숨김
- 백그라운드: 갱신·애니메이션·센서 수집 중단, 복귀 시 상태 복원
- 성능: 비내비 앱 주행 중 CPU <5%, 정차 ≤25%, RAM ≤400MB / 입력 응답 0.25초 / 1~2초 안에 파악 / 한 손 조작
- 화면: Driving view(시스템 영역)는 가릴 수 없음, 세로 1창 / 가로 2창
- 개인정보: PII 최소, 운전 중 행동 데이터 마케팅 금지, 공유 기기(프로필 전환·로그아웃)
- 실차 판별: Build.DEVICE = CONNECT_S / CONNECT_L / CONNECT_W (실차만)
- 속도는 PERF_VEHICLE_SPEED(클러스터와 동일), GPS 속도 쓰지 말 것

## A01 Issue #16: 실제 SDK 2.3.2 연동 증거 (2026-11-13 KST)

**코드 기준 `dd0a5a17d97457158598f112a0038563942707da`, 기반 dev `b66f28e70a32c4b474cb85ecc02841c1e3a6fb8e`.** 이전의 Pleos TODO 설명은 이 절로 대체한다. Seat 벨트·Brake·TurnSignal 읽기 어댑터는 구현·컴파일되었지만 **실제 수신은 NOT_VERIFIED**다. 다른 Pleos 필드는 계속 연동 미구현 `UNSUPPORTED`이며, 차종 미지원 실측을 뜻하지 않는다.

### 아티팩트와 실제 시그니처

로컬 `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/Vehicle/2.3.2`의 AAR, sources.jar, javadoc.jar 존재 확인. ZIP을 작업용 tmp에 풀고 `jar tf` 및 `javap -public -classpath <AAR의 classes.jar> <클래스>`로 소스와 바이너리를 대조했다. SDK 바이너리/원문 소스/로컬 설정은 Git에 넣지 않았다.

- `Vehicle-2.3.2.aar` SHA-256: `E11FDFE498CAD3ECC0D5A2A6992B0C4A4D135195F1455018127BC069FD5EF937`
- `Vehicle-2.3.2-sources.jar` SHA-256: `62B5DD2C4C0523FD13C2E64040CD775BC89A14CE54F35A37560D0EB4EB3FAB40`
- 진입점: `ai.pleos.playground.vehicle.Vehicle(Context)` / `initialize(): Unit` / `getSeat(): Seat` / `getBrake(): Brake` / `getTurnSignal(): TurnSignal` / `release(): Unit`.

아래는 **실물에서 확인한 Kotlin 시그니처의 요약**이다. `failed` 인자는 모두 `(Exception) -> Unit`이다.

| 도메인 | capability / 초기 읽기 / 구독 / 해제 | 값·영역·조회 권한 |
|---|---|---|
| Seat | `checkSeatBeltBuckledCapabilities((CapabilityConfig, List<SeatArea>) -> Unit, failed)` / `getCurrentSeatBeltBuckledState(SeatArea, (Boolean?) -> Unit, failed)` / `registerSeatBeltBuckledState(SeatArea, SeatBeltBuckledStateListener)` / `unregisterSeatBeltBuckledState(SeatArea, SeatBeltBuckledStateListener)` | `SeatArea(SeatValue.ROW_1_LEFT)` 단독 영역, 한국 LHD 에뮬레이터 시험 한정. `CAR_SEATS` |
| Brake | `checkBrakePedalStatusCapabilities((CapabilityConfig) -> Unit, failed)` / `getCurrentBrakePedalStatus((Boolean?) -> Unit, failed)` / `registerBrakePedalStatus(BrakePedalStatusListener)` / `unregisterBrakePedalStatus(BrakePedalStatusListener)` | `true`=밟음. 연속 압력 값 아님. 글로벌 API라 area 인자 없음. `CAR_DRIVE` |
| TurnSignal | `checkTurnSignalStateCapabilities((CapabilityConfig) -> Unit, failed)` / `getCurrentTurnSignalState((TurnSignalValue?) -> Unit, failed)` / `registerTurnSignalState(TurnSignalStateListener)` / `unregisterTurnSignalState(TurnSignalStateListener)` | `OFF/LEFT/RIGHT`만 존재. 앱의 `NONE/LEFT/RIGHT`에 매핑. HAZARD 추정 금지. `CAR_LIGHTS` |

리스너는 `ai.pleos.playground.vehicle.listener` 패키지의 `onSeatBeltBuckledStateUpdated(Boolean?)`, `onBrakePedalStatusUpdated(Boolean?)`, `onTurnSignalStateUpdated(TurnSignalValue?)` 및 공통 `onFailed(Exception)`이다. `CapabilityConfig.accessType`이 `READ/READ_WRITE`인 경우만 읽는다. 운전석 단독 area가 없거나 WRITE/NONE이면 미지원으로 차단한다. `pleos.car.permission.*` 조회 권한은 기존 매니페스트 그대로이며 추가 권한은 없다.

**배포물 주의:** sources/공식 문서의 `VehicleException`은 실제 AAR에서는 난독화되어 해당 공개 이름으로 import할 수 없다. `ErrorCode`는 runtime 의존 `vehicle-data:1.1.11`에 있다. 난독화 이름 import/리플렉션/숫자 오류 코드 우회는 하지 않았다. 앱은 공개 `Exception`만 받고, 권한 선확인 및 `SecurityException`, sources에서 확인한 정확한 오류명 메시지만 분류한다. 그 밖의 숫자·문자열·값 타입/변환 오류는 `UNKNOWN`으로 폐기한다. 메시지 이름도 플랫폼별로 보장된다고 주장하지 않으며 실제 오류 전달은 미검증이다.

### 수명·안전 폴백과 기본 차단

- capability 요청 순서: 벨트 → 브레이크 → 방향지시등. 각 신호는 capability 성공 후 초기 읽기 및 listener 등록. 실패는 센서별로 격리하며 다른 신호를 Mock으로 대체하지 않는다.
- 최초 응답 전 `UNKNOWN`, 미지원 `UNSUPPORTED`, 권한 거부 `PERMISSION_DENIED`, 연결 오류/5초 응답 제한 초과 `DISCONNECTED`. null/잘못된 타입은 `UNKNOWN`. 모두 value=null이다.
- SDK에 공개 연결 상태 listener가 없으므로 활성 중 1초마다 확인된 getter로 연결 상태를 확인한다. 타임아웃은 monotonic clock 기준. 요청이 진행 중이면 중복 요청하지 않고, 한 번 실패한 채널은 재시작 전까지 잠근다. 새 이벤트가 먼저 오면 이전 getter 결과/실패로 덮어쓰지 않는다.
- pause에서 중지·값 폐기, resume에서 새 구독. repository generation 및 AAOS의 세션별 callback/sink가 이전 연결 이벤트를 거부한다. ViewModel은 중지 스냅샷을 동기 반영해 미션 재개가 StateFlow 중간 이벤트 전달에 의존하지 않게 했다.
- stop은 먼저 세션을 닫아 늦은 이벤트를 거부한 뒤 각 listener를 해제한다. 하나의 해제 실패도 다른 해제를 막지 않는다. 진행 중 capability/get callback을 배출한 후 `release()`한다.
- **공개 취소 API 부재 한계 / NOT_VERIFIED:** SDK가 끝내 응답하지 않으면 조기 release를 강행하지 않는다. 해당 source 인스턴스는 추가 client 생성을 차단하고 sink 참조를 버린다. 한 client는 응답 또는 프로세스 종료까지 남을 수 있다. 응답이 배출되면 release 후 다음 foreground 재시작 가능, 영구 미응답이면 앱 프로세스 재시작 필요. 에뮬레이터에서 자원 회수·연결 복구를 별도 검증해야 한다.
- **기본 `PLEOS_SIGNALS_ENABLED=false`.** `-PenablePleosSignals=true`로 만든 debug에서만, 출처가 EMULATOR인 경우 시험한다. 실차와 release는 opt-in을 줘도 차단. 이 opt-in은 실환경 검증·출시 승인 표시가 아니다. AAOS 주차 게이트는 기존 경로를 유지하며 이 어댑터가 기어/주차브레이크/UX 제한을 발행하지 않는다.

### 공식 근거와 남은 실측

확인일 2026-11-13, [Vehicle 소개/버전/수명](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [Seat](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry), [Brake](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry), [TurnSignal](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/entry), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [오류](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code), [호환성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability). 온라인 API Reference 추출에는 시그니처 본문이 노출되지 않아 실제 AAR/sources 및 컴파일을 함께 근거로 삼았다. 공모전 O03 URL 재조회는 접근 실패; 기존 규정 대장을 참고했으며 이번 조회로 공모전 승인을 새로 확인한 것은 아니다.

설치된 AVD는 `Medium_Phone_API_37.0` 하나, 이미지 tag는 `google_apis_playstore`; Pleos 이미지 없음. `adb devices -l`은 빈 목록. 따라서 앱 설치·Virtual Console→앱 callback·운전석 매핑·실제 권한/주차 게이트·앱 생명주기·실차는 모두 **NOT_VERIFIED**다. 일반 전화 AVD를 Pleos 검증에 대체하지 않았다.

준비 후: opt-in vehicleDebug 빌드 → Pleos AVD에 설치 → `adb forward tcp:8088 tcp:8088` → P/주차브레이크/UX 해제 확인 → 벨트 미체결/체결, 브레이크 밟음/뗌, 방향지시등 좌/우/끔을 변경한다. `adb logcat -v time -s PleosVehicleSource AaosVehicleSource`의 `SDK callback sensor=... value=...`와 앱의 snapshot/미션·출처를 함께 기록한다. get 응답, callback 로그, 앱 화면 판정은 각각 구분한다. 이 절은 시험 절차이며 실행 결과가 아니다.
