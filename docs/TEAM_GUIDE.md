# 3인 공동개발 가이드

기준: 2026-11-11. 제품 요구사항은 **공식접수기획서 최종 DOCX**를 우선한다. 이 파일에는 팀원의 실명·이메일을 기록하지 않는다.

## 1. 합의된 방식

- 저장소의 기준 브랜치는 main. 기능 개발은 **한 작업 = 한 기능 브랜치**로 수행한다.
- **PR 및 동료 리뷰는 필수가 아니다.** PR을 사용하면 제목/설명을 한국어로 짧게 적는다.
- 원칙적으로 main 직접 개발/강제 푸시는 하지 않는다. 통합 담당자 한 명이 테스트된 변경만 순차적으로 병합한다.
- 각자 맡은 파일을 우선 수정하고, 공통 파일 변경은 작업 시작 전에 팀 채팅/Issue에서 공유한다.
- 변경 사항을 작게 유지해 병합 충돌과 디버깅 범위를 줄인다. CI가 실패하거나 실행되지 않으면 원인 및 대체 검증 결과를 기록한다.
- 차량 안전·권한·신호 출처 관련 변경은 회귀 테스트 없이는 병합하지 않는다.
- 모의 입력, 에뮬레이터 수신, 실차 수신, 문서 확인을 서로 혼동하지 않는다.

## 2. 초기 역할(사람별 배정은 팀에서 확정)

| 역할 | 주요 수정 영역 | 책임 |
|---|---|---|
| A — 차량 플랫폼 | app/src/main/java/com/chiilkkabwa/app/data/vehicle/ | AAOS/Pleos, 권한, 신호 실수신 |
| B — 핵심 로직 | app/src/main/java/com/chiilkkabwa/app/core/, app/src/test/ | 안전 게이트, 세션, 이벤트 판정 |
| C — Android UI | app/src/main/java/com/chiilkkabwa/app/ui/ | Compose, 복기 타임라인, 프로필 |
| 통합 담당(1명) | 공통 연결 지점 | 인터페이스 변경 조율, main 순차 병합 |

공동 파일: ChiilViewModel.kt, MainActivity.kt, VehicleSignals.kt, Signals.kt, AndroidManifest.xml, app/build.gradle.kts, README.md, docs/01_PRD.md. 두 명이 같은 파일을 동시에 수정하지 않도록 담당자와 변경 순서를 미리 합의한다. 새로운 데이터 인터페이스는 사용 팀원에게 공유한 뒤 개발한다.

## 3. 각 PC에서 작업 시작

PowerShell (이미 clone을 완료한 팀원은 다시 clone할 필요 없음):

~~~powershell
git fetch origin --prune
git switch main
git pull --ff-only origin main
git switch -c feature/my-task
~~~

수정 후:

~~~powershell
git status
git add -p
git commit -m "feat: 기능 요약"
git push -u origin feature/my-task
~~~

담당 브랜치의 CI 결과를 확인하고 실제 실행 결과를 docs/VERIFICATION_LOG.md 양식으로 남긴다.

## 4. 병합 규칙 (리뷰/PR 대기 없음)

- 다른 사람 작업 영역과 충돌하는 수정이 있는지 통합 담당자에게 알린다.
- 통합 담당자는 최신 main을 기준으로 **한 브랜치씩** 병합한다.
- 병합 전에 관련 테스트, Android mock/vehicle 컴파일, 출처 표시, 안전 잠금을 확인한다.
- 병합 직후 main에서 테스트를 재실행하고 결과를 기록한다.
- 실패 시 원인을 해결하거나 해당 통합을 되돌린다. main을 강제 푸시해 과거 이력을 덮지 않는다.
- 1차 제출은 별도 기준 커밋/태그를 고정해 개발 중인 변경과 구분한다.

## 5. 문서와 제품 규칙

- 최종 DOCX: 제품 요구사항(아직 구현되지 않은 계획을 포함).
- docs/IMPLEMENTATION_STATUS.md: 현재 구현 사실. 반드시 기준 SHA와 코드 경로 명시.
- docs/VERIFICATION_LOG.md: 실제 테스트 결과. 기기, SDK, 일시, 증거를 기록.
- docs/FACT_AUDIT.md: 기존 Markdown의 정정 및 미해결 목록.
- 차량 제어 API 금지, 미확인 SDK 시그니처 추측 금지, 신호 불명 시 안전 잠금 우선.
- 계정·연락처·키스토어·토큰·운전자의 민감한 정보는 Git에 넣지 않는다.
