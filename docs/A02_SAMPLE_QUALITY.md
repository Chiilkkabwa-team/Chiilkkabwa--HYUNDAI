# A02 표본 품질 Shadow 검증 — 2026-11-14

기준 `origin/dev=2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8`, 브랜치 `fix/a02-speed-sample-quality`, 전용 worktree `chiilkkabwa-codex1-a02-quality/a01-pleos`.
기존 PR #27의 AAOS 수신·엔진은 유지한다. **새 계층은 EXPERIMENTAL 독립 비교 도구이며 앱 실행 경로에 연결하지 않았다.** 실제 급가감속 정확도와 실차 오탐률은 NOT_VERIFIED다.

## 문제 재현과 실측의 한계

[원본 증거 설명](evidence/a02-2026-11-14/README.md)의 Pleos Emulator 2.1.0/API34 기록만 오프라인 재사용했다. 이번 작업에서 새 Emulator/실차 운전 시험은 하지 않았다.
`increase.log`의 Float 원본 `0.8333334 → 1.1111112m/s`는 엔진 입력에서 각각 `0.8333333730697632 → 1.1111111640930176` Double로 확장된다. propertyNs `699801352100 → 699902045600`의 간격은 100693500ns다. 기존 엔진은 ms로 내린 `699801 → 699902`, 즉 101ms를 사용하여 약 **2.750275m/s²**와 `RAPID_SPEED_INCREASE@699902ms`를 재현한다. 양자화 단계에 민감한 계산임을 보여 주며, 운전의 참값이 급출발이었는지는 알 수 없다.

로그에는 가속도 참값·정답 레이블·원래 processing elapsed ns와 발췌 이전 엔진 상태가 없다. 리플레이는 **receiptNs를 분석 now로 사용한 cold excerpt**다. 소비 결과를 입력으로 역산하거나 과거 cooldown을 임의 주입하지 않는다. 원래 `consumed`와 재계산은 별도 파일에 보관한다. 감소 발췌의 기록된 증가 이벤트는 798801ms이고 cold replay는 798204ms다. 발췌 이전 상태가 없으므로 이 차이를 엔진 회귀나 새로운 정답으로 단정하지 않는다. 감소 이벤트는 양쪽 799168ms다.

## 독립 후보 방식

`SpeedQualityShadow`가 변경 없는 `SpeedEventBridge`를 소유해 기존 결과를 그대로 반환하고, 별도 `ShadowSpeedCandidate`를 산출한다. 기존 `SpeedChangeEvent`의 ID/출처/세션/시각/evidence와 공개 계약 변경은 없다. 후보에는 원본 관측값 목록, 모드, 양자화 가정, 창 가속도와 별도 `quality-shadow` ID를 보존한다. **B/C 음성·복기·저장소나 runtime 호출자는 없다.**

비교 모드:

1. `WINDOW_ONLY`: 최신 표본에서 최소 600ms와 최소 5표본을 함께 보존하는 가장 가까운 시작 표본을 택하고, 창 양 끝 평균 가속도를 기존 임계값과 비교한다. 정규 200ms 간격에서는 800ms가 필요하다.
2. `SUSTAINED_QUANTIZATION`: 같은 창 전체와 시간상 중간 표본으로 나눈 두 구간 모두에서 `(방향을 적용한 Δv − 가정한 양자화 폭) / Δt`가 기존 임계값 이상이어야 후보로 인정한다. 양 구간은 각각 200ms 이상이다. 기본 가정 폭은 `1/3.6m/s`다.

증가 `2.5m/s²`, 감소 `12/3.6m/s²`는 그대로다. 최소 5개 표본, 창 600–1000ms, 개별 간격 최대 300ms, 보유 최대 32개다. 100–200ms 기록의 여유를 둔 **실험 설정**이며 센서 사양/인증값이 아니다. 창은 exact elapsed ns, 기존 비교 엔진은 원래 ms를 쓴다. 임계값 주변 부동소수점 차이에 특별 보정을 추가하지 않았으므로 후보 경계 역시 실험적이다.

상태/유효성 거절(짧은 간격 포함), 큰 공백, 출처 변경은 품질 창·episode를 끊는다. 같은 세션의 중단된 episode가 cooldown을 우회하지 않도록 출처/유형별 6000ms 기록은 유지한다. 지속 episode 중복은 억제한다. 세션 시작은 명시적 `start`, 종료 후/다른 세션 callback은 무시한다. raw 음수를 절댓값으로 바꾸지 않는다. 권한 거부·STALE·UNKNOWN·DISCONNECTED와 미래/역전/동일 시각은 기존 bridge 검증을 따른다. 기존 bridge와의 결과 동일성도 별도 테스트한다.

1km/h 폭은 관측 계단에 대한 **민감도 가정**이다. 센서의 최대 오차가 그 이내라는 뜻이 아니며, 위 수식을 실제 가속도의 통계적 하한으로 인증하지 않는다. 강한 변화도 짧거나 임계값 근처이면 놓칠 수 있다. 기본 경로 승격 근거는 부족하다.

## 전후 비교

시각은 원본 elapsed property 시각이며 epoch가 아니다. 정답 레이블이 없는 실측에서는 아래를 후보 수/지연의 비교로만 해석한다.

| 입력 | cold 기존 후보 | 창 평균 후보 | 지속성+양자화 가정 후보 |
|---|---|---|---|
| increase 실측 14표본 | 증가 1 @699902ms | 0 | 0 |
| decrease 실측 15표본 | 증가 1 @798204ms, 감소 1 @799168ms | 감소 1 @799404455800ns | 감소 1 @799779340800ns |
| 일정 참값 10m/s 가정, 관측은 0/+1km/h 교대 | 증가 1 @100ms | 0 | 0 |
| +1m/s² 완만 증가, 1km/h 반올림 | 증가 1 @200ms | 0 | 0 |
| −1m/s² 완만 감소, 1km/h 반올림 | 0 | 0 | 0 |
| 지속 +5 / −5m/s², 100ms | 각 1 @100ms | 각 1 @600ms | 각 1 @600ms |
| +5m/s², 100/200ms 교대 | 1 @300ms | 1 @700ms | 1 @700ms |
| +5m/s², 정규 200ms | 1 @200ms | 1 @800ms | 1 @800ms |
| 100ms 동안만 +1m/s 스파이크 | 증가/감소 2 @800/900ms | 0 | 0 |
| 정속 20m/s | 0 | 0 | 0 |
| +5m/s² 중 300ms callback 1개 누락 | 1 @100ms | 1 @600ms | 1 @600ms |
| 지속 +3m/s² — 누락 한계 | 1 @100ms | 1 @600ms | **0 (놓침)** |

실측 감소는 약 236ms/611ms 늦어진다. 합성 ±5m/s²는 유지되지만 약 500ms의 추가 지연이 있다. +3m/s² 누락은 양자화·지속성 보수화의 비용이다. 후보 감소가 실제 오탐 감소율이라는 결론은 내리지 않는다.

[자동 생성 상세 보고서/CSV](evidence/a02-quality-2026-11-14/README.md)에 각 관측 시각/원본 Float 문자열·확장값/상태/출처/세션/수신 시각, adjacent ns 간격·가속도, 기존 ms 근거, 이벤트·억제·거절·품질 보류 이유를 남겼다. 원본 관측과 consumed는 구분하며 빈 값은 unknown/null이다. 연속 동일 시각에서 가속도를 0으로 채우지 않는다.

## 재현 명령과 시험

Windows PowerShell, Temurin17.0.20.101, Gradle8.10.2, Android SDK35, Node24.18.1. 기존 JUnit4 외 의존성 추가 없음.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/SMPOWER/AppData/Local/Android/Sdk'
$env:GRADLE_USER_HOME='C:/Users/SMPOWER/.gradle'
$env:A02_REPLAY_REPORT_DIR=Join-Path (Get-Location) 'docs/evidence/a02-quality-2026-11-14'
.\gradlew.bat --offline :app:testMockDebugUnitTest --tests '*SpeedLogReplayTest' --tests '*SpeedQualityShadowTest'
# 보고서를 다시 만들 때 UP-TO-DATE이면 --rerun-tasks 추가. 환경변수 미설정 시 파일 출력 없음.
.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug
node --test web/test/*.test.mjs
git diff --check
```

기준선 각164개 → 신규16개 포함 **각180개 PASS**, 두 debug 빌드 PASS, 웹126개 PASS. 타입/파서 부재 compile RED 후 구현 GREEN. 초기 합성 감소 기대조건 오류는 실제 감소 임계값에 맞춰 수정했다(임계값 변경 없음). 로그 timestamp는 파일의 실제 `21:02:36.909`를 대조했다. 합성 데이터에만 참값 가정이 있다.

독립 리뷰와 추가 테스트에서 200ms 정규 간격의 창을 600ms로 줄이면 4개만 남아 영구 WARMING_UP이 되는 결함을 재현했다(1 FAIL). 최소 5개를 보존하도록 수정해 800ms의 새 근거로 검출한다. 기존 비교 결과는 동일하다. 메모리 테스트는 정상 입력의 상한 준수를 확인하며, 32개 절단 분기 자체의 실행 증거로 주장하지 않는다(기존 최소 50ms 검증과 시간 창으로 더 작게 제한됨).

추가 회귀: 300ms 초과 공백 뒤 600ms 새 coverage, duplicate/역전 시계, receipt 역전, 누락 timestamp, 미래 ns, NaN, 음수 원본, 권한 철회/invalid 상태, 출처/세션 변경·stop 뒤 늦은 callback, cooldown 우회 차단, 원본 evidence 보존·메모리 상한. 기존 AAOS source/session/권한·활성화 gate 테스트도 전체 실행했다. MainActivity/Manifest/Gradle/A01/A03/B/C/Web/SafetyGate/기존 엔진·bridge/A02_SPEED_EVENTS.md는 변경하지 않는다. 기본 OFF, debug EMULATOR opt-in, 실제 차량/release 차단 유지.

## 규정·통합 게이트

범위는 **오프라인 분석 계층**이다. 아래 PASS는 이 diff/단위시험에 한정하며 전체 앱의 공식 승인이나 실차 검증을 뜻하지 않는다. 근거는 [규정 대장](COMPLIANCE_REGISTER.md) 및 [안전 원칙](SAFETY_RULES.md)의 기존 원문 색인을 유지한다. 새로운 SDK·정책 허용 판단은 하지 않았다.

| ID | 상태 | 범위·증거·근거 |
|---|---|---|
| S01 | PASS | 새 SDK/제어·권한 0, 순수 Kotlin 분석만. 플랫폼 [P04](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture) |
| S02 | PASS (변경 범위) | SDK/LLM/STT/의존성 추가 0. 공모전 [O03](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)의 기존 허용 범위를 확장하지 않음 |
| S03 | NOT_APPLICABLE | 정차 학습·SafetyGate 변경 없음. 속도0으로 주차 추론 안 함. 팀 정책 및 플랫폼 [P02](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) |
| S04 | NOT_APPLICABLE | 화면/터치/주행 UI 없음. 플랫폼 [P01](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) |
| S05 | NOT_VERIFIED | 주행 중 수집/서비스 허용·수명 정책은 새 검증 없음. 앱 경로 연결 0. 플랫폼 P02/P03, 기존 차단 유지 |
| S06 | NOT_APPLICABLE | 음성 연결/출력 없음. 플랫폼 [P09](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) |
| S07 | PASS | 관측/기록소비/cold재계산/합성참값을 구분, 근거·단위·시각 유지. 실제 정확도 NOT_VERIFIED. 플랫폼 [P06](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) 및 팀 정책 |
| S08 | NOT_APPLICABLE | 워밍업·벨트·페달 미션 변경 없음. 팀 정책 |
| S09 | PASS (변경 범위) | 기존 가상 시험 로그만 로컬 분석, 업로드/실차 식별자/새 수집/저장 0. 플랫폼 [P07](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy), P08 |
| S10 | PASS | 빌드·오프라인 기록 재현·합성·실차 등급 분리, Shadow 후보 타입 별도. 팀 정책 |
| S11 | NOT_APPLICABLE | 외부 콘텐츠·의존성·렌더링 추가 0. 창 메모리 상한 단위시험은 PASS, 실환경 성능 측정은 NOT_VERIFIED |
| S12 | PASS (변경 범위) | main/기존 작업 보존, 제출·APK 업로드·공개 전환 없음. 제출/릴리스 적합성 NOT_APPLICABLE. 공모전 [O01](https://appdevchallenge.auto-hmg.io/guide/submission) |

G1 PASS: A02 배정·파일·수정금지 범위 명시. G2 PASS: 기존 근거/적용범위와 미확인 구분(원문 새 승인 주장 없음). G3 PASS: 독립 Shadow·실행 연결 0·새 권한 0. G4 PASS: JVM/빌드/웹/오프라인 재현만; 새 Emulator·실차·정답 정확도 NOT_VERIFIED. G5: 검토·원격 CI 및 통합 결과는 VERIFICATION_LOG와 PR 이력에 기록한다. 안전 FAIL 상태로 병합하지 않는다.

후속: 정답 레이블·실제 속도 분해능/오차·샘플링 지터·운전 맥락을 확보하고 지연/재현율을 평가해야 한다. 주행 정책·생명주기 검증도 별도다. B/C 연결 전에 Shadow를 실제 운전 사실로 기록하거나 즉시 코칭 트리거로 사용하는 것을 금지한다. 기본 승격은 이번 범위에서 보류한다.

최신 통합 재검증(2026-11-15): A01 PR#30의 dev91413da9e581c0fd4d18adc056221c63605079c7를 반영했다. 공통 문서의 양쪽 기록을 모두 보존했고, 제품코드 충돌은 없었다. 최신 기준178 + A02 신규16 = 양JVM **194/194 PASS**, 두debug빌드 PASS(5초), 웹126/126 PASS, diff check PASS. A02외 제품코드는 최신dev와 동일하다. [PR #31](https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/31)의 최종 HEAD·Actions·병합 SHA가 원격 결과의 정본이다. G5 로컬 통합 PASS, 원격 CI는 실제 성공 확인 후 병합한다.
