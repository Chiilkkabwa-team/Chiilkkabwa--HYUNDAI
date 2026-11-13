# A02 차량 속도 변화 이벤트 엔진

작업: `feature/a02-speed-events`, A02 worktree. 착수 기반 `origin/dev e3958dad1bf159479d75e3fc1821969e942b8e59`, 최종 검증 기반 `b47e96d9f28437e6ac218ec1140a66e14de480c6` (A01 PR #18 통합 후 fast-forward).
범위: 독립 Kotlin 코어 + JVM 테스트. SDK/차량 신호·UI·음성·저장소 연결 없음. A01의 금지된 7개 파일과 기존 `VehicleSnapshot`/공유 모델은 수정하지 않는다.

## 구조와 입력/출력 계약

- `core/event/SpeedEvents.kt`: `SpeedSample`, `SpeedUnit`, `SpeedEventConfig`, `SpeedChangeEvidence`, `SpeedChangeEvent`, 결과/사유 enum.
- `core/event/SpeedEventEngine.kt`: `SpeedEventEngine(sessionId, config)`의 상태와 `update(sample: SpeedSample?, nowMs: Long): SpeedDetectionResult`, `reset()`.
- `core/event/SpeedEventEngineTest.kt`: 정상·경계·오류·쿨다운·출처·세션 격리 회귀 테스트.

입력 `SpeedSample(speed: Double?, timestampMs: Long, unit: SpeedUnit, source: SignalSource, status: SignalStatus)`는 기존 출처/상태 enum을 재사용한다. 단위는 반드시 `METERS_PER_SECOND` 또는 `KILOMETERS_PER_HOUR`로 지정한다. 속도는 비음수 크기이며 음수를 후진으로 추측하거나 abs 처리하지 않는다.

시각은 같은 세션의 동일 밀리초 시계로 공급한다. 획득 시각을 권장하며 callback 도착 시각/센서 monotonic 시각/epoch 시각을 섞지 않는다. `nowMs`도 동일 시계여야 하며, 표본 시각과 처리 시계의 역전을 각각 차단한다. `SpeedSample.fromSignal(signal: Signal<Number>, unit)`는 value/status/source/receivedAtMs를 그대로 변환하므로, 기존 receivedAtMs가 epoch이면 nowMs도 같은 epoch를 사용해야 한다. 시계 기준이 바뀌면 새 고유 sessionId로 새 엔진을 만든다. 이 변환 함수는 지원·권한·실측 여부를 인증하지 않는다. 공급자가 확인하지 못한 신호를 OK로 올리지 않는다.

반환은 세 종류다.

| 결과 | 의미 |
|---|---|
| `Baseline(sample, reason)` | 최초/단절 후 첫 유효값, 출처 변경, 긴 간격 이후의 새 기준값. 이벤트 없음 |
| `Rejected(reason, sample)` | 누락·오류·불명·시각·범위·정밀도 등으로 판정 불가. 이벤트 없음 |
| `Evaluated(evidence, event?, suppression?)` | 유효 표본 쌍의 실제 계산 근거. 정상 변화이면 event=null, 중복/쿨다운이면 suppression 명시 |

이벤트에는 `eventId`, `sessionId`, `type`, `occurredAtMs`, `source`, 부호를 가진 `triggerThresholdMps2`, 근거가 있다. 근거는 **두 원본 표본(원단위·상태·출처·시각 포함)**, 정규화 m/s 값, intervalMs, deltaSpeedMps, accelerationMps2와 roundingToleranceMps2를 담는다. `eventId = <sessionId>:speed:<증가번호>`이며 발생 시각은 현재 표본 시각이다. 세션 상대 시간은 통합 계층이 같은 시계의 세션 시작값을 빼서 만든다.

현재 Android 공통 `DriveEvent`는 미정이다. 이 모델은 A02의 로컬 제안 계약이며 B/C의 이벤트/음성/복기 모델로 자동 편입되지 않는다. B/C가 연결할 때 eventId·sessionId·유형·시각·근거·source를 그대로 매핑하되, 급감속을 HARSH_BRAKE/충돌/앞차 위험으로 이름만 바꾸지 않는다.

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

A01 완료 후 별도 어댑터에서 **실제로 확인한** 속도 신호·단위·획득 시각·지원/권한·상태·출처를 공급한다. 현재 VehicleSnapshot에 speed 필드를 추가하거나 SDK/권한을 연결하지 않았다. 실제 샘플의 시계 기준을 합의하고, 누락·권한 거부·background/재연결/세션 종료에서 단절을 알려야 한다. 이 엔진은 기어 P/주행 UX 정책을 판정하거나 화면/음성을 열지 않는다. 주행 중 수집 허용·생명주기·TTS/기록 연동은 별도 규정·에뮬레이터 검증 전 비활성 상태를 유지한다.

판정은 두 표본 사이의 평균 속도 변화다. 브레이크/가속 페달을 밟았는지, 급출발 의도, 주변 차량·차선·충돌 위험, 운전 실력은 확정하지 않는다. 측정 노이즈/지연·주기별 보정, 지속 시간 검증·필터, 실제 차량/에뮬레이터 재현은 남은 과제다. 이번 코드는 차량 안전 경로와 연결되어 있지 않다.
