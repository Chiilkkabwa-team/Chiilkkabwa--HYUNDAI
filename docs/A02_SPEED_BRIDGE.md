# A02 AAOS 차량 속도 입력과 이벤트 연결

작업 기반 `1acc54036c5660ff05d572dd904d250762f0dad5`, 브랜치 `feature/a02-aaos-speed-bridge`, Codex1 전용 관리 worktree. 기존 SpeedEventEngine의 임계값·쿨다운·히스테리시스는 수정하지 않는다.

## 공개 API·권한 근거

[VehiclePropertyIds.PERF_VEHICLE_SPEED](https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED): 읽기 전용 global/continuous Float, m/s, 후진 음수. [Car.PERMISSION_SPEED](https://developer.android.com/reference/android/car/Car#PERMISSION_SPEED)는 `android.car.permission.CAR_SPEED` dangerous 권한. [CarPropertyValue.getTimestamp](https://developer.android.com/reference/android/car/hardware/CarPropertyValue#getTimestamp())는 elapsedRealtimeNanos 기준 ns다. [CarPropertyManager.registerCallback](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager#registerCallback(android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback,int,float))로 지원/config 확인 후5Hz를 요청하고 등록·해제한다. 이 API는 최신 문서에서는 deprecated지만 로컬 API35 stub과 대상 API34 공개 API에 존재한다. 새 버전 전용 API를 추측하지 않는다.

Temurin17 javap로 로컬 `platforms/android-35/optional/android.car.jar`의 상수, Car.createCar(Context,Handler,long,CarServiceLifecycleListener), CAR_WAIT_TIMEOUT_DO_NOT_WAIT, getCarPropertyConfig, registerCallback/unregisterCallback, getTimestamp/getStatus/getValue를 확인했고 컴파일했다. 숫자 property ID/비공개 API/제어 API/GPS를 사용하지 않는다. 공개 문서·stub 확인은 Pleos 런타임 지원이나 공모전 주행 수집 승인을 의미하지 않는다.

## 파일·입출력 계약

- `core/signal/VehicleSpeedObservation.kt`: 부호 있는 `rawMps: Float?`, 원본 `timestampNanos`, 수신 `receivedAtNanos`, `source/status/sessionId`. 유효한 관측만 `kilometersPerHour`를 제공하며 후진도 부호를 보존한다. 기존 epoch 기반 `Signal.receivedAtMs` 계약은 바꾸지 않았다.
- `VehicleSnapshot.speed`: 끝에 기본 UNKNOWN 필드를 추가했다. 기존 호출·필드 계약 보존. repository stop에서 raw/time을 지우고 DISCONNECTED로 폐기한다.
- `core/event/SpeedEventBridge.kt`: start(sessionId)/accept(observation,nowNanos)/stop. 활성 sessionId가 아닌 입력은 무시한다. 정확한 ns에서 미래·오래됨·수신 시계 역전을 검사하고, 표본과 now를 모두 elapsed ms로 나눠 기존 엔진에 전달한다. 후진 원본은 보존하되 엔진의 SPEED_OUT_OF_RANGE 거부와 연속성 초기화를 사용한다. abs나 앞/뒤 기어 추론 없음.
- `data/vehicle/SpeedSignalSession.kt`: 순수 Kotlin 권한/지원/연결/등록 세션. 새 시작마다 고유 ID, generation으로 늦은 callback 차단. 등록 중 동기 callback은 등록 성공 이후만 반영한다.5Hz 요청,200ms foreground heartbeat로 마지막 원본 timestamp가1000ms를 넘으면 STALE. timeout은 새 속도 표본을 만들지 않으며 다음 유효값은 Baseline이다.
- `AaosSpeedReadClient.kt`: async Car lifecycle, permission 정의/부여, global/Float/continuous/readable config, area0 callback만 수용. definition 없음=UNSUPPORTED, 거부/보안 예외=PERMISSION_DENIED, config 없음=UNSUPPORTED, 연결 예외/종료=DISCONNECTED, 등록false·일반 callback 오류=UNKNOWN. AVAILABLE 외 value는 읽지 않는다.
- `AaosSpeedSource.kt`: 세션을 VehicleSource로 감싸고 debug opt-in 진단만 기록한다. `AaosVehicleSource`에서 독립 위임해 기존 ViewModel/레이아웃을 변경하지 않았다. stop은 heartbeat와 client를 해제한다.
- `MockVehicleSource.setSpeedMps`: 명시적 MOCK 입력과 주입 가능한 단조 시계/session factory 및 `onSpeedResult` callback. 기어·페달로 속도를 만들어내지 않는다. UI 제어 추가 없음.

## 시계·생명주기

AAOS property ns와 receipt/now는 SystemClock.elapsedRealtimeNanos 한 기준이다. original timestamp를 callback 도착 시각으로 치환하지 않는다. 세션 시작 이전 값, 미래 ns(1ns 포함),1000ms 초과 오래된 값, null/타입 오류/NaN/Infinity는 계산 연속성을 끊는다. engine에 전달하는 timestampMs/nowMs는 ns/1,000,000이며 epoch 시각을 섞지 않는다. 같은 ms에 든 중복/역전은 기존 엔진 정책으로 거부한다.

Mock의 기본 시계도 elapsedRealtimeNanos이며 JVM 시험에서는 같은 기준의 가짜 시계를 명시적으로 주입한다. 오류 전후·출처 전환·세션 전환을 연결해 이벤트를 만들지 않는다. 서비스가 끊기면 현재 세션은 terminal이고 Activity의 다음 stop/start에서만 새 세션을 만든다. 무한 자동 재접속하지 않는다.

## 비활성화와 소비 제한

`-PenableAaosSpeed=true :app:assembleVehicleDebug`에서만 opt-in한다. 기본 `AAOS_SPEED_ENABLED=false`, release는 옵션과 무관하게false다. 추가로 DEBUG && EMULATOR 출처 && Build.HARDWARE ranchu/goldfish를 모두 요구한다. 실차·일반 물리 단말에서는 열리지 않는다. CAR_SPEED runtime request도 이 조건+권한 정의 존재에서만 수행한다. 거부를 강제 허용하지 않는다.

수집은 기존 Activity onResume/onPause의 start/stop에 한정한다. DO·주행 중 백그라운드 수집/음성/터치 허용을 주장하지 않는다. 주행 제한이 Activity를 중단하면 수집도 끝나므로 주행 전체의 연속 수집은 보장하지 않는다. 새 event는 `onResult(SpeedDetectionResult)` callback/opt-in 진단으로만 노출하며 B02 음성/C01 복기/C02 저장소에 연결하거나 영구 저장하지 않는다. speed0으로 주차를 판단하지 않는다.

## B/C 후속 연결 계약

SpeedEventBridge 반환 Baseline/Rejected/Evaluated를 그대로 구분한다. 이벤트는 기존 RAPID_SPEED_INCREASE/DECREASE 의미와 eventId/sessionId/source/occurredAtMs 및 두 원본 sample 근거를 유지한다. occurredAtMs는 epoch가 아니라 해당 부팅의 elapsed ms다. 복기 상대 시각은 동일 시계의 세션 시작값을 빼야 한다. 벽시계 변환·영속 중복 방지·주행 수집 허용·TTS 정책 검증은 이번 범위가 아니다. 감소를 브레이크 조작·충돌·차간거리 위험으로 바꾸지 않는다.

## 검증 상태

최종 코드 `8166fe443b4bd479b12c978be9928c1585cb14c3`. 로컬 mock/vehicle JVM **각107/107**, 두 debug 빌드, 웹109/109 PASS. 기준 dev 각79개에서 신규28개 테스트가 추가됐다. 원본 엔진 테스트·정책은 유지했다. 아래는 실제 Pleos 실측이며 실차나 정책 승인과 구분한다.

### 실제 Pleos 시험 (2026-11-14 KST 약14:01~14:08)

- Pleos Connect2.1.0/API34/x86_64, Vehicle Service2.3.22, Emulator37.2.13/Windows Arc140T/WHPX, 기존 AVD RAM4096MB/4코어/GPUhost. Build.HARDWARE=ranchu, device=crp_avd_x86_64, 사용자10, serial emulator-5554. guest PST 11/13 21시는 KST 11/14 14시다.
- 시험 APK SHA256 `882487C30FC4F72E485C24AD596FF90BA481BFC036E2AC6DAA82417DA75386D6`, AAOS speed opt-in=true, A01 Pleos opt-in=false, USE_MOCK=false. 후속 문서 변경은 시험 코드를 바꾸지 않는다.
- `pm list permissions`의 단순 필터에 CAR_SPEED가 보이지 않았지만 **실제 정의는 존재했다**. `dumpsys package permissions`에서 sourcePackage=com.android.car.updatable, prot=dangerous를 확인했고 `pm grant --user 10 ... CAR_SPEED` exit0 및 사용자10 granted=true를 확인했다. 목록 누락을 미지원으로 처리하지 않는다.
- 기본 APK에는 A02 trace0건. opt-in 및 권한 부여 후 원본 Float m/s/속성ns/수신ns/source=EMULATOR 관측과 Baseline/Evaluated 소비를 확인했다. runtime 전체의 AVAILABLE 관측1733개에서 propertyNs>receiptNs는0개였다. 원본 callback에서 decode/관측을 거쳐 기존 엔진이 소비한 결과는 각각 trace에서 구분한다.
- 콘솔 DDS ONLINE, D/ACCEL 입력에0→증가, BRAKE 지속 입력에감소→0을 관측했다. `RAPID_SPEED_INCREASE`(guest21:02:36.911)와 `RAPID_SPEED_DECREASE`(21:04:16.172)가 실제 입력에서 발행됐다. 정속0 관측에서는 event=null이다.
- **측정 양자화 주의:** 완만한 콘솔 가속에서도 0.8333334→1.1111112m/s 같은 계단과 약101ms 간격이2.5m/s² 임계값을 넘는 사건을 만들었다. 이는 실제 표본에 대한 엔진 계산/파이프라인 PASS이지 실제 급출발·위험 판정의 정확성 인증이 아니다. 초기 임계값·노이즈/주기 보정은 NOT_VERIFIED이며 무단 변경하지 않았다. B/C 실사용 연결 전 보정·검증이 필요하다.
- R 전환 시 CarEvsCameraPreviewActivity가 앱을 가려 동일 PID의 speed가 DISCONNECTED/null로 폐기됐다. P 복귀 시 새 sessionId와 Baseline을 관측했다. 카메라 화면을 우회해 강제 수집하지 않았다. **실제 후진 음수 수신은 NOT_VERIFIED**이며 부호 보존·제외는 JVM에서만 검증했다.
- 권한 철회 후 새 시작에서는 PERMISSION_DENIED/null 및 Rejected, OS GrantPermissionsActivity를 관측했다. 실제 사용자의 Allow/Deny 조작과 자동 재부여 복구 전체는 NOT_VERIFIED다. adb 재부여 후 기존 권한 창이 남는 구간은 복구 성공으로 표시하지 않는다.
- opt-in→기본 OFF 증분 빌드/재설치 후 새 PID1349에서 A02 trace0건. 마지막에는 테스트 CAR_SPEED를 다시 철회하고 앱/우리 로그 수집/AVD 종료, 콘솔P/0km/h 복원, 기존 AVD 데이터 보존.

[실제 증거와 구간](evidence/a02-2026-11-14/README.md). 후진 음수·권한 정의 부재·실서비스 영구 미응답·실차·전체 주행 생명주기/정책은 시험 등급을 별도로 유지한다.

### 검토 및 빌드 플래그 보완

독립 리뷰에서 등록 중 inline callback의 receipt가 등록 완료 시각으로 바뀌는 결함을 발견했다. callback 진입에서 decode한 관측을 pending에 보존하며, 등록 대기 중 오래된 표본은 발행 시 STALE로 내린다. 미래1ns가 대기 중 정상으로 바뀌지 않도록 했다. 또한 같은 ms 안의 now 역전에서도 이벤트가 발생할 수 있는 사례를 재현하여 bridge에 ns high-water를 추가했다. 이 방어 항목은 명시적 시간 역전 거부 계약에 따라 중요 수정으로 처리했다. 네 테스트 RED→GREEN, 전체107/107씩 통과했다.

실기 준비 중 literal BuildConfig boolean을 바꿨는데 Kotlin bytecode에 이전 false가 남는 증분 빌드 현상도 재현됐다. 새 A02 debug 플래그는 `Boolean.parseBoolean`으로 초기화하여 Kotlin이 상수를 인라인하지 않고 GETSTATIC으로 읽도록 했다. 실제 opt-in과 이후 기본false 재설치를 모두 확인했다. release는 여전히 literal false다. 생성된 Java 소스만 보고 활성화를 주장하지 않는다.

## S-ID 및 통합 게이트 (이번 변경 범위)

| 규정 | 판정·근거 |
|---|---|
| S01 | PASS: 공개 속성 조회/config/register/unregister/disconnect만. CONTROL 권한·직접 VHAL·설정/차량 제어 API 추가0 |
| S02 | 공개 API/stub/해당 Emulator 실행 PASS. 공모전·주행 수집 승인 자체는 NOT_VERIFIED. 신규 미공개 SDK·외부 STT 없음 |
| S03 | PASS(회귀/정적): 속도0을 주차로 쓰지 않음, 기존 P+주차브레이크+UX 게이트 무변경 |
| S04 | 카메라 pause의 구독 중지 관측 PASS. 전체 주행 UX 정책·화면 인증 NOT_VERIFIED |
| S05 | NOT_VERIFIED: 실차·release·기본 수집 차단, foreground debug Emulator 시험만. 음성/백그라운드 서비스 미연결 |
| S06 | NOT_APPLICABLE: 이번 음성/오디오 정책 변경 없음 |
| S07 | PASS: signed m/s·원본ns·수신ns·세션/출처·상태 보존, 오류와 역전 차단, 관측 사건과 주변 위험 구분 |
| S08 | NOT_APPLICABLE(미션 변경 없음); 안전 게이트/실차 활성화 변경0 |
| S09 | PASS(변경 범위): 메모리의 고정 크기 엔진 상태와 opt-in 합성 시험 진단만. 새 영구 저장/업로드·계정·VIN·GPS 수집 없음 |
| S10 | PASS: JVM/Mock/Emulator/실차 및 callback 관측/계산/판정 정확성 등급 구분 |
| S11 | NOT_VERIFIED(장시간/CPU/RAM/양자화 보정); 외부 자산 신규 도입 없음 |
| S12 | NOT_APPLICABLE(제출): release APK 업로드·공개 전환·main 변경 없음 |

공식 근거의 적용 범위는 SAFETY_RULES/COMPLIANCE_REGISTER의 O03/P02/P03/P04/P06/P07/P08와 위 Android 원문을 따른다. 문서 열람이나 개발자 자체 검토를 플랫폼 승인으로 바꾸지 않는다.

G1 PASS: 배정된 A02·전용 브랜치/worktree·범위 명시. G2 PASS(공개API/해당실측)/주행정책 NOT_VERIFIED. G3 PASS: default OFF·최소읽기권한·fail-closed·실차/release 차단. G4 아래 실행 등급별 증거만 PASS. G5: 최신 dev 충돌·전체회귀·CI 확인 후 개발 통합만 허용; B/C 실사용·실차·주행 정책 활성화는 하지 않는다.
