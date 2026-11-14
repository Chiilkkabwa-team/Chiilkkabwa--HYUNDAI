# A05 웹 2D Mock 운전 조작 개선

2026-11-14 KST. 최초 기준 `44bfe68c7aec5dd4cfe97cd20b1fa839c8b9ec05`, 브랜치 `feature/a05-web-control-polish`, 전용 Worktree `Chiilkkabwa-Codex3-A05-Controls`.
시험 코드 `5cb0612`, 최신 dev `02091872cf77650051318aa844517c0f3c033d19` 반영 후 `c20197d6f99b4cd5b77485dbcf144f4c2f2da5e2`. 최신 dev 반영은 A02 Android/문서 변경이며 웹 시험 코드는 동일하다.

## 범위

기존 콘솔·페달·키보드·가상 정지 요청을 재사용했다. 새 `web/drive-controls.js`는 입력 반응/소유권과 콘솔 문구만 계산한다. `web/sim.js`의 물리·A04 교차로 판정·이벤트 계약은 변경하지 않았다. 실제 차량 제어·자동 D 전환은 없다.

이번 A05 diff에는 Android, 음성 정책/`voice.js`, 리플레이/`replay.js`, 학습/`learn.js`, 소개/`about.js`, 온보딩/`onboarding.js`, 저장 모델과 SafetyGate 변경이 없다. C02 코드를 가져오거나 수정·커밋·병합하지 않았으며 C02 AVD를 사용/종료하지 않았다.

## 기존 동작과 변경 비교

| 대상 | 기존 | 변경 |
|---|---|---|
| WASD 안내 | 오래 핸들을 조작하면 자동 표시 | 콘솔 정보 버튼에서만 열기. Enter/클릭, 닫기/Escape 지원. 모바일 터치 설명 포함. 안내를 닫아도 주행 입력 유지 |
| 키보드 조향 | 즉시 ±110° | 목표 ±180°, 1200°/s로 접근. 해제 시 1800°/s로 0 복귀(180°에서 약0.1초) |
| 키보드 가속 | 즉시45% | 100 percentage points/s로60%까지(0→60 약0.6초) |
| 키보드 브레이크 | 즉시55% | 160 percentage points/s로70%까지(0→70 약0.44초) |
| 수동 핸들 | 드래그1:1, 슬라이더 ±540°, 5° 단위 | 범위/단위/드래그 배율 유지. 수동 조작은 해당 키보드 채널만 인계 |
| 페달 그림 | 가속2.5초/브레이크1.6초에100% | 상승 시간 유지. 자연 해제도 가속250ms/브레이크900ms 유지. pointercancel/예상치 못한 캡처 상실은 즉시0 |
| 슬라이더 | 브레이크 E1 수정 존재, 가속 누름/해제 애니메이션과 충돌 가능 | 양쪽 슬라이더가 자신의 페달 애니메이션·키보드보다 우선. 늦은 취소 콜백 차단 |
| 기어 | PRND 선택 강조 | 기존 선택 UI/색상 유지, 콘솔 상태 문구 추가. P와 주차 브레이크 확인, R/N/D 선택, 신호 확인 중을 구분. P/속도0만으로 게이트 해제하지 않음 |
| 정지 요청 | 요청 on/off만 캐시해 감속 후 문구가 남을 수 있음 | 요청 없음/감속 중/속도0 근처 정지 확인/요청 해제 구분. 버튼과 HUD가 같은 상태 함수 사용 |

키보드 수치는 웹 체험판의 입력 반응 설정이다. 실제 조향·제동 기준이나 실차에서 허용된 제어 강도가 아니다. `sample(dt)`는 프레임 지연을 최대60ms로 제한한다. 코드의 백분율은 기존0~100, 조향은 기존도(°), 가상 속도는 기존m/s를 유지한다.

## 입력 충돌 방지

- 가속/브레이크/조향은 독립 채널이다. 조향 키 입력이 수동 페달을0으로 덮어쓰지 않는다.
- W/↑ 등 물리 키를 따로 추적한다. 같은 역할의 두 키 중 하나만 놓아도 나머지는 유지된다. 좌/우 동시 입력은 중립 목표이며 둘 중 하나를 놓으면 남은 방향으로 접근한다. 키보드 가속·브레이크 동시 입력은 브레이크를 우선한다.
- 수동 슬라이더/드래그가 인계한 채널은 누르고 있던 키의 자동 반복으로 재활성화하지 않는다. 키를 놓은 뒤 새로 누르면 키보드가 다시 소유한다.
- 키 해제는 오버레이/포커스 조건보다 먼저 처리한다. 입력 폼 포커스·탭/창 비활성화 시 키보드 잔류를 제거하고, 진행 중 포인터/해제 애니메이션도 창 비활성화 시 해제한다. 의도적으로 설정한 수동 슬라이더 값은 활성 입력이 없으면 유지한다.
- 핸들 그림에 포커스가 있어도 WASD를 쓸 수 있다. 그림 자체의 좌/우 방향키15° 조작과 실제 range 입력의 키보드 조작은 보존한다.
- 페달은 소유 포인터만 해제할 수 있고 두 번째 포인터/보조 마우스 버튼은 누름을 훔치지 못한다. 각 애니메이션의 세대를 검사해 취소된 콜백이 새 수동 값을 덮지 못한다.
- 수동 핸들은 기존처럼 놓은 각도를 유지한다. 0 복귀는 키보드 해제·명시적 가운데 버튼 등 입력 방식에 따른 동작이다.

정지 상태는 `sim.stopReq`와 실제 가상 속도로 결정한다. 0.05m/s 이하를 가상 정지 확인으로 표시하며 요청 클릭만으로 정지했다고 표시하지 않는다. 해제는 기어·페달 확인 후 재가속 가능하다는 뜻이며 주차 게이트 해제가 아니다. 기존 `sim.requestStop()`을 그대로 호출한다.

## 실제 검증

Windows / Node24.22.1 / Chrome157.0.8103.51 / 기존 번들 Playwright. 외부 자산·무거운 라이브러리·설치 의존성을 추가하지 않았다.

- 기준 웹110/110 PASS. 새 페달5개는 기존 코드에서 실패(취소 잔류/포인터 소유/늦은 콜백/보조 버튼)함을 확인한 뒤 수정했다. 새 입력 모델11개를 포함한 최종 웹 **126/126 PASS**, 실패/건너뜀0.
- `node --check web/app.js`, `git diff --check`: PASS.
- `NODE_PATH=<기존 Playwright 경로> node web/test/controls.browser.cjs`: **1440×900,390×900 PASS**. 안내 키보드/닫기·핸들 드래그/슬라이더·WASD/방향키·좌우 동시·해제·포커스 이동·수동 우선·페달 짧게/길게/취소·PRND·가상 정지 상태·가로 넘침을 확인했다. 기존 온보딩5단계 완료/코치 마크/GNB 안내도 PASS. pageerror/console error0.
- `NODE_PATH=<기존 Playwright 경로> A04_EVIDENCE_DIR=<로컬 경로> node web/test/junction.browser.cjs`: 기존 **평가·강습은 정상 회전 기록과report, 연습은 복기 저장 후goal** 도달 PASS. A04 좌/우/직진26개 단위 테스트도 그대로 통과. 390/1440px, 브라우저 오류0/요청 실패0.
- A04 브라우저 검사기가 예전 온보딩 버전2를 가정하고 있어 현재 C04 버전을 코드에서 읽도록 검사기만 보완했다. 제품 온보딩은 무변경이다. READY 진입을 명시적으로 기다려 초기화 오류를 놓치지 않는다.
- 브라우저 최초 검사에서 A05 초기화 선택자 오타를 발견·수정한 후 최종 오류0을 확인했다. 390px 시트 전환이 끝나고 실제 안내가 보이는 화면도 확인했다.
- 원본 브라우저 결과는 [A05 결과](evidence/a05-2026-11-14/controls-results.json), [A04/시연 회귀](evidence/a05-2026-11-14/junction-results.json). 화면은 이 Worktree의 git 제외 `build/a05-evidence`와 `build/a05-junction-evidence`에 보존한다.
- 최신 dev의 A02 반영 후 웹 코드 동일성을 확인하고126개를 재실행했다. Android는 A05에서 수정하지 않았으며 실제 원격 CI 결과는 A05 PR/Actions 이력으로 구분한다. 브라우저 검사에서 음성/효과음을 껐으므로 실제 음질·발화 성공 시험이 아니다.

## 안전·검증 등급

근거: [SAFETY_RULES](SAFETY_RULES.md), [COMPLIANCE_REGISTER](COMPLIANCE_REGISTER.md), [P04 하드웨어 접근](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture), [P06 API 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability). 이번 시험은 공식 승인·실차 기능 인증이 아니다.

- S01 PASS(웹 Mock): 실제 차량 API/권한/직접 하드웨어 제어 추가0.
- S03/S04 PASS(웹 회귀): 기존 게이트·주행 잠금·복기 경로 보존. 콘솔은 웹 체험판 영역이며 실제 차량 주행 중 터치 UI가 아니다.
- S07/S10 PASS(표시/보고): Mock 출처 유지, 요청·감속·가상 정지·해제 구분. 가상 차로/교차로를 실차 위험 감지로 표현하지 않음.
- S11 신규 자산/라이브러리 없음. 실제 장비 CPU/RAM·실차 성능 검증은 NOT_VERIFIED.
- S02/S05/S06/S08/S09/S12는 신규 SDK/차량 주행·음성 정책/미션·저장·제출/공개 전환 변경 없음(NOT_APPLICABLE). 기존 제품의 해당 미검증 항목을 PASS로 바꾸지 않는다.
- G1~G4 PASS: A05 배정·전용 브랜치·허용 범위·Mock 입력 설계·위 실행 증거 확보. G5는 최신 dev·보호 파일 diff·CI 확인 후 통합. main·기존 브랜치/Worktree는 보존한다.

## 후속 후보

실제 사용자별 키보드 감도 비교와 설정은 별도 범위로 검토한다. 가상 자동D 전환은 이번 기본 구현에서 제외했다. 도입하더라도 명시적인 웹 Mock 선택·사전 안내·P/주차 브레이크 게이트 회귀 검증을 별도 설계해야 하며 실제 차량에는 적용하지 않는다. 실제 운전 실력 평가·실제 차량 제어·3D는 이 작업의 결과가 아니다.

## 최신 dev A03 충돌 해결 재검증 (2026-11-14)

기준 dev9a729698f87f93d78fb4a948f63db49ea8307277(A03 PR#29), 병합커밋 dbce4354d54093e519ea7d402712e2213d5e7e5c. 기존 A05 브랜치/전용 Worktree에서 일반 merge. 충돌은 IMPLEMENTATION_STATUS/VERIFICATION_LOG의 끝부분 기록2곳이며 양쪽 내용을 함께 보존했다. 각 문서의 이전 HEAD와 origin/dev의 모든 비어 있지 않은 줄이 해결본에 남아 있음을 스크립트로 확인. A02/A03/A05 기록 삭제0.

최신 dev 대비 app/ 전체·루트 Gradle·web/sim.js/core.js/voice.js/replay.js/learn.js/about.js/onboarding.js diff0. A03 NaviHelper 코드·권한·기본OFF/release차단 및 B/C 계약 보존. 다른 Worktree·C02·AVD 조회/조작0. main 보존.

Windows/Temurin17.0.20/Node24.22.1/Chrome157.0.8103.51. 웹126/126 PASS. A05 브라우저1440×900/390×900 PASS,오류0. JAVA_HOME/ANDROID_HOME은 프로세스 지정만 사용. .\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug — BUILD SUCCESSFUL26초,86/86 tasks 실행, XML집계 Mock164/164·Vehicle164/164·실패/오류0,두debug빌드 PASS. 기존 deprecated/strip 경고 유지. A04/세 모드 최종 결과는 아래 후속 기록으로 구분.

git diff --check 및 최신dev 대비 diff --check PASS. dev에서 들어온 역사적 A03_NAVIHELPER_FEASIBILITY 사본의 hard-break/EOF 공백 경고는 원본 보존; A05 변경 공백오류0.

S01 PASS(웹Mock·실제제어/권한추가0),S03/S04 PASS(기존 게이트/잠금회귀),S07/S10 PASS(Mock·가상상태 출처). S02/S05/S06/S08/S09/S12 NOT_APPLICABLE(신규SDK/주행서비스/음성/미션/저장/제출변경0). S11 신규자산/의존성0·실장비 CPU/RAM NOT_VERIFIED. 기존 제품의 미검증 등급은 승격하지 않음. 공식근거/적용범위는 본문P04/P06 및 SAFETY_RULES/COMPLIANCE_REGISTER의 P01/P02/P07/P08/P10 참조. G1/G2/G3 PASS(배정·범위·근거·Mock전용/차량경로무변경),G4 실행한 단위/빌드/브라우저만 PASS·Pleos/실차 NOT_VERIFIED. G5 최종회귀/최신dev/HEAD CI/mergeable 확인 후 dev통합·병합후CI 추적. 강제푸시/브랜치삭제 없음.

최종 후속 결과: node web/test/junction.browser.cjs 전체 PASS(평가/강습 TURN_GOOD 기록 후 report, 연습 reviewsSaved 후 goal, 390/1440 조작). 브라우저 오류0/요청실패0. A04 26개를 포함한 웹126개 PASS. 원본 결과는 build/a05-junction-evidence/browser-results.json 및 추적 evidence/a05-2026-11-14/junction-results.json. 최종 코드 dbce435 기준이며 이후 커밋은 이 문서/증거만 변경한다. G4/G5 로컬 회귀·보호 범위 검사는 PASS; 원격CI·병합은 PR#28의 최종 HEAD와 Actions 결과로 확인한다.
