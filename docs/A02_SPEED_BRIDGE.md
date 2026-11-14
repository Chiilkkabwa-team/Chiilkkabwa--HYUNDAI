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

구현 후 로컬 mock/vehicle JVM 각103/103, 두 debug 빌드, 웹109/109 PASS. 기준 dev는 각79개였으며 신규24개 시나리오가 추가됐다. 실제 실행 명령과 최종 결과는 VERIFICATION_LOG의 A02 절에 기록한다. Emulator 권한·속성·실수신은 별도 실측 후 갱신하며, 실차와 주행 수집 정책은 NOT_VERIFIED다.
