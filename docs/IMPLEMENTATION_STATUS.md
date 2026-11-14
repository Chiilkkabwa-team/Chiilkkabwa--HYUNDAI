# 구현 상태 대장

Android 구현 정적 점검일: 2026-11-11 KST (당시 기준 main: de612636cc2c13f6d4397626fdf24cf8a70cb2e6). **웹 확장과 브랜치 병합 상태 확인: 2026-11-13, dev 9ca4316507481e3e56e50815e90d237da2fe1c0f / main c4c15686a30e788143ccc88d8ebaa52500d3187b.** 새 분업/결함 정리: [3인 기능 분업 v3](THREE_PERSON_FEATURE_PLAN_V3.md).
요구사항 원본: 사용자가 제공한 **공식접수기획서 최종 DOCX**, 02-3(주요 기능) 및 02-5(개발 계획).

**이 표는 정적 코드 점검 결과이다.** 이 작성 시점에는 Android Studio, CI, Pleos Emulator, 실차를 새로 실행해 검증한 것이 아니다.

| 기능 | Android main 코드 | 웹 모의 시연 | 실제 에뮬레이터/차량 검증 |
|---|---|---|---|
| 주차 안전 게이트 | SafetyGate 및 AAOS 소스 코드 존재. 신호 최신성/복기 입력 정책 보완 필요 | 모의 구현 | 미검증 |
| 목표·혼자/멘토·약속 | ViewModel/Compose 구현 | 모의 구현 | 미검증 |
| 벨트/브레이크/방향지시등 워밍업 | Pleos 읽기 어댑터 연결·기본 off | 모의 구현 | 초기 벨트/브레이크/방향지시등 수신 확인, 전체 미션 미검증 |
| 가상 주차/조건부 페달 | Kotlin 로직·화면 존재, 실신호는 조건부 | 모의 구현 | 미검증 |
| AAOS PERF_VEHICLE_SPEED | 기본OFF 속도 관측/권한/독립 구독·엔진 브리지 | 가상 속도 | Pleos2.1.0 전진 콜백/소비 확인, 후진 음수·실차 미검증 |
| 속도 기반 주행 이벤트 | 기존 코어+A02 입력 브리지, UI/음성/저장소 미연결 | 가상 이벤트 | Emulator 증가/감소 이벤트 관측, 양자화/임계값 보정·주행정책 미검증 |
| 주행 중 음성 및 AudioFocus | Android 미구현 | Web Speech 구현 | 플랫폼 허용·실환경 미검증 |
| 주차 후 시계열 이벤트 리플레이 | Android 미구현 | 웹 모의 타임라인 | 미검증 |
| 복기 요약·배지·다음 목표 | Kotlin 코드 일부 구현 | 모의 구현 | 반복 세션 미검증 |
| NaviHelper 경로 전달 | Android 미구현 | 제품 계획 | 미검증 |
| 프로필 선택/삭제 UX | Store API 존재, 화면 구현 불완전 | 별도 | 미검증 |
| 암호화 Room 저장 | 미구현. 현재 SharedPreferences JSON | 브라우저 localStorage | 미검증 |
| Pleos Vehicle 신호 구독 | Seat 벨트·Brake·TurnSignal 구현, 나머지 미연동 | 해당 없음 | API34 Pleos2.1.0에서 일부 실수신 확인, 실차 미검증 |

### 2026-11-13 확인: 웹 모의 추가 (Claude 브랜치 → PR #7 dev, PR #8 main 병합 완료)
11/12 회의 + 3인 기능 배정안 v2의 웹 부분. **전부 웹 모의**이고 Android·에뮬레이터·실차는 손대지 않았다. **브랜치 병합은 기능 완료를 뜻하지 않는다.** 웹 64/64 단위 테스트 PASS 보고와 별개로 브레이크 슬라이더, 벨트 미션, 복기 게이트, 음성 우선순위/이벤트 연결 오류가 확인되었으므로 [분업안 v3](THREE_PERSON_FEATURE_PLAN_V3.md)에 수정 과제로 기록했다.

| 배정안 ID | 기능 | 웹 모의 | Android | 에뮬레이터/실차 |
|---|---|---|---|---|
| B01 | 겹친 상황 우선순위·칭찬·회전 주의 대사, 평가·강습 리포트 완료 발화의 사건 ID 연결 | 구현 + 단위 테스트 | 이벤트 음성 미구현 | 미검증(S05) |
| A04 | 가상 교차로 좌·우회전(판정: 깜빡이·차로·속도) | 구현 + 단위 테스트 | 해당 없음 | 해당 없음 |
| C01 | 주차 후 속도 시간표(사건 점·브레이크 띠·말한 표시). **E3 수정(11/13): 복기 중 주차 확인이 깨지면 복기·장면 재현 즉시 잠금, 재주차 시 이어서** | 구현 + 단위 테스트 | 게이트 잠금만 수정+단위 테스트, 시계열 미구현 | 미검증 |
| C03 | 장면 재현 작은 창(시뮬레이션 재현 표기) | 구현 + 단위 테스트 | 미구현 | 미검증 |
| A05 | 콘솔 핸들 그림·누른 만큼 깊어지는 페달·WASD·벨트 표시 | 구현(브라우저 조작 확인) | 해당 없음(웹 콘솔 전용) | 해당 없음 |
| B04 | 미션 도움말(O/X)·운전 노트, 주차 확인 해제 시 닫기, 벨트 신호 미지원 안내 | 구현 + 단위 테스트 | 미구현 | 해당 없음 |
| C04 | 알림 남은 시간 테두리·온보딩 버전. **11/14: 온보딩 문구 개편, 소개·사용법 주행 중 열림(가상 주행 정지), 앱 안 창 주행 잠금 이유, 하단 바 데모 미지원 안내.** 내비식 정보 배치 11/14 처리, 온보딩 코치 마크 추가 | 구현 | 미구현 | 해당 없음 |
| C05 | 서비스 소개(About Us)·구현 상태 표. **11/14: 내 기록에 다음 목표·최근 7일 연습한 날(연속 기록 없음)** | 구현 + 단위 테스트 | 미구현 | 해당 없음 |
| B03 | 한국어 음성 선택·짧은 안내 문구 | 로컬 한국어 음성 우선, 회전·칭찬 문구 조정. 실제 2.5초 실측은 미검증 | — | — |
| A06 | 3D 차량 | **안 함** — 자산 라이선스·성능 측정 먼저(S11) | — | — |
| B02 | Android 정차 음성 미리듣기 | 해당 없음 | `mockDebug` 주차 준비 화면의 오프라인 한국어 TTS·오디오 포커스 | Pleos TTS·차량 포커스·주행 재생 미검증(S05) |
| A01·A02·A03 | Pleos 신호·속도 이벤트·NaviHelper | 위 기능별 항목 참고 | 위 기능별 항목 참고 | 위 기능별 항목 참고 |

주요 코드 근거:
- app/src/main/java/com/chiilkkabwa/app/core/{signal,safety,mission,session}/
- app/src/main/java/com/chiilkkabwa/app/data/vehicle/
- app/src/main/java/com/chiilkkabwa/app/data/store/PracticeStore.kt
- app/src/main/java/com/chiilkkabwa/app/ui/
- web/{app,voice,sim}.js

**과거 기록(재검증 아님)**: 기존 CLAUDE.md와 docs/14에는 2026-11-08 기준 Android mock/vehicle 빌드 및 JVM 테스트 17개 성공 기록이 있다. 이번 작성 시점에 재실행하지 않았다.

작성 원칙: '코드 존재' / '빌드 성공' / '단위 테스트 통과' / '모의 시연' / '에뮬레이터 수신' / '실차 검증'을 각각 별개로 표시한다. 해당 기능의 검증 로그가 없으면 완료로 승격하지 않는다.

### A01 Issue #16 후속 (2026-11-13 / 코드 dd0a5a1)

- `PleosSdkReadClient`에 실제 Vehicle 2.3.2의 Seat 벨트·Brake·TurnSignal 공개 읽기 capability/get/register/unregister를 연결했다. `PleosSignalSession`은 오류/타입/null/타임아웃, 초기 읽기와 이벤트 경쟁, 종료·재시작 후 콜백, 해제 배출을 관리한다. `VehicleSnapshot` 필드·단위·출처 계약과 매니페스트 권한은 변경하지 않았다.
- Activity pause/resume에 구독을 연결하고 repository 중지 시 값을 폐기한다. AAOS callback은 시작 당시 sink를 캡처하여 이전 연결 이벤트를 격리한다. 동기 중지 스냅샷 반영으로 warmup 재개를 보완했다.
- JVM 테스트 mock **44/44**, vehicle **44/44**, 양 debug APK 빌드 및 웹 **79/79** PASS. SDK 클래스/시그니처 확인과 컴파일을 통과한 상태이며 실제 callback 수신 완료를 뜻하지 않는다.
- 기본 비활성화. 명시적 debug opt-in + EMULATOR에서만 시험 가능하고 실차/release는 차단한다. SDK 미응답 상태에는 추가 client 생성을 차단한다. 리소스 회수·실제 생명주기·권한·Pleos Emulator/실차는 **NOT_VERIFIED**. 자세한 근거/재현 명령은 SDK 노트 A01 절과 검증 로그 참고.

### A01 Pleos Emulator 실측 갱신 (2026-11-13 / 코드258e4da)

공식 Pleos2.1.0 이미지 설치·해시 검증과 API34 AVD 부팅/앱 설치를 완료했다. Vehicle SDK2.3.2, 실제 서비스2.3.22 환경에서 **초기 벨트false, 콘솔 브레이크false/true/false, 방향지시등LEFT/RIGHT/OFF → 앱 소비 snapshot**을 실측했다. 카메라 화면에 의한 pause의 unregister/release·값 폐기와 복귀도 관측했다.

단, 콘솔에 belt-buckled 전용 제어가 없어 벨트true 전환은 미검증이며 Parking Brake 경고등 On이 실제 PARKING_BRAKE_ON을 켜지 않아 안전 게이트는 잠긴다. **전체 미션 완료/게이트 해제/실차는 검증되지 않았다.** 장시간 중단 후 검은 캡처도 관측해 GPU·화면 안정성은 미검증이다. 실기에서 발견한 '정의 없는 페달 권한 요청'을 차단하고 opt-in debug EMULATOR 진단을 보완했다. 기본 off/실차/release 차단 유지. 근거: SDK 노트의 최신 A01 절 및 docs/evidence/a01-2026-11-13.

### A02 속도 변화 이벤트 코어 (2026-11-13 / 11d43ab)

- 최신 dev `b47e96d` 기반 `feature/a02-speed-events`에서 독립 Kotlin `SpeedEventEngine`/`SpeedEvents`와 JVM 테스트를 추가했다. 기존 VehicleSnapshot·A01 어댑터·UI·권한·Gradle 의존성은 수정하지 않았다. [A02 입력/출력·판정·연결 계약](A02_SPEED_EVENTS.md).
- 명시적 속도 단위·타임스탬프·출처·상태에서 유효한 표본 쌍의 Δv/Δt를 계산한다. 급격한 속도 증가/감소와 원본 근거를 반환하며, 브레이크 조작·주변 위험을 판단하지 않는다. 중복 구간/쿨다운·히스테리시스·오류/신선성·출처/시간 단절을 처리한다.
- 로컬 mock/vehicle JVM 각각 **78/78**, 두 debug 빌드, 웹 **79/79 PASS**. 원격 push CI에서도 Web unit tests 및 Android tests/both debug flavors SUCCESS 확인. PR #19로 dev 대상 검토 중이며 아직 병합하지 않았다.
- 실제 차량/SDK·Emulator 입력·주행 수집 정책·노이즈/임계값 보정·B/C 연결은 **NOT_VERIFIED**. 앱 실신호 경로는 미연결이며 A01의 실수신 검증을 A02 실행 증거로 사용하지 않는다.

### A04 웹 가상 교차로 보완 (2026-11-13 / def35eb)

- `feature/a04-intersection-turns`, 기반 dev `1140c74`. 기존 2D 교차로 배치를 재사용해 가상 진행과 깜빡이 독립 선택, 접근/진입 분리, 진입 속도·가상 차로·구간/신호 불일치, 중복 억제와 다음 교차로 처리를 보완했다. 모든 교차로 기록과 복기에 방향·시뮬레이션 시각·근거·MOCK 출처를 보존한다.
- 기존 네 교차로 이벤트 계약·음성 우선순위는 유지했다. 새 신호/구간 불일치 및 직진/판정 보류 기록은 [A04 계약·실측](A04_INTERSECTION_TURNS.md)에 정리했다. 웹 외 Kotlin·Manifest·Gradle은 무변경이다.
- 웹 전체 **105/105 PASS**, headless Chrome 평가·강습·연습 자동 시연 회귀 PASS, 1440px/390px 콘솔의 독립 조작·넘침 없음·브라우저 오류0 확인. 시연의 늦은 감속 결함도 수정 후 재검증했다.
- S07/S10 PASS는 **웹 2D Mock 범위**다. 실제 회전 물리·3D·보행자/통행권·실제 차선 위험은 구현/검증하지 않았다. Android/Pleos Emulator/실차 검증으로 표시하지 않는다.

### A01 신뢰성 후속 재검증 (2026-11-13 KST23시 / dev1140c74)

[상세 보고서](A01_RELIABILITY_FOLLOWUP.md). 앱 코드 변경 없음. 공식 콘솔48개 카드·경고등 비교에서 벨트true 및 실제 주차브레이크ON 입력 경로가 없어 전체 워밍업은 계속 BLOCKED/NOT_VERIFIED다. 안전 조건을 완화하지 않았다. Settings에서325.96초 중단 후 같은 PID로 화면·새 신호 복귀는 PASS. Sleep/Display OFF에서 전체 검은 캡처 및 Wake 복구를 관측했지만 기존 장시간 검은 화면 사건의 원인/해결 완료는 NOT_VERIFIED다. 두 JVM 각각78/78, 양debug빌드, 웹79/79 PASS. 기본off·debug EMULATOR opt-in·실차/release 차단 유지. 증거는 `docs/evidence/a01-2026-11-13-reliability/`.



### A02 AAOS 속도 입력 브리지 (2026-11-14 / 코드8166fe4)

최신 dev1acc540 기반 Codex1 전용 worktree의 feature/a02-aaos-speed-bridge. 공개 PERF_VEHICLE_SPEED/CAR_SPEED의 signed Float m/s와 elapsed ns를 보존하는 독립 소스를 기존 SpeedEventEngine에 연결했다. Signal 벽시계 계약·엔진 정책·안전 게이트·UI/음성/저장소/Web은 유지했다. 기본OFF, debug의 명시적 opt-in+확인된 emulator hardware만 가능하며 실차/release는 차단한다.

최종 JVM mock/vehicle 각107/107, 두debug빌드, 웹109/109 PASS. Pleos2.1.0에서 실제 속도 증가·감소/원본 callback 관측·엔진 소비와 두 이벤트 발행, R 카메라 pause의 폐기/새 세션 복귀, 권한 철회의 PERMISSION_DENIED를 확인했다. 기본OFF 재설치의 trace0건도 확인했다. 후진 음수 실제 수신, 권한창 전체 조작·재부여 복구, 실제 급가감속 판정/노이즈·양자화 보정 및 주행 수집 정책·실차는 NOT_VERIFIED다. B/C 실사용 연결은 하지 않았다. [계약·등급·실측](A02_SPEED_BRIDGE.md).
### B02 Android 코칭 판단 코어 (2026-11-14)

- 기준 dev 최초1acc540, 통합 전b758c2b(B01/B04) 반영. feature/b02-coaching-policy-core, 전용 Worktree. [계약·정책·검증·미확인](B02_COACHING_POLICY.md).
- 순수 Kotlin CoachingPolicy/CoachingCandidate·Record/CoachingDecisionEngine 신규. 목표 우선 필터,800ms 비교,45초/5회/동일2회,멘토 기본off,중복·신선도·단위·출처·세션·결과 관리. A02 원본ID·근거를 보존하며 속도 감소를 브레이크·위험으로 추론하지 않는다.
- 기본 DISABLED. 명시적 정차 Mock 시험만 OFFERED 가능하며 EMULATOR/VEHICLE은 출처를 보존해 BLOCKED. 기존 미리듣기·차량 소스·UI·저장소·권한·Gradle·웹 무변경. 실제 주행 자동 음성 미연결.
- JVM mock/vehicle 각각110/110(신규31),두 debug빌드,웹110/110 PASS. SPOKEN은 합성 정상 완료callback 시험 결과이며 실제 TTS 재생 증거가 아니다. 실음성 길이·포커스·Emulator·실차·주행 플랫폼정책은 NOT_VERIFIED(S05). PR/병합/CI는 해당 검증 로그·PR 이력 참조.

A02 통합 후 갱신: B01/B02/B04 dev44bfe68를 반영해 Android 양JVM **138/138**, 양debug빌드, 웹 **110/110 PASS**. A02 외 코드 변경은 최신dev에서 가져온 것이며 A02 PR의 B02·Web diff0이다. 실기/실차 등급과 차단 조건은 A02 전용 문서 그대로 유지한다.

### A05 웹 Mock 조작 반응 보완 (2026-11-14 / 5cb0612)

- `feature/a05-web-control-polish`, 최초 dev44bfe68 / 최신 dev0209187 반영. 기존 E1 페달 수정을 재사용해 입력 채널 소유권·점진 WASD·포인터 취소/늦은 콜백 차단·슬라이더 우선·정보 버튼·가상 기어/정지 요청 상태를 보완했다. [A05 상세·입력 수치·한계](A05_CONTROL_POLISH.md).
- 웹126/126, 390×900/1440×900 실제 조작·온보딩/코치 마크/GNB, 기존 세 모드 자동 시연과 A04 회귀 PASS. 최종 브라우저 오류0. Android/B·C 모듈·저장·SafetyGate·sim.js 계약은 최신dev 대비 무변경이다.
- 전부 웹 2D Mock 결과이며 실제 차량 제어·자동D·실차 운전 평가가 아니다. C02 작업물을 포함하지 않으며 기존 Worktree/AVD는 보존한다. main 변경 없음. 원격 CI/통합은 A05 PR 및 Actions 이력으로 추적한다.

### A03 NaviHelper 독립 SDK 연동 기반 (2026-11-14)

- feature/a03-navihelper-integration / 전용 Chiilkkabwa-Codex2-A03-Navi. 최초dev44bfe68, A02 병렬 통합 dev0209187 반영. [실제 구현·근거·검증 등급](A03_NAVIHELPER_INTEGRATION.md), [기존 조사 사본](A03_NAVIHELPER_FEASIBILITY.md).
- 실제 NaviHelper3.0.5 의존성·공개API/모델,독립 클라이언트/어댑터·엄격한 정차/신선도/출처 게이트·명시적 요청·콜백세대/타임아웃·해제 구현. 경로 함수 Unit반환/검색시작/검색완료/nav상태를 구분하고 안내성공은 자동 확정하지 않는다.
- 기본OFF,debug 명시opt-in+EMULATOR만 허용. 실제클라이언트가 빌드/기기 재확인,release는 항상false. NAVI_ROUTE_SEARCH만 추가. 기존B/C UI·음성·저장소·A02·웹 무변경. 자동요청/안내/취소/위치수집0.
- SDK 실컴파일·양JVM164/164(신규A03 26),양debug빌드,웹110/110 PASS. ON→OFF 증분BuildConfig+getstatic 바이트코드 및 opt-in에도releasefalse 확인.
- 실제 앱 SDK초기화·경로호출·검색callback·기본내비안내는 모두 NOT_VERIFIED. 현재 타작업 일반Android 기기를 사용하지 않았고 Pleos의 실제 정차신호가 준비되지 않아 UI시험진입점·실제호출을 보류했다. 기본비활성 기반만 통합 대상.

A05 A03 통합 재검증: dev9a72969를 dbce435에 반영. 공통문서2곳 충돌은 A02/A03/A05 전부 보존. 웹126/126,양JVM164/164,양debug빌드,390/1440 조작 PASS. A03·Android·Gradle 및 B/C보호diff0. 최종통합/CI는 PR#28 이력 참조.

### A03 Debug 실행 진입점 (2026-11-15)

- feature/a03-debug-emulator-probe, 기준dev2a70830. [전용 검증 문서](A03_EMULATOR_VERIFICATION.md). 기존 A03 어댑터를 사용하는 Debug-only owner,vehicleDebug 비공개 Activity,명시 Android instrumentation runner를 추가했다. 자동초기화/자동요청 없음,실차/release/default차단·strict 게이트 유지.
- 양JVM176/176(신규owner12),두debug·시험APK·release manifest/클래스 제외,웹126/126 PASS. Main/B/C/A01/A02/Web 기능변경0. SDK hook은 고정단계만 관측.
- 실제AVD는 타Codex 점유 및 사용자 요청으로 미사용. 실제 SDK초기화·요청·검색·안내는 모두NOT_VERIFIED. JVM의 브레이크false 차단을 실제 게이트 관측으로 표시하지 않는다. 제품 release 기능은 활성화하지 않았다.
### A02 표본 품질 후속 — 독립 EXPERIMENTAL Shadow (2026-11-14)

- `fix/a02-speed-sample-quality`, 기준dev2a70830. PR#27 수신·기존 엔진/bridge는 그대로 유지하고, bounded600ms창·지속성·1km/h 양자화 민감도 비교를 독립 구현했다. runtime/B·C/UI/권한/Gradle/Web 연결·변경0. [방법·가정·비교·규정](A02_SAMPLE_QUALITY.md), [오프라인 증거](evidence/a02-quality-2026-11-14/README.md).
- 실측 증가 후보1→Shadow0 재현. 실측 감소 후보는 유지되나 지속성 방식 약611ms 지연. 합성±5m/s² 검출 유지, +3m/s²는 놓침을 명시. 정답 없는 실측의 실제 오탐률/정확도·새Emulator/실차 시험은 NOT_VERIFIED. 기본 경로 승격 보류.
- 기준각164→신규15 포함 양JVM179/179,두debug빌드,웹126/126 PASS. 원본 signed Float/ns/source/session·상태와 recorded consumed/cold replay 구별. 기존 기본OFF/debugEMULATORopt-in/실차·release차단 유지. main/기존Worktree·브랜치·A01/A03/A05 기록 보존. 원격통합/CI는 VERIFICATION_LOG 및 PR이력 참조.

A02 Shadow 최종리뷰보완: 정규200ms에서최소5표본(800ms)을보존하도록RED→GREEN수정. 양JVM180/180(신규16),양debug빌드PASS. 기본경로승격없이기존비교결과/차단그대로. 상세는A02_SAMPLE_QUALITY.md와검증로그참조.

### A01 입력 제약 후속 (2026-11-14)

최신dev2a70830 기반 fix/a01-emulator-input-blockers / 전용 A01-Inputs. 필수3미션의 과거/미래 벨트값·중단전관측과 브레이크/깜빡이 부분진행 재사용을 최소 차단. 코드8469119f48c5ed60db9b3d5eb6d721dacf536f80. 양JVM178/178(신규14),양debug빌드,웹126/126 PASS. 실제2.1.0/Service2.3.22/SDK2.3.2 초기false/OFF와 PBfalse 잠금 재확인. 벨트true·실PBON·전체Emulator워밍업은BLOCKED,Mock6미션완료와구분. [공식경로·증거·안전·외부조건](A01_INPUT_BLOCKERS.md). A02/A03/B·C/웹/Manifest/Gradle/SafetyGate변경0. Issue#16열림,main·기존Worktree보존.

A03 Probe 최신 통합: A01 PR#30의dev91413da를c159d15에반영,대장append충돌양쪽보존. 양JVM190/190·양debug·시험APK·release제외·웹126/126 PASS. 실제장치단계NOT_VERIFIED유지. 최종PR/CI는이력참조.
A02 최신통합: A01 PR#30 dev91413da의14개테스트/기존기록을보존해양JVM194/194,양debug빌드,웹126/126 PASS. PR#31(base=dev)의최종HEAD/CI/병합결과참조. A02외제품코드는최신dev와동일하다.

A03 Probe PR#32 최종동기화: dev8993d25(A02 PR#31)와공통기록전부보존. 양JVM206/206·양debug·시험APK·release제외·웹126/126 PASS. 실제장치NOT_VERIFIED유지. 최종PR/CI는#32이력참조.

### A01 선택 센서 공개 SDK 연결 (2026-11-15)

기준dev01eff12 / feature/a01-optional-vehicle-signals / 전용 OptionalSignals. 코드19e4ec3e8e4cad17012984a10b7c2124dc6efb3a. Wiper실제상태·LowBeam실제상태·앞오른쪽착석·현재Trip거리(m→km)/시간(분→Long초)을 기존세션에연결. Steering은public전륜각≠기존핸들회전각,CarInfo는public치수없어미연결폴백유지. 차량미지원확정과구분. 선택Toggle/Center 중단·시각·출처회귀보완. 양JVM226/226(신규20),양Debug/opt-in컴파일·release차단·웹126/126 PASS. **AVD인계전 미접근,실수신·실미션 NOT_VERIFIED**. [정확한API/권한/단위/안전/한계](A01_OPTIONAL_SIGNALS.md). A02/A03/B·C/웹/게이트/Manifest/Gradle과기존Worktree/main보존.
