# B02 Android 코칭 판단·발화 결과 코어

기준: 2026-11-14 KST / Codex 2
작업: feature/b02-coaching-policy-core
최초 기준 origin/dev: 1acc54036c5660ff05d572dd904d250762f0dad5
통합 전 반영 dev: 77af260015981305e7b24891bd76afa29a8e961c
전용 Worktree: Chiilkkabwa-Codex2-B02-Coach
**순수 Kotlin 판단·Mock 시험만 구현했다. Android 실제 주행 음성 자동 재생은 연결하지 않았다.**

## 1. 구조와 선택

| 신규 클래스/파일 | 역할 |
|---|---|
| CoachingEvents.kt | 후보·근거·세션·출처·결과·콜백 계약, A02 결과의 명시적 변환 |
| CoachingPolicy.kt | 빈도·창·신선도·메모리 제한과 중립적 한국어 대사, 길이 추정 |
| CoachingDecisionEngine.kt | 세션별 후보 필터/선택, 완료·실패·중단·중복 관리 |
| CoachingDecisionEngineTest.kt | 31개 JVM 시험. A02 Mock 결과 변환과 기존 계약 호환 포함 |

기존 ParkedVoicePreview와 AndroidPreviewAudio를 확장하는 대신 독립 코어를 선택했다. 미리듣기 회귀와 실제 재생 활성화 위험을 줄이지만 후속 오디오 어댑터 연결이 필요하다. 새 TTS·Speaker·센서 감지기를 만들지 않았고 기존 미리듣기 동작·미리듣기 문장은 그대로다.

기존 Goals 문자열, SignalSource, SignalStatus, A02 SpeedChangeEvent/SpeedChangeEvidence를 재사용했다. A02/C01의 공동 모델을 변경하지 않았다. Web 코드, 차량 소스, Signals, SpeedEventEngine, 저장소, UI/ViewModel, Manifest, Gradle 설정은 무변경이다. A03 로컬 문서는 다른 Worktree에 보존했다.

## 2. Web 정책과 대응

| 기존 Web VoiceGuide | B02 Kotlin |
|---|---|
| 목표 1개 필터 | 세션 goal과 후보 생성 시 goal 모두 일치해야 함. 기존 Goals 목록만 허용 |
| 최소 45초 | 정상 완료된 발화의 offeredAtMs 기준 45,000ms. 기본보다 짧게 설정 불가 |
| 주행당 최대5회 | 세션당 SPOKEN 최대5회. 설정은 더 작게만 가능 |
| 동일 코드 최대2회 | SPOKEN 기준2회. 실패는 횟수/간격을 소비하지 않음 |
| 0.8초 비교 창 | 첫 적격 후보 접수 시각부터800ms, 적격성 재검사 후 상대 우선순위 비교 |
| 높은 비적격 항목이 다른 후보를 막지 않음 | 목표·출처·상태·근거·허용·빈도 검사를 통과한 항목만 정렬 |
| 멘토 기본off | voiceEnabled=null이면 SOLO on/MENTOR off. 두 경우 모두 별도 runtime 차단 적용 |
| 90초 무지적 칭찬 | 최신 유효 Mock 속도10km/h 이상 + 주행 조건 명시 확인 +90초 +주행당1회 |
| committed/dropped | OFFERED와 terminal 결과 분리. 정상 완료 callback만 SPOKEN |
| late callback 폐기 | 동일 offer 인스턴스만 허용. 종료된 offer·다른 엔진 offer·중복 완료 무시 |
| 글자 길이 추정 | 한글 음절1/그 외 비공백0.4, 초당6.5 기준2.5초 이하 |

의도적으로 제외한 부분:

- Web SignalWatcher, 브라우저 Speaker, 시뮬레이터의 위험·차선·충돌·교차로·브레이크 조작 추론은 포팅하지 않는다.
- A02 RAPID_SPEED_INCREASE/DECREASE를 HARSH_START/HARSH_BRAKE로 바꾸지 않는다. 우선순위 숫자35/85는 선택 순서만 뜻하며 위험도나 제동 판정이 아니다.
- 세 대사만 제공한다: “속도가 빠르게 늘었어요.” / “속도가 빠르게 줄었어요.” / “모의 주행을 이어가고 있어요.”
- Web의 칭찬은 부드러운 흐름이라는 표현이지만, Android 코어는 관측 공백을 안전 운전으로 단정하지 않도록 Mock 진행 문구로 제한했다. 위험이 없었다는 인증이 아니다.
- 접수와 선택 시점에 **모두** 빈도·허용 검사를 한다. 접수 당시45초가 지나지 않은 이벤트를 나중에 적격으로 되살리지 않는 보수적 차이가 있다.
- force flush는 제공하지 않는다. 창이 지난 뒤 새 이벤트를 넣으려면 기존 창을 flush해야 하며, 그렇지 않으면 새 이벤트는 WINDOW_ELAPSED로 소비한다.
- 실제 Android TTS 길이, 오디오 포커스·Activity/서비스 주행 유지, 기본 내비 양보 실측은 NOT_VERIFIED다.

## 3. 입력 계약

CoachingSession:
- sessionId, 기존 goal 문자열, SOLO/MENTOR, nullable voiceEnabled.
- runtime 기본 DISABLED.
- MOCK_REHEARSAL과 parkedMockAllowed=true를 **명시적으로** 설정한 순수 시험만 후보 제공 가능.
- parkedMockAllowed는 기존 정차 Mock 게이트 결과를 소비하기 위한 계약이다. 엔진이 차량 상태나 정차를 직접 검증하지 않는다. 속도0으로 대신 계산하지 않는다.

CoachingCandidate:
- 원본 eventId, sessionId, CoachingCode.
- occurredAtMs: 세션 내 단조 ms. 모든 nowMs 및 A02 시계와 같은 기준.
- evidence.value와 명시적 unit(m/s² 또는 km/h). A02 변환은 원본 SpeedChangeEvidence도 보존.
- SignalStatus, SignalSource(MOCK/EMULATOR/VEHICLE).
- 생성 당시 사용자가 선택한 goal, 후보의 voiceAllowed.

지원 코드는 RAPID_SPEED_INCREASE, RAPID_SPEED_DECREASE, STEADY 3개다. 다른 위험 코드를 입력하는 공개 enum은 제공하지 않는다. 모르는 문자열을 이 세 코드로 대체하는 어댑터를 만들면 안 된다.

검사:
- 세션 불일치, 빈 ID, 소비된 ID, 세션 시작 이전·미래·역전 시각, 최대 나이2,000ms 초과, OK 아닌 신호는 차단한다.
- 단위 불일치, NaN/무한대, 증가/감소 부호 불일치, 극단적인 유한값도 차단한다.
- 가속도 절댓값15m/s², 칭찬 속도 최대360km/h는 A02 기본값에 맞춘 **보수적 개발 입력 범위**다. 실차 안전·법규·노이즈 보정 기준으로 인증된 값이 아니다. 사용자 정의 A02 범위를 넓히더라도 B02는 이 범위 밖을 거부한다.
- A02 원본 근거가 있을 때 두 표본의 출처·상태 및 현재 시각/가속도와 후보 일치를 확인한다. 코어는 물리 센서 신뢰성 자체를 인증하지 않는다.
- 모든 비칭찬 새 후보는 지적 없는 시간의 기준을 다시 잡는다. 불명 신호가 들어왔다고 칭찬을 앞당기지 않는다.
- 목표가 출발·정지/그냥 몸풀기인 경우만 속도 변화 대사를 제공한다. STEADY는 유효한 기존 목표에 열리지만 나머지 조건도 필요하다.
- MOCK 외 출처는 기록에 그대로 보존하고 RUNTIME_BLOCKED. live 데이터를 Mock으로 재표시하는 변환 없음.

## 4. 결정·결과 계약

단일 실행 문맥에서 세션당 엔진 하나를 소유한다. 순수 코어는 타이머·오디오·IO를 소유하지 않는다.

1. enqueue(candidate, nowMs): null은 비교 창에 접수됐다는 뜻이다. 발화나 성공이 아니다. 거부 시 사유가 있는 CoachingRecord 반환.
2. flush(nowMs): 800ms가 되면 모든 후보의 적격성을 다시 검사하고 우선순위 내림차순, 관측 시각 오름차순, 같은 시각이면 접수 순으로 하나를 고른다. 다른 후보는 LOWER_PRIORITY로 SKIPPED.
3. 반환된 CoachingOffer에는 원본 후보·line·estimatedSeconds·offeredAtMs가 있다. 이때 상태는 OFFERED.
4. complete(그 offer 인스턴스, PlaybackOutcome, nowMs)로 실제 어댑터 결과를 전달한다.
5. 정상 완료 COMPLETED만 SPOKEN이며 카운터와 didSpeak(eventId)에 반영한다.
6. FOCUS_DENIED→BLOCKED, FOCUS_LOST/APP_STOPPED/TIMEOUT→INTERRUPTED, TTS_FAILED→SKIPPED. 실패 ID도 소비된 채 유지하여 재접수·늦은 성공 callback으로 소급 재생할 수 없다.
7. 완료 대기4,000ms는 개발값이다. flush/enqueue/complete 호출에서 만료를 검사한다. 후속 소유자는 이 시각에 poll/stop을 호출해야 하며 이 코어가 실시간 타이머를 자동 실행한다고 가정하면 안 된다.
8. stop(nowMs)은 허용 취소·세션 종료 시 대기/큐를 폐기하고 영구 차단한다. 소유자가 실제 음성을 stop하고 포커스도 반환해야 한다. 코어만 멈추면 기기 소리가 자동으로 멈추는 구조가 아니다.
9. 호출 시계가 뒤로 가면 엔진은 fail-closed로 영구 차단한다. 정상 재생 세션으로 되살리지 않는다.

CoachingOffer는 callback 상관관계 토큰이므로 eventId만으로 성공을 보고하지 않는다. 이전/다른 엔진의 offer는 새 active offer를 완료할 수 없다. **COMPLETED를 enqueue/재생 요청 반환/AudioFocus 승인에서 호출하면 계약 위반**이다. 실제 어댑터에서는 해당 utterance의 정상 완료 callback에만 연결해야 한다. 이번 테스트의 합성 완료는 실재생 증거가 아니다.

세션 재사용 주의: 엔진 상태는 메모리뿐이다. 동일 논리 주행에서 새 엔진을 만들면 횟수가 초기화된다. 후속 앱 소유자는 동일 주행에서 엔진을 재생성해 제한을 우회하지 않아야 한다. 영구 stop 이후에는 기존 세션의 자동 주행 재개를 지원하지 않는다. 프로세스 재생성·복귀 시 횟수 복원/보수적 차단 정책은 실제 연결 전에 검증할 과제다.

## 5. A02 Mock 연결 예시

실제 구독을 연결하는 코드는 아니다. JVM 테스트 a02AdapterPreservesIdentityEvidenceUnitsAndDoesNotInferBraking에서 아래 흐름을 시험한다.

~~~kotlin
// SpeedEventEngine에 명시적으로 MOCK/OK 속도 표본을 공급한 시험 결과.
val candidate = CoachingCandidate.fromSpeedEvent(
    event = speedChangeEvent,  // A02 원본 SpeedChangeEvent
    goal = "출발·정지",
    voiceAllowed = true,
)
val engine = CoachingDecisionEngine(
    CoachingSession(
        sessionId = candidate.sessionId,
        goal = "출발·정지",
        runtime = CoachingRuntime.MOCK_REHEARSAL,
        parkedMockAllowed = true, // 시험 전제, 차량 신호 대체 아님
    ),
    startedAtMs = 0,
)
engine.enqueue(candidate, nowMs = candidate.occurredAtMs)
val offer = engine.flush(candidate.occurredAtMs + 800)
// offer는 문장 제안일 뿐이다. 여기에서 실제 TTS나 COMPLETED를 자동 호출하지 않는다.
~~~

변환은 eventId/sessionId/occurredAtMs/source/원본 evidence/가속도 단위를 보존한다. 속도 감소는 관측된 속도 감소로만 표현하며 페달 조작·충돌 원인을 추정하지 않는다. VEHICLE/EMULATOR 원본을 변환해도 source가 보존되어 엔진에서 차단된다.

## 6. C01 복기와 최소 공유 계약

공동 저장소/모델을 수정하지 않았다. C01 후속 어댑터에 다음 **연결 계약을 제안**한다.

- (sessionId, eventId)를 키로 A02 사건과 CoachingRecord를 결합한다.
- candidate.source/status/occurredAtMs/evidence.value/unit 및 원본 evidence를 보존한다. signal clock을 벽시계/경과 초로 바꿀 때 기준점을 명시한다.
- OFFERED는 말함 표시가 아니다. 해당 키에 최종 SPOKEN이 확인된 경우만 “말했어요”로 표시한다.
- SKIPPED/BLOCKED/INTERRUPTED는 최종 사유를 표시하며 점수·실력 등급을 만들지 않는다.
- history는 최근512개 진단 레코드의 snapshot이다. 영속 복기 데이터베이스가 아니다. 동일 eventId에 OFFERED와 terminal 기록이 생길 수 있다.
- didSpeak는 성공 ID 최대5개를 별도 보존하므로 history에서 초기 항목이 빠져도 성공 여부가 손실되지 않는다. 하지만 프로세스 종료 시 메모리 상태는 소실된다.
- 소비된 ID는 최대4096개까지 보존한다. 상한 도달 시 신규 후보를 CAPACITY_REACHED로 차단하여, 오래된 ID를 지웠다가 실패 발화를 재생하는 문제를 방지한다.
- C01이 전체 이벤트·상태 전이를 저장하려면 후속 소유자 경계에서 이 기록을 수집해야 한다. 주행 중 저장 정책과 개인정보·프로필 분리는 별도 검증 전 NOT_VERIFIED다.

## 7. 주행 경로 차단과 규정

이번 코어는 앱 실행 경로에서 참조하지 않는다. AndroidPreviewAudio/ParkedVoicePreview/ViewModel/Manifest에 연결하지 않았다. 오디오 포커스만 지키면 주행 중 허용된다는 과거 PRD/웹 주석의 단정은 구현 허용 근거로 삼지 않았다.

공식 근거(정책 범위 참고, 신규 공식 인증 아님):

- [공모전 SDK 범위 O03](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)
- [지역별 주행 제한 P02](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)
- [주행 제한 개발 P03](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [오디오 포커스 P09](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development)
- [API 가용성 P06](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)
- [개인정보 P07](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy)
- 기준 문서: SAFETY_RULES/COMPLIANCE_REGISTER/TEAM_GUIDE, PRD F12/F13, MISSIONS_CONTENT, 기존 구현·검증 대장.

| 규정 | 이번 변경 범위 판정 |
|---|---|
| S01 | PASS 정적: 차량 제어·차량 API·권한 변경 없음 |
| S02 | PASS 범위: 새 SDK/비공개API/외부 STT 없음. 공식 승인·SDK 실차 지원을 새로 인증하지 않음 |
| S03 | PASS JVM: 기본off·정차 Mock 허용 없으면 차단·stop 후 영구 차단. 실제 정차 게이트 실측은 NOT_VERIFIED |
| S04 | PASS 정적: UI/Driving view/주행 터치 변경 없음. 실제 주행 UX 시험 NOT_VERIFIED |
| S05 | NOT_VERIFIED: DO 미선언 주행 중 실행·음성·기록 허용. 실제 경로 미연결/live 입력 BLOCKED |
| S06 | PASS JVM:45초/5회/동일2회/목표/중단 폐기/추정길이. 실제 포커스·내비 양보·2.5초 실측 NOT_VERIFIED |
| S07 | PASS JVM: 원본ID/근거/단위/상태/출처 보존, 불명·이상치 차단, 브레이크·위험 추론 없음 |
| S08 | NOT_APPLICABLE: 미션·조작 안내 추가 없음 |
| S09 | PASS 정적: IO/서버/위치/음성원본/계정/새 저장 없음, 메모리상한. 앱전체 개인정보 인증 아님 |
| S10 | PASS JVM/보고: Mock 합성callback·빌드와 실제 음성·Emulator·실차를 구분 |
| S11/S12 | NOT_APPLICABLE: 외부 자산/제출/배포/릴리스 없음. main 보존 |
| G1/G2 | PASS: 사용자 배정·수정범위·공유계약·근거·미검증 명시 |
| G3 | PASS 코어: 출처·기본차단·실패폐기·개인정보 제한. 플랫폼 어댑터 정책은 NOT_VERIFIED |
| G4 | 아래 JVM/빌드/웹 범위 PASS. Emulator/실차/실음성 NOT_VERIFIED |
| G5 | 차단된 독립 코어만 dev 통합 대상. 관련 실패/충돌/CI 해결 후 병합 |

## 8. 실행 계획과 검증 기록

- [x] 전용 Worktree 생성/최신 dev 확인, A03 보존.
- [x] 필독문서·기존 웹/주차 미리듣기·A02 계약 분석.
- [x] 변경 전 Mock JVM PASS(기존79개).
- [x] 테스트 우선: 타입 미구현 컴파일 실패 확인 → 구현 →27개 신규 테스트 PASS.
- [x] 자체 리뷰: 극단적인 유한 근거값 누락을 추가 회귀로 재현(31개 중1개 실패) → 원인 확인 → 범위 검사 보강 →31개 PASS.
- [x] 메모리상한·이전 완료·동일코드 상한·창/나이 경계도 시험.
- [x] 필수 Android/웹 회귀와 보호 파일 diff 확인.
- [ ] 최신 dev 반영/재검증/feature push/PR/CI/dev 병합·병합 후 CI. 최종 결과는 검증 로그와 PR 이력에 기록.

Ruling: 과도한 데이터모델·새 오디오 엔진 대신 순수3파일, 기존 타입 재사용 — 기존 미리듣기와 차량팀 충돌 방지 — 후속 연결이 필요함.
Ruling: 임계 가속도15m/s²·속도360km/h 유한 범위 추가 — 비정상 유한값이 후보가 되는 실패 재현 — 더 넓은 custom A02 입력은 보수적으로 거부.
Ruling: 별도 에이전트 사용 금지인 이 대화 조건에 따라 전체 변경 자체 리뷰 — 독립 동료 리뷰로 표현하지 않음.

환경: Windows PowerShell, Temurin17.0.20, 기존 Android SDK35/Gradle8.10.2, Node 로컬 환경. 기존 Gradle 캐시 사용(--offline). A01의 실행 중 에뮬레이터에는 접근하지 않았다.

| 시험 | 실제 명령/결과 |
|---|---|
| Android mock JVM | .\gradlew.bat --offline :app:testMockDebugUnitTest — **110 tests,0 failures,0 errors** |
| Android vehicle JVM | .\gradlew.bat --offline :app:testVehicleDebugUnitTest — **110 tests,0 failures,0 errors** |
| 기존 ParkedVoicePreview | 양 플레이버 전체 suite에 포함, 기존4개 PASS |
| B02 신규 | 양 플레이버 CoachingDecisionEngineTest **31개 PASS** |
| 양 debug 빌드 | :app:assembleMockDebug :app:assembleVehicleDebug — **BUILD SUCCESSFUL** |
| Web 회귀/음성 | node --test web/test/*.test.mjs — **109/109 PASS** |
| 보호 파일 | 기준 dev 대비 차량·Signals·A02·저장소·UI·Manifest·Gradle·web diff0 |
| Android/Pleos 실음성·Emulator·실차 | 미실행 **NOT_VERIFIED**. 합성callback은 실제 완료 증거가 아님 |

기존 AAOS deprecated API 경고가 변경 전과 빌드에 존재한다. 이번 코어의 경고 해결을 위해 보호 파일을 수정하지 않았다.

## 9. 남은 과제

실제 플랫폼 DO/카테고리/주행 수명·음성 허용, 정차 확인·허용 취소 전달, 정상utterance callback·포커스·watchdog 연결, 실제2.5초 측정·엔진별 음색, 프로세스 재생성·논리 주행 횟수 보존, C01 내구 저장·프로필 격리를 검증해야 한다. 이 과제를 해결하기 전 실제 주행 자동 음성을 활성화하지 않는다. 이번 결과는 **검증 가능한 판단 기반**이며 A02/C01 실제 공동 데이터 흐름 완료가 아니다.
