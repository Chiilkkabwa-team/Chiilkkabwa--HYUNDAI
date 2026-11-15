# A02 차량 속도 변화 이벤트 엔진

작업: `feature/a02-speed-events`, A02 worktree. 착수 기반 `origin/dev e3958dad1bf159479d75e3fc1821969e942b8e59`, 최종 검증 기반 `b47e96d9f28437e6ac218ec1140a66e14de480c6` (A01 PR #18 통합 후 fast-forward).
범위: 이 문서는 독립 Kotlin 코어와 JVM 테스트를 설명한다. 별도 AAOS 속도 입력 브리지와 에뮬레이터 재현 절차는 [A02 속도 브리지](A02_SPEED_BRIDGE.md)에 있다.

## 구조와 입력/출력 계약

- `core/event/SpeedEvents.kt`: `SpeedSample`, `SpeedUnit`, `SpeedEventConfig`, `SpeedChangeEvidence`, `SpeedChangeEvent`, 결과/사유 enum.
- `core/event/SpeedEventEngine.kt`: `SpeedEventEngine(sessionId, config)`의 상태와 `update(sample: SpeedSample?, nowMs: Long): SpeedDetectionResult`, `reset()`.
- `core/event/SpeedEventEngineTest.kt`: 정상·경계·오류·쿨다운·출처·세션 격리 회귀 테스트.

입력 `SpeedSample(speed: Double?, timestampMs: Long, unit: SpeedUnit, source: SignalSource, status: SignalStatus)`는 기존 출처/상태 enum을 재사용한다. 단위는 반드시 `METERS_PER_SECOND` 또는 `KILOMETERS_PER_HOUR`로 지정한다. 속도는 비음수 크기이며 음수를 후진으로 추측하거나 abs 처리하지 않는다.

시각은 같은 세션의 동일 밀리초 시계로 공급한다. 획득 시각을 권장하며 callback 도착 시각/센서 monotonic 시각/epoch 시각을 섞지 않는다. `nowMs`도 동일 시계여야 하며, 표본 시각과 처리 시계의 역전을 각각 차단한다. **현재 `Signal.receivedAtMs` 어댑터는 `System.currentTimeMillis()` 기반 벽시계 시각을 사용한다.** 따라서 `SpeedSample.fromSignal(signal, unit)`를 그대로 사용할 때는 `nowMs`도 같은 벽시계 기준이어야 한다. 단조 시계를 채택한다면 연결 계층에서 표본 획득 시각을 `elapsedRealtime()` 기준으로 별도 공급해야 하며 벽시계 timestamp와 섞으면 안 된다. 시계 기준이 바뀌면 새 고유 sessionId로 새 엔진을 만든다. 이 변환 함수는 지원·권한·실측 여부를 인증하지 않는다. 공급자가 확인하지 못한 신호를 OK로 올리지 않는다.

반환은 세 종류다.

| 결과 | 의미 |
|---|---|
| `Baseline(sample, reason)` | 최초/단절 후 첫 유효값, 출처 변경, 긴 간격 이후의 새 기준값. 이벤트 없음 |
| `Rejected(reason, sample)` | 누락·오류·불명·시각·범위·정밀도 등으로 판정 불가. 이벤트 없음 |
| `Evaluated(evidence, event?, suppression?)` | 유효 표본 쌍의 실제 계산 근거. 정상 변화이면 event=null, 중복/쿨다운이면 suppression 명시 |

이벤트에는 `eventId`, `sessionId`, `type`, `occurredAtMs`, `source`, 부호를 가진 `triggerThresholdMps2`, 근거가 있다. 근거는 **두 원본 표본(원단위·상태·출처·시각 포함)**, 정규화 m/s 값, intervalMs, deltaSpeedMps, accelerationMps2와 roundingToleranceMps2를 담는다. `eventId = <sessionId>:speed:<증가번호>`이며 발생 시각은 현재 표본 시각이다. 세션 상대 시간은 통합 계층이 같은 시계의 세션 시작값을 빼서 만든다.

이 모델은 A02의 출력 계약이다. 실제 속도 입력 브리지의 이벤트를 B/C 화면·음성·복기에 전달하는 앱 경로는 아직 미연결이다. B/C가 연결할 때 eventId·sessionId·유형·시각·근거·source를 그대로 매핑하되, 급감속을 HARSH_BRAKE/충돌/앞차 위험으로 이름만 바꾸지 않는다.

## 판정식과 초기 설정

`v(m/s)=v(km/h)/3.6`, `dt(s)=(현재시각-이전시각)/1000`, `a=(현재속도-이전속도)/dt`.

| 설정 | 기본값 | 동작 |
|---|---|---|
| increaseThresholdMps2 | 2.5m/s² (=9km/h/s) | 이상이면 RAPID_SPEED_INCREASE 후보 |
| decreaseThresholdMps2 | 12/3.6 ≈3.333333m/s² | a가 음수 임계값 이하이면 RAPID_SPEED_DECREASE 후보 |
| minIntervalMs / maxIntervalMs | 50 / 2000ms | 짧으면 기존 anchor에 누적, 길면 현재값으로 기준 재설정 |
| maxSampleAgeMs | 1000ms | 정확히 1000ms까지 허용, 그보다 오래되면 폐기 |
| maxSpeedMps | 100m/s (=360km/h) | 범위 밖 속도 폐기; 실제 차량의 최대속도 인증 아님 |
| maxAbsAccelerationMps2 | 15m/s² | 초과하면 이상치로 폐기, 다음 값도 기준부터 다시 시작 |
| cooldownMs | 6000ms | 출처·유형별 마지막 발행 이후 최소 간격, 정확히 경계에서 새 구간 발행 가능 |
| releaseRatio | 0.6 | 증가 a≤1.5, 감소 a≥-2.0 근처로 회복해야 같은 유형 구간을 다시 발행 가능 |

모든 설정은 생성 시 범위·유한성을 검사한다. 증가/감소/쿨다운은 기존 `web/voice.js`의 모의 초기 수치를 참고했지만, 브레이크 입력과 위험 추론은 가져오지 않았다. 나머지는 A02의 보수적 개발용 필터 초안이다. **모든 값은 실차 센서·운전 환경에서 보정/검증되지 않은 초기값이며 안전·법규 기준이 아니다.**

부동소수점 변환으로 72→60km/h의 정확한 -12km/h/s가 경계에서 누락되는 회귀를 재현했다. 계산마다 `((ulp(v0)+ulp(v1))/dt + ulp(a))*4`의 작은 산술 오차 범위를 근거에 기록하여 임계값·회복·이상치 경계를 비교한다. 이는 센서 노이즈 허용이나 임계값 보정이 아니다. 오차 범위가 임계값과 회복 임계값 사이 여유 이상이면 정밀도 불충분으로 판정을 거부한다. 실제로 임계값보다 작은 속도 변화는 오차 범위 밖에서 그대로 미발행한다.

## 단절·중복·출처 정책

- `status != OK`는 모두 폐기한다. 값이 숫자여도 STALE/UNKNOWN/UNSUPPORTED/PERMISSION_DENIED/DISCONNECTED를 정상값으로 쓰지 않는다.
- missing/null/NaN/Infinity/음수/속도 상한 초과/미래/오래된 값/역전/이상 변화는 연속성을 끊는다. 오류 전후 값을 연결하여 이벤트를 만들지 않는다. 값이 오지 않는 동안 엔진은 자체 타이머를 돌리지 않으므로 공급자는 timeout/연결 중단 시 `update(null, nowMs)` 또는 `reset()`을 호출한다. 다음 유효값에도 maxInterval 검사를 적용한다.
- 동일/역전 표본은 거부하고 기존 시각 상한을 유지하여 오래된 표본 쌍의 재생을 막는다. 처리 시계가 역전되면 마지막 정상 처리 시각을 따라잡기 전까지 계산하지 않는다.
- 간격이 50ms 미만인 유효한 고빈도 표본만 예외적으로 anchor를 유지한다. 이후 최소 간격을 만족하는 표본과 계산하므로 10ms 단위 입력을 모두 잃거나 작은 dt로 변화율을 부풀리지 않는다.
- 출처가 바뀌면 기준과 구간을 다시 시작한다. Mock↔Emulator↔Vehicle 표본끼리 변화율을 계산하지 않는다. 단위 변경은 명시적 변환 후 같은 출처 안에서 허용한다.
- 지속 임계값 초과 구간은 한 번만 발행하며 쿨다운이 끝나도 연속 구간을 반복 발행하지 않는다. 회복 이후의 새 구간도 쿨다운을 지켜야 한다. 쿨다운으로 억제된 구간을 나중에 소급 발행하지 않는다. 반대 방향은 별도 유형이다.
- `reset()`은 anchor/구간만 버린다. 시각 상한·발행번호·쿨다운을 유지하여 일시 중단으로 중복 방지를 우회하지 않는다. 엔진은 세션마다 한 개, 한 실행 문맥에서 순서대로 사용한다. 프로세스 재생성/새 엔진에는 새 고유 sessionId가 필요하며 인스턴스 간 영속 중복 제거는 하지 않는다.
- 메모리는 이전 표본 1개와 고정 크기 출처×유형 맵만 보유한다. 원시 시계열·위치·프로필·차량 식별자를 저장/업로드하지 않는다.

## A01 및 B/C 연결 시 필요한 일

```kotlin
val engine = SpeedEventEngine(sessionId = "고유한-주행세션-ID")
val result = engine.update(
    SpeedSample(36.0, 1000L, SpeedUnit.KILOMETERS_PER_HOUR,
        SignalSource.MOCK, SignalStatus.OK),
    nowMs = 1000L,
)
// 이 예제는 명시적인 Mock. 첫 값은 Baseline이며 이벤트가 아니다.
```

현재 AAOS 입력은 `SpeedEventBridge`가 연결한다. `VehicleSpeedObservation`의 원본 단위·출처·상태를 유지하고, `elapsedRealtimeNanos()` 기반 시각과 처리 시각을 같은 밀리초 기준으로 엔진에 전달한다. 입력이 끊기면 브리지가 세션을 정리하고 재연결 시 새 기준부터 시작한다. 활성화 조건과 재현 명령은 [A02 속도 브리지](A02_SPEED_BRIDGE.md)를 참고한다.

판정은 두 표본 사이의 평균 속도 변화다. 브레이크/가속 페달을 밟았는지, 급출발 의도, 주변 차량·차선·충돌 위험, 운전 실력은 확정하지 않는다. Pleos 에뮬레이터에서 속도 수신과 증가·감소 이벤트를 확인했다. 속도 단계와 짧은 표본 간격이 임계값을 넘기는 경우도 관측했다. 센서·임계값 보정, 실차 검증과 B/C 실사용 연결은 남아 있다.

## 유지보수와 디버깅

`dev`에는 Android 속도 판정 코어와 [AAOS 속도 입력 브리지](A02_SPEED_BRIDGE.md)가 있다. 기본 설정에서는 속도 수집이 꺼져 있으며, 에뮬레이터에서는 명시적으로 활성화한 `vehicleDebug`로 검증했다.

### 코드 읽는 순서

1. [SpeedEvents.kt](../app/src/main/java/com/chiilkkabwa/app/core/event/SpeedEvents.kt)에서 표본의 단위·출처·상태와 반환 타입을 확인한다.
2. [SpeedEventEngine.kt](../app/src/main/java/com/chiilkkabwa/app/core/event/SpeedEventEngine.kt)의 `update()`는 입력 상태와 시각을 검사하고, 같은 출처의 유효한 표본 두 개를 m/s로 바꿔 평균 속도 변화율을 계산한다. 임계값을 넘으면 회복 조건과 쿨다운을 검사한 뒤 사건을 발행한다.
3. [SpeedEventEngineTest.kt](../app/src/test/java/com/chiilkkabwa/app/core/event/SpeedEventEngineTest.kt)에서 수정하려는 동작의 기존 테스트를 찾는다. 예를 들어 정확히 임계값에 걸치는 감속은 `default_decrease_threshold_is_inclusive_after_kmh_conversion`, 입력 단절은 `every_non_ok_status_breaks_continuity_even_with_a_numeric_value`에서 확인한다.

### 무엇을 고칠 때 어디를 볼까

| 변경 또는 증상 | 확인할 위치와 순서 |
|---|---|
| 임계값·최소 표본 간격·쿨다운 조정 | `SpeedEvents.kt`의 `SpeedEventConfig`부터 본다. 정확히 경계, 경계 바로 안팎, 회복 후 재발행을 테스트한다. 운영 값은 에뮬레이터나 차량 계측 근거로 정한다. |
| 속도를 바꿨는데 첫 사건이 안 나옴 | 첫 표본은 `Baseline`이어야 한다. 다음 표본의 `status`, `source`, 속도 단위, 시각 기준과 간격을 확인한다. |
| 표본은 유효한데 사건이 빠지거나 반복됨 | `Evaluated`의 `event`와 `suppression`을 확인한다. `ONGOING_EPISODE`면 같은 구간이 이어진 상태이고, `COOLDOWN`이면 같은 출처·유형의 발행 간격이 짧은 상태다. |
| Pleos 에뮬레이터 속도 입력 수정 | 센서 단위·시각·권한·미지원 동작은 신호 어댑터에서 확인한다. 세션당 엔진 한 개에 순서대로 전달하고, 신호 중단·재연결 시 기준 표본을 초기화한다. Android Mock과 Pleos 입력은 출처를 유지한다. |
| 코칭이나 복기에 사건 연결 | `eventId`, `sessionId`, 발생 시각, 출처와 원본 근거를 유지한다. 음성 재생 정책과 주차 후 복기 잠금은 각 소비자에서 적용한다. 감속 문구는 확인한 속도 변화에 근거한다. |

### Android Studio에서 재검증

저장소 루트를 열고 Gradle Sync를 완료한다. `mockDebug`에서 `SpeedEventEngineTest.kt`를 실행하거나, 저장소 루트의 PowerShell에서 아래 명령을 실행한다.

```powershell
.\gradlew.bat :app:testMockDebugUnitTest --tests 'com.chiilkkabwa.app.core.event.SpeedEventEngineTest'
.\gradlew.bat :app:testMockDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug
```

Pleos 어댑터까지 수정한 PR에서는 속도 콜백 수신 값과 단위 → 사건에 기록된 원본 근거 → 신호 끊김 뒤 새 기준값을 순서대로 확인한다. 빌드와 단위 테스트, Pleos 에뮬레이터 수신, 실차 결과는 [검증 기록](VERIFICATION_LOG.md)에 각각 기재한다. 주행 경로의 제한과 출처 표시 기준은 [안전 원칙](SAFETY_RULES.md)의 S05·S07·S10을 따른다.

## 후속 Android 기능 PR에 함께 기록할 내용

- 변경한 파일과 바꾼 이유, 입력부터 화면·이벤트까지의 코드 흐름
- 다음 담당자가 설정값을 바꾸거나 오류를 추적할 때 볼 위치와 명령
- A·B·C가 공유하는 데이터의 필드·단위·출처와 호환성에 미치는 영향
- 실행한 시험과 결과, 미검증 경로 및 재현에 쓰는 빌드 변형·기기
