# 00. 빌드 시작 — 첫날 순서와 마일스톤

> TL;DR — ① 영문 경로로 복사해 Android Studio로 열고 mock 플레이버부터 빌드 ② 컴파일 오류 수정 ③ 테스트 16개 통과 확인 ④ 에뮬레이터·Virtual Console 설치 ⑤ vehicle 플레이버로 실제 신호 연결(Day1 신호 시험) ⑥ 시연 영상·GitHub·APK.

## 0. 준비
| 항목 | 값 |
|---|---|
| Android Studio | Ladybug(2024.2) 이상 권장 |
| JDK | 17 |
| Gradle / AGP / Kotlin | 8.10.2 / 8.8.2 / 2.0.0 (공식 Vehicle SDK 샘플 앱과 같은 조합, 11/3 조사) |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0, API 34, Automotive, RAM 4GB, Cold boot |
| 경로 | **한글 없는 경로**(예: `C:\dev\chiilkkabwa-app`). 한글 경로는 Windows AGP 경로 검사에 걸림 |

## 1. 첫날 순서 (Day 1)
1. 이 폴더를 영문 경로로 복사 → `git init` → 첫 커밋(실명·이메일이 커밋 작성자에 들어가지 않게 팀 공용 이름 사용 권장: `git config user.name "chiilkkabwa-team"`)
2. Android Studio로 열기 → Sync. 실패하면:
   - `ai.pleos.playground:Vehicle:2.3.2`를 못 받으면 Playground 로그인·저장소 URL을 `docs/03`에서 재확인. 임시로 `app/build.gradle.kts`에서 주석 처리하면 mock 빌드는 된다(PleosVehicleSource는 SDK를 import하지 않으므로 컴파일 영향 없음).
3. `./gradlew :app:testMockDebugUnitTest` → 16개 통과 확인
4. `./gradlew :app:assembleMockDebug` → 컴파일 오류 수정(UI·data 레이어는 클라우드에서 컴파일되지 않았음)
5. 에뮬레이터에 mock APK 설치 → 디버그 패널(오른쪽 아래 '모의 ▲')로 전체 흐름 확인: P/주차브레이크 → 연습 시작 → 목표 → 워밍업 3+3 → 가상 주차 → (페달) → 약속 → D → 잠금 → P → 복기 → 배지
6. Playground에서 My Project 생성(카테고리: Lifestyle 1순위) → SDK 상태 화면 캡처(문서 대비 실제 '사용 가능' 확인)
7. vehicle 플레이버 설치 → `adb logcat -s AaosVehicleSource`로 **지원 속성 목록** 확인 → `docs/03` 표 갱신
8. Virtual Console에서 기어·주차브레이크·벨트·브레이크·방향지시등·핸들각·속도를 바꾸며 각 신호 수신 확인 → 결과를 `docs/06` 로그 양식에 기록
9. PleosVehicleSource의 TODO(sdk)를 실제 시그니처로 채움(Seat 벨트 → Brake → TurnSignal → SteeringWheel → Odometer → CarInfo → Wiper/Light 순)

## 2. 마일스톤
| # | 목표 | 완료 기준 | 목표일(제안) |
|---|---|---|---|
| M0 | mock 빌드 동작 | 디버그 패널로 전 흐름 1회 끊김 없이 | 11/7 |
| M1 | 에뮬레이터 신호 확인 | 신호별 '문서/계정/수신' 표 완성, 스크린샷 | 11/8 |
| M2 | 안전 게이트·잠금 실신호 | Virtual Console 속도/기어 변경 시 잠금·복귀(T02·T06·T07) | 11/10 |
| M3 | 워밍업 실신호 3종 | 벨트·브레이크·방향지시등 에뮬레이터 신호로 완료(T04·T05) | 11/12 |
| M4 | 1차 제출물 | 45~60초 시연 영상, GitHub(README·APK) — 선택 제출 | 11/14 |
| M5 | 2차 PT 완성 | T01~T12 통과, 가상 주차 핸들 연동, 복기 트립값, 리허설 3회 | 12/2 |

## 3. 완료의 정의(Definition of Done)
- 테스트 통과 + 해당 시험(T##) 결과를 로그 양식으로 남김
- 화면 출처 라벨이 실제 데이터 출처와 일치
- 주행 제한 중 어떤 입력도 받지 않음
- docs 갱신(신호 상태·문구)

## 4. 막히면
| 증상 | 대응 |
|---|---|
| Pleos 신호가 안 옴 | capability·권한 확인 → 미지원이면 UNSUPPORTED로 두고 화면 연습(모의)로 진행. 운영진(appdevchallenge@hyundai-partners.com)·partnership@pleos.ai 문의 |
| 주차 브레이크 신호 없음 | `GatePolicy(allowWhenParkingBrakeUnsupported = true)`는 **팀 결정 후**에만. 기본은 잠금 |
| CarUxRestrictions가 안 바뀜 | Virtual Console Motion에서 속도를 올려 확인. 그래도 안 되면 기어 D를 주행으로 보는 현재 규칙(NOT_IN_PARK → DRIVING_LOCK)으로 시연 |
| 페달 압력 미지원 | 페달 미션 자동 생략(현재 구현). 기획서에도 '조건부'로 표기돼 있음 |
