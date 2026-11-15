# 배경지식 노트 (2026-11-15 기준)

> 새로 합류한 사람이나 새 AI 세션이 "이건 왜 이렇지?"를 다시 조사하지 않도록, 지금까지 알아낸 사실·결정·함정을 한곳에 모은다.
> 규칙의 원본은 [AGENTS.md](../AGENTS.md)·[SAFETY_RULES.md](SAFETY_RULES.md)·[COMPLIANCE_REGISTER.md](COMPLIANCE_REGISTER.md)다. 여기는 **요약과 경험**이고, 충돌하면 원본이 이긴다.
> 사실이 바뀌면 이 문서의 해당 줄을 고치고 날짜를 적는다.

## 1. 제출 일정과 규칙

| 항목 | 내용 | 출처·확인 |
|---|---|---|
| 1차 접수 | 원래 2026-11-15. **운영진 메일로 7일 연장**(11/15 사용자 전달). 공식 공지 페이지에는 날짜 있는 공지가 아직 안 보임 → 새 마감 일시는 메일 원문 기준 | 사용자, 공지 페이지 11/15 확인 |
| 필수 | 공식 DOCX(팀원 전원 동의·서명·날짜) | [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission) |
| 선택(가산점) | 시연 영상(화면 녹화 인정), GitHub(소스+APK) | 같음, [심사](https://appdevchallenge.auto-hmg.io/guide/judging-awards) |
| APK | **저장소 `apk/` 폴더**에 `팀명_앱이름_release.apk`, 용량 초과 시 Releases. "실행 가능한 release 버전" | 제출 방법 |
| README 필수 | 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·설정, **에뮬레이터 버전과 화면 규격**, APK 위치 | 제출 방법 |
| Private 저장소 | 초대 불필요. 마감 후 공개 전환·유지(원래 11/16·11/23 — 연장 반영 여부 메일 확인) 또는 마감 전 소스+APK 압축본 링크 | 제출 방법, FAQ |
| 메일 | appdevchallenge@hyundai-partners.com, 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`, 전원 CC, 커밋 SHA | 제출 방법 |
| 1차 심사 | 차량특화성·창의성·기술 활용성·사업성(배점 비공개) | 심사·시상 |
| 2차 PT | 실제 동작 가능한 앱 필수. 시연 방식·시간은 추후 안내 | 심사·시상 |
| 개발 환경 | API 28+, Gradle 8+, Java/Kotlin. SDK: Vehicle·FusedLocation·NaviHelper·TextToSpeech·SpeechToText | [개발 환경](https://appdevchallenge.auto-hmg.io/guide/development) |
| 성능 기준 | 공모전 페이지에는 없음. Pleos 문서 권장 CPU 3%·RAM 400MB(P10)를 **팀 내부 목표**로 씀 | COMPLIANCE_REGISTER |

절차는 [SUBMISSION_RUNBOOK.md](SUBMISSION_RUNBOOK.md)에 명령어까지 있다.

## 2. 블라인드 — 알고 있어야 할 것

- 파일 내용에는 실명·학교·연락처·키가 없다(11/15 검색).
- **Git 이력의 커밋 작성자 정보에는 실명 형태 이름 1개·개인 메일 3개가 있다.** 저장소를 Public으로 바꾸면 보인다. 이력 재작성은 팀 브랜치를 전부 깨뜨려 비추천 → 압축본(`.git` 제외) 또는 이력 없는 새 공개 저장소가 안전([08 §6](08_SUBMISSION_CHECKLIST.md)).
- 회의 파일 제목에 있던 팀명 후보는 쓰면 안 된다([12 §6](12_SUBMISSION_STRATEGY.md)). 팀명은 아직 미정.
- Vercel 개별 배포 주소(`…-<계정명>.vercel.app`)에는 계정명이 들어간다 → 제출·공유에는 **운영 주소만**.

## 3. 브랜치·병합·배포

- 기능 브랜치 → `dev`: 담당자(AI 포함)가 자체 검증 후 직접 병합 가능. `dev → main`: **팀의 명시적 승인 + 전용 PR**만. 제출 SHA는 `main` 기준.
- `main` 반영 이력: 11/13 PR #8(사용자 요청) 1회. 이후 작업은 모두 `dev`에만 있다 → **웹 운영 주소는 아직 11/13 내용**.
- CI(`.github/workflows/ci.yml`, Team CI): 웹 단위 + Android mock 단위 + mock/vehicle **debug** 빌드. push(main·dev·feature/** 등)와 dev·main 대상 PR에서 돈다. `claude/**` 브랜치 push에서는 안 돈다(PR을 만들면 돈다).
- release 빌드 확인(`.github/workflows/release-apk.yml`): `claude/**`·`chore/release-apk` push 또는 수동 실행 → mock/vehicle **release** APK + 성능 예산 테스트 + 권한 출력, artifact 14일. 공식 제출 업로드는 하지 않는다.
- **Vercel**: 프로젝트 `chiilkkabwa-web`, rootDirectory=`web`(없으면 404), 프레임워크 없음(정적). 운영 주소 https://chiilkkabwa-web.vercel.app. 11/13에 MCP로 `gitSource`(org `Chiilkkabwa-team`, repo `Chiilkkabwa--HYUNDAI`, ref `main`) 배포 성공 → 새 조직 저장소 연결은 동작한다. `main` 푸시 시 자동 배포 여부는 아직 확인 못 함.

## 4. 앱의 현재 사실 (자주 헷갈리는 것)

| 사실 | 왜 그런가 / 어디서 |
|---|---|
| **release 빌드에서는 Pleos 신호·AAOS 속도·NaviHelper가 꺼져 있다** | `app/build.gradle.kts` release `buildConfigField` false 고정. 미검증 실차 경로 기본 잠금(S03·G5). debug에서만 `-PenablePleosSignals=true` 등으로 시험 |
| 그래서 vehicle release APK는 에뮬레이터에서 주차 확인 단계에 머물 가능성이 크다 | AAOS 기어·주차 브레이크·주행 제한만 읽음. Pleos 콘솔로 주차 브레이크 ON이 안 만들어짐(A01 실측) |
| **mock release APK**는 화면 안 '모의' 패널로 전체 흐름을 볼 수 있다 | `DebugPanel`은 `USE_MOCK`이면 release에서도 보임 |
| Android 주행 중 음성은 **mock 앱에서만** 말한다 | `MockDriveCoach` → B02 `CoachingDecisionEngine`은 MOCK 출처만 허용. 주행 중 실제 음성 허용은 미검증(S05) |
| 패널 '급가속(모의)' = 0→50km/h를 1.2초에 | A02 판정 구간(0.05~2초, ≤15m/s²) 안에 들어가게 만든 시연 버튼 |
| 칭찬(STEADY)은 Android에 연결 안 함 | mock 속도가 주기적으로 오지 않아 '90초 조용함'을 정직하게 판정할 수 없음 |
| 웹 음성 규칙(`web/voice.js`)과 Android B02는 같은 규칙의 두 구현 | 플랫폼이 달라 의도된 중복. 규칙을 바꾸면 둘 다·양쪽 테스트를 고친다 |
| 저장소는 SharedPreferences JSON | 최종 DOCX의 암호화 Room은 미구현 — 그렇게 쓰지 않는다 |
| APK 약 22.9MB, 대부분 dex | `isMinifyEnabled=false`. R8 축소는 리플렉션(페달 속성)·Pleos SDK 규칙 확인 뒤(2차 전 후보) |
| 웹 3D 차 모형은 CSS 3D뿐 | 외부 모델·라이브러리 없음(권리·용량). 정차 화면에만. Android 3D는 미구현 |

## 5. 검증 수치 (마지막 측정)

| 항목 | 값 | 날짜·근거 |
|---|---|---|
| 웹 단위 테스트 | 126/126 | 11/15 |
| Android 단위 테스트 | 241/241(mock, C01 기준) + B02·성능 예산 테스트 | VERIFICATION_LOG |
| 판단 로직 CPU(JVM) | 주행 잠금 0.0013% @10Hz, 준비 0.0017% @5Hz | Actions run 43664504111 |
| 웹 메인 스레드(데스크톱 Chromium) | 연습 정차 ~2.3%, 연습 주행 잠금 ~2.4%, 평가 가상 주행 ~11% | `tools/web-check.cjs perf` |
| 접근성 | axe WCAG 2 A·AA 8개 화면 위반 0 | `tools/web-check.cjs axe` |
| Android 화면 포함 CPU·메모리 | **미측정** — 에뮬레이터 Profiler 필요 | — |
| 에뮬레이터 실수신 | 벨트 초기값·브레이크·방향지시등, 속도 증가/감소, NaviHelper 연결·재연결(경로 요청은 BLOCKED) | IMPLEMENTATION_STATUS, docs/evidence |

## 6. 작업 환경의 함정

**Claude Code 클라우드 컨테이너**
- Maven Central이 **429(요청 제한)** 를 자주 준다. Gradle은 실패를 캐시해서 재시도해도 같은 "Could not find …jar"가 난다 → 이 환경에서는 Android 빌드를 포기하고 **GitHub Actions**로 빌드한다. 저장소 밖 Gradle 미러 설정은 자동 권한 판단에서 막혔다.
- `/dev/kvm`이 없어 Android 에뮬레이터(API 34)를 돌릴 수 없다.
- Playwright·Chromium은 미리 설치돼 있다. axe-core만 `npm i axe-core@4.10.2`로 받으면 `tools/web-check.cjs`가 돈다(`NODE_PATH`에 그 node_modules 추가).
- 세션 간에 파일이 사라질 수 있다 → 남길 것은 커밋.

**PC(Windows) 연결**
- Remote Control 목록에 PC(THK_ThinkPad)는 보이지만 폴더가 클라우드 세션에 'not_available'(앱 업데이트·재시작 뒤에도, 11/15). 이 기능은 아직 모든 계정에 열려 있지 않다.
- 대안: **PC에서 Claude Code 세션을 직접 연다**(데스크톱 앱에서 저장소 폴더로 새 Code 세션, 또는 저장소 폴더 터미널에서 `claude`). 그러면 Android Studio·adb·에뮬레이터를 그대로 쓸 수 있다.
- PowerShell에서 `fatal: not a git repository`는 저장소 폴더 밖(예: `C:\WINDOWS\System32`)에서 실행한 것.

**GitHub**
- 이 클라우드 세션은 `gh` CLI 대신 GitHub MCP 도구로 PR·병합·Actions 로그를 다룬다. Actions 로그·artifact는 MCP가 주는 임시 URL로 받는다.

## 7. 결정 기록 (짧게)

| 날짜 | 결정 | 이유 |
|---|---|---|
| 11/9 | 음성 길잡이 기본 켜짐, 주행당 3·5·10·20회 설정 | 팀 결정 |
| 11/12 | 겹친 상황은 0.8초 모아 가장 급한 하나만, 칭찬 90초 | 회의(5초는 잦음, 10분은 들을 일 없음) |
| 11/12 | 회전 멘트는 길 안내가 아니라 주의만 | 내비와 중복 금지 |
| 11/13 | 기능 → dev 자체 병합, dev → main은 승인 | 3인 동시 개발 |
| 11/15 | 기능 동결(사용자) — 이후 3D·성능·정리 같은 마무리만 | 제출 준비 |
| 11/15 | Android 주행 중 음성은 mock에서만 | S05 미검증, 시연은 가능하게 |
| 11/15 | 3D는 웹 CSS로만, Android 3D·R8 축소는 2차 전 | 권리·성능 측정·위험 |
| 11/15 | 추천: 제출 APK는 mock release, 공개는 압축본 또는 새 저장소 | 흐름 전체 시연, 블라인드 |

## 8. 열린 질문 (사람이 정할 것)

1. 팀명 2. 제출 APK(mock/vehicle) 3. GitHub 공개 방식 4. `dev → main` 릴리스 시점 5. 새 마감 일시(메일 원문) 6. 에뮬레이터 화면 규격 값(`adb shell wm size`)

## 9. 문서 지도

| 무엇을 알고 싶나 | 문서 |
|---|---|
| 제출하려면 지금 뭘 하나 | [SUBMISSION_RUNBOOK.md](SUBMISSION_RUNBOOK.md) |
| 무엇이 구현·검증됐나 | [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md), [VERIFICATION_LOG.md](VERIFICATION_LOG.md) |
| 안전 규칙 | [SAFETY_RULES.md](SAFETY_RULES.md) |
| 공식 근거 | [COMPLIANCE_REGISTER.md](COMPLIANCE_REGISTER.md) |
| 이번 세션에서 한 일 | [WORKLOG_CLAUDE_2026-11-12_15.md](WORKLOG_CLAUDE_2026-11-12_15.md) |
| 웹 브라우저 점검 도구 | [`tools/web-check.cjs`](../tools/web-check.cjs) |
