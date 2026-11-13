# CLAUDE.md — 치일까봐(Chiilkkabwa) Pleos Connect 앱

> **먼저 루트 [AGENTS.md](AGENTS.md), [docs/SAFETY_RULES.md](docs/SAFETY_RULES.md), [docs/COMPLIANCE_REGISTER.md](docs/COMPLIANCE_REGISTER.md)를 읽고 준수한다.** `AGENTS.md`는 Codex·Claude Code 등 AI 개발 도구가 공유할 공통 개발 원칙이다. 이 파일은 Claude Code용 보충 안내다.

## 1. 프로젝트
- 초보·장롱면허 운전자가 주차된 차량에서 조작을 익히고, 주행 중에는 0터치 안전 원칙을 지키며, 주차 후 속도·조작 이벤트를 복기하는 **읽기 전용 Pleos Connect/AAOS 앱**.
- 원칙: **확인은 차가, 판단은 사람이.** 차량의 제동·가속·조향을 물리적으로 제어하거나 도로 안전을 자동 판정하지 않는다.
- 최종 공식접수기획서 DOCX가 제품 요구사항의 기준이다. 코드 구현이나 에뮬레이터 검증이 완료됐다는 뜻은 아니다.
- 공모전 1차 제출 예정일 2026-11-15, 2차 PT 2026-12-03~04. 실제 일정·규정은 공식 공지를 확인한다.

## 2. 작업 전 읽을 문서
1. [AGENTS.md](AGENTS.md) — **안전·Git·PR·AI 공통 규칙**
2. [docs/SAFETY_RULES.md](docs/SAFETY_RULES.md) — **S01~S12 제품 안전 불변조건 + G1~G5 검증 게이트**
3. [docs/COMPLIANCE_REGISTER.md](docs/COMPLIANCE_REGISTER.md) — **공모전/Pleos 공식 URL·확인일·적용 범위·미확인 항목**
4. [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md) — **기능별 독립 개발** 및 3인 통합 방식
5. [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) — 실제 구현 상태(작성 시점의 스냅샷)
6. [docs/VERIFICATION_LOG.md](docs/VERIFICATION_LOG.md) — 빌드/에뮬레이터 결과 기록
7. 작업에 관계된 [docs/01_PRD.md](docs/01_PRD.md), [docs/03_PLEOS_SDK_NOTES.md](docs/03_PLEOS_SDK_NOTES.md), [docs/FACT_AUDIT.md](docs/FACT_AUDIT.md)

## 3. 이번 개발의 중요한 기술적 사실
- Kotlin/Jetpack Compose, Android `mock`/`vehicle` 플레이버, Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.0.
- `core/`: 안전 게이트, 미션·세션, 복기 요약과 배지 기반 로직 존재.
- `data/vehicle/`: AAOS 일부 조회 코드 존재. `PleosVehicleSource`는 아직 SDK 실연동 TODO.
- `data/store/`: `SharedPreferences` JSON 기반 코드 존재. 최종 DOCX의 암호화 Room 계획은 아직 구현되지 않음.
- `ui/`: 기본 Compose 화면과 Mock 디버그 화면 존재. **Android 음성 코칭·실차 시계열 리플레이는 미구현**.
- `web/`: 차량 신호가 아닌 웹 Mock 시연용. 웹에서 되는 기능을 Android 구현 완료로 간주하지 않는다.
- 2026-11-11 세팅 브랜치 CI에서 웹 단위 테스트, Android mock 테스트·mock/vehicle 빌드가 통과했다. **Pleos Emulator 실신호나 실차 동작을 입증하는 결과는 아니다.** 최신 실행 이력은 GitHub Actions와 검증 기록 확인.

## 4. AI 작업 방식
- **개발자별 폴더 고정 담당을 두지 않는다.** 팀에서 배정한 기능 하나를 담당자 한 명이 UI·Core·Data·테스트·문서까지 구현한다. 담당 기능은 아직 미정이며 AI가 임의로 배정·착수하지 않는다.
- **최신 `dev`에서 기능 브랜치를 만들고, 담당 기능을 자체 검증한 뒤 다른 팀원의 컨펌 없이 직접 `dev`에 병합할 수 있다. AI도 배정받은 기능이라면 별도 병합 승인을 다시 묻지 않는다.** 충돌·테스트 실패·안전 FAIL은 스스로 해결해야 한다. 공유 계약 변경은 기록·공유하되 동료의 답변을 기다리는 절차는 없다. **`main` 직접 커밋·푸시·기능 PR·임의 병합은 금지**하며 `dev → main`은 **팀의 명시적 승인 + 전용 PR + 최종 검증** 후에만 진행한다.
- 차량 신호·이벤트·세션 상태 등 여러 기능이 쓰는 데이터 계약은 병렬 구현 전에 합의한다. 요청받지 않은 기능 추가와 대규모 리팩터링은 금지한다.
- **매 기능 착수 전 규정 S-ID·공식 원문 링크와 현재 준수/미검증 상태를 확인**하고, 구현 후 PASS/FAIL/NOT_VERIFIED/NOT_APPLICABLE 및 근거를 기록한다. 주행 중 백그라운드 음성·신호 구독은 AudioFocus만으로 허용된다고 가정하지 않는다. Pleos LLM SDK·Gleo Interface 승인 신청·차량 마이크의 외부 STT 전송을 공모전 구현 범위에서 제외한다.
- **기능→`dev`는 담당자가 자체 병합 가능**, PR·동료 리뷰·사전 승인 모두 선택. PR을 만들면 한국어로 짧게 작성하고 base=`dev` 확인. **`dev` 병합 후 CI 실패는 담당자가 직접 해결한다.** **`main`용 PR은 팀이 승인한 `dev → main` 릴리스에서만** 사용한다.
- 사용자가 요구한 범위만 구현하고 정밀 테스트 후 보고한다. 필요한 사실은 실제 코드·공식 SDK 문서·실행 결과로 확인한다.
- 주차 게이트·새 이벤트·신호 신선성·오디오 정책이 관련되면 실패 경로까지 테스트한다. 지원되지 않는 차량 SDK 기능이나 API를 가정하지 않는다.
- 일시적으로 해결할 수 없는 부분은 `미검증`, `BLOCKED`, `UNSUPPORTED`로 정확하게 기록한다.
- 중요한 화면·문구·시나리오 변경은 관련 문서를 함께 정정한다. 오래된 회의록을 현재 사실처럼 재해석하지 않는다.
- 답변은 **한국어로 간결하게**: 구현 내용 → 검증 결과 → 남은 위험.

## 5. 편의 명령 (Windows PowerShell)
```powershell
git fetch origin --prune
git switch dev              # 처음엔 git switch -c dev --track origin/dev
git pull --ff-only origin dev
git switch -c feature/my-task

.\gradlew.bat :app:testMockDebugUnitTest
.\gradlew.bat :app:assembleMockDebug
.\gradlew.bat :app:assembleVehicleDebug
```

**주의:** 새 clone의 기본 브랜치가 `main`이어도 그곳에서 개발을 시작하지 않는다. `dev`로 이동한 뒤 기능 브랜치를 만든다. 자세한 협업·검증 규칙은 [AGENTS.md](AGENTS.md)와 [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md)를 따른다.
