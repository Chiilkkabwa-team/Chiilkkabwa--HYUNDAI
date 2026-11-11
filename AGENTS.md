# AGENTS.md — 치일까봐(Chiilkkabwa) AI 개발 공통 규칙

이 파일은 저장소 루트부터 적용하는 Codex/AI 개발 지침이다. Claude Code는 `CLAUDE.md`도 읽는다.
작업을 시작할 때 이 문서와 아래 공통 문서를 확인한다. 상충할 때는 최신 사용자 지시, 제품 최종 접수 DOCX, 이 파일, docs/TEAM_GUIDE.md, 과거 회의 문서 순으로 취급한다. 단, 최종 DOCX는 제품 요구사항 기준이며 현재 구현 완료 증거가 아니다.

## 1. 매 작업 시작 시
1. `git status --short --branch`, `git branch --show-current`, `git fetch origin --prune`로 로컬 상태와 최신 원격 상태를 파악한다.
2. `docs/TEAM_GUIDE.md`(3인 협업), `docs/IMPLEMENTATION_STATUS.md`(기존 구현), `docs/VERIFICATION_LOG.md`(검증 증거), 관련 `docs/01_PRD.md`·`docs/03_PLEOS_SDK_NOTES.md`를 확인한다.
3. 새 코드 작업은 최신 `main`에서 `feature/<scope>`, `fix/<scope>`, `chore/<scope>`, `docs/<scope>` 중 맞는 브랜치를 생성해 진행한다. 작업 브랜치에 이미 변경 사항이 있다면 보존하고 사용자에게 상태를 먼저 보고한다.
4. 현재 목표·수정 예상 파일·다른 역할과 공유할 인터페이스·검증 방법을 짧게 설명하고 구현한다. 요청 범위 밖의 대규모 재작성은 하지 않는다.

## 2. 3인 협업 및 Git
- 한 작업 = 독립 브랜치. `main`에 직접 코드를 커밋하거나 강제 푸시하지 않는다.
- 다른 팀원 브랜치와 파일을 임의로 덮어쓰지 않는다. 공통 파일(특히 `ChiilViewModel.kt`, `MainActivity.kt`, `VehicleSignals.kt`, `Signals.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`)을 고치려면 먼저 변경 범위와 인터페이스를 통합 담당자와 합의한다.
- 담당 기본안: 차량 SDK/AAOS = `data/vehicle/`, 안전·이벤트 = `core/`, Compose·리플레이 = `ui/`. 실제 담당자는 팀 협의가 우선한다.
- **PR은 선택 사항이고 리뷰 승인도 필수가 아니다.** PR을 만들면 **한국어로 짧고 쉽게** 작성한다. 제목은 '무엇을 구현했는지', 본문은 '만든 것 2~3줄 / 확인한 테스트 1~2줄 / 미검증 사항'만 적는다. 장황한 생성형 설명, 영문 장문 템플릿, 불필요한 체크리스트는 피한다.
- 작업 브랜치의 커밋/푸시는 가능하다. `main` 병합은 통합 담당자가 차례대로 수행하며, AI는 명시적 요청 없이는 병합·브랜치 삭제·릴리스·저장소 공개 전환을 하지 않는다.
- 실패한 검증을 통과라고 보고하지 않는다. CI 실패 시 환경 문제와 기능 실패를 구분한다.
- 사용자가 요청하지 않은 비밀 키, 계정, 개인 정보, 로컬 설정 파일, 빌드 산출물을 커밋하지 않는다.

## 3. 차량 안전과 SDK (절대 준수)
- **불확실하면 잠금**: 기어 P + 주차 브레이크 ON + UX 제한 해제가 모두 신선하고 유효하게 확인될 때만 정차 학습·복기를 열어 준다. 속도 0만으로 주차 상태로 판단하지 않는다.
- 미션은 **시작 이후 새로 관측된 조작**만 성공으로 인정한다. 이전 세션 값, stale/unknown/unsupported, Mock 값으로 실차 성공을 선언하지 않는다. 이미 체결된 벨트를 풀었다 다시 채우도록 강요하지 않는다.
- 실차 / Pleos Emulator / Mock / 추정/미지원 출처를 UI·기록·영상에서 구별한다.
- 차량을 **읽기만** 한다. 제동·가속·조향 제어 API 또는 `CONTROL_CAR_*` 권한을 추가하지 않는다.
- 실제 문서/SDK에 없는 클래스·시그니처·VehicleProperty ID를 만들어내지 않는다. 지원 여부·권한·SDK 버전·차종 지원을 확인하고 미지원은 폴백 또는 비활성화한다.
- 주행 중 **터치·복기·학습 화면 활성화 금지**. 음성/백그라운드 수집은 오디오 포커스만으로 허용된다고 가정하지 않는다. Pleos 주행 규제·카테고리·권한·생명주기 정책과 에뮬레이터 실측을 확인하기 전에는 미검증으로 유지한다.
- 영상/음성 원본, GPS 정밀 궤적, VIN 등 계획 외 민감 정보를 수집하지 않는다. 로컬 기록은 설계에 맞춰 보호하며 `SharedPreferences`를 암호화된 Room으로 잘못 기술하지 않는다.

## 4. 구현·검증·보고
- 컴파일 성공 != APK 실행 성공 != 실제 신호 수신 != 실차 검증.
- Windows PowerShell: `./gradlew` 대신 `.\gradlew.bat` 사용. 기본 검증:
  `.\gradlew.bat :app:testMockDebugUnitTest`
  `.\gradlew.bat :app:assembleMockDebug`
  `.\gradlew.bat :app:assembleVehicleDebug`
  웹 로직 변경 시 `node --test web/test/*.test.mjs`.
- 변경과 연관된 테스트부터 실행하고, 영향이 넓으면 두 Android 플레이버와 전체 웹 단위 테스트까지 확인한다. Android/Pleos 에뮬레이터 시험은 실제 실행 후에만 PASS라 적는다.
- 실제 실행한 명령, 환경, 커밋 SHA, 결과, 실패/차단 원인을 `docs/VERIFICATION_LOG.md` 양식으로 기록한다. 작업 완료 시 `docs/IMPLEMENTATION_STATUS.md`의 관련 항목을 현재 상태와 맞춘다.
- 응답은 한국어, 핵심부터. 변경 파일, 만든 기능, 테스트 결과, 남은 위험/다음 작업을 구별해 짧게 보고한다.

## 5. 기준 문서
- 제품 요구사항: 최종 공식접수기획서 DOCX(원본은 로컬/팀 공유 자료이며 저장소에 복제하지 않음). 접근할 수 없으면 가정하지 말고 관련 명세와 사용자 지시를 확인한다.
- 협업 규칙: `docs/TEAM_GUIDE.md`
- 기존 코드 사실: `docs/IMPLEMENTATION_STATUS.md` + 실제 소스 확인
- 시험 증거: `docs/VERIFICATION_LOG.md`
- 미정정 문서 목록: `docs/FACT_AUDIT.md`
- 기존 브리프와 회의록은 역사적 자료로 취급하고 최종 DOCX나 실제 코드와 충돌하면 임의로 사실을 덮어쓰지 않는다.
