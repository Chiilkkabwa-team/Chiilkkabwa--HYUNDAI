# 실제 검증 기록

**실제로 실행한 검증만 PASS로 기록**한다. 예상·문서·설계 검토를 실행 결과처럼 표시하지 않는다.

## 검증 1건당 기록 양식

- 일시(KST):
- 담당 역할:
- 브랜치 / commit SHA:
- OS / Android Studio / JDK / Gradle / SDK / 에뮬레이터 버전:
- 플레이버:
- 명령 또는 Virtual Console 입력:
- 시험 대상/시나리오:
- 기대 결과:
- 실제 결과:
- 상태(PASS / FAIL / BLOCKED / NOT_RUN):
- 재현 가능한 증거(비식별 로그/스크린샷/링크):
- 추가 조치/이슈:

## 기존 문서의 과거 실행 기록 (이번 실행 아님)

- 2026-11-08: CLAUDE.md와 docs/14에는 Android mock/vehicle 디버그 빌드 및 Kotlin 테스트 17/17 통과라고 기록되어 있다. 이번에 독립 재실행한 결과는 아니다.
- 에뮬레이터 실제 신호 수신, 주행 중 오디오 정책, 실차 실행에 관한 재현 가능한 PASS 증거는 현재 확인되지 않았다.
- 새 GitHub Actions 결과는 실행 후에만 기록한다. 네트워크/저장소 접근 문제는 BLOCKED로 기록하고 테스트 PASS와 혼동하지 않는다.

## 검증 단계 구분

1. 소스에 구현 코드 존재
2. Gradle/Node 컴파일 및 실행 성공
3. 자동 단위 테스트 PASS
4. Mock 화면/시나리오 검증
5. Pleos Emulator 실제 신호 수신
6. 정책·라이프사이클·오디오 포커스 통합 검증
7. 실차 검증(환경 및 권한 확보 시)


## 2026-11-12 웹 모의 기능 추가 (11/12 회의 반영)

- 일시(KST): 2026-11-12
- 담당 역할: AI(Claude Code) — 배정 확정 전, 사용자 요청으로 웹 부분만
- 브랜치 / commit SHA: `claude/confident-wright-u9iqdb` / `e5e5be3`·`55483e9`·`b4275a4`·`7738f94` (기준 main `b9166eb`)
- 환경: Linux 클라우드 컨테이너, Node 22.23.4, Playwright Chromium(헤드리스). Android·에뮬레이터 실행 없음
- 플레이버: 해당 없음(웹)
- 명령: `node --test "web/test/*.test.mjs"` / 정적 서버 + Playwright로 연습·평가 자동 시연 끝까지, 콘솔 페달·핸들·WASD 조작, 도움말·운전 노트·소개 열기, 390px 폭
- 기대 결과: 단위 테스트 전부 통과, 브라우저 콘솔 오류 0, 자동 시연이 복기/리포트까지 도달, 가로 스크롤 없음
- 실제 결과: 단위 테스트 **64/64 통과**(기존 46 + 신규 18). 브라우저 오류 0건. 평가 자동 시연 → 교차로 우회전 '잘함' → 리포트 시간표·장면 재현 열림. 연습 자동 시연 → 리플레이 시간표 표시. 브레이크 페달 0.5초 누름 = 31%, 놓으면 0. W 1.5초 = 가속 45%. 390px 폭 scrollWidth 390
- 상태: 웹 단위 테스트 **PASS** / 웹 브라우저 조작 **PASS**(헤드리스 Chromium) / axe 접근성 **NOT_RUN** / Android 빌드 **NOT_RUN** / Pleos Emulator **NOT_VERIFIED** / 실차 **NOT_VERIFIED**
- 규정: S01 PASS(차량 제어 코드 없음, 웹) · S04 PASS(웹: 재현 창·도움말·노트는 주차 후/정차 중 화면에서만, 주행 진입 시 닫힘) · S05 NOT_VERIFIED(주행 중 음성은 웹 모의만) · S07/S10 PASS(웹: '가상'·'시뮬레이션 재현·실제 영상 아님' 표기) · S08 PASS(벨트 도움말 '그대로 두세요') · S09 PASS(시간표 원자료 메모리만) · S11 NOT_APPLICABLE(외부 음성·3D·이미지 도입 안 함)
- 추가 조치/이슈: Typecast·ElevenLabs(B03)와 3D(A06)는 권리·성능 확인 전이라 하지 않음. 주행 진입 순간의 평가 모드 '핸들을 가운데로' 알림(기존 코드)은 주행 잠금 직후 뜰 수 있어 S04 관점에서 따로 볼 것

## 2026-11-13 A01 / Issue #16 — 실제 SDK 시그니처·읽기 어댑터

- 일시(KST): 2026-11-13, 본 채팅에서 직접 실행. 담당: 사용자 A 파트 요청에 따른 Codex.
- 브랜치: `feature/a01-pleos-vehicle-signals`; 기반 `origin/dev b66f28e70a32c4b474cb85ecc02841c1e3a6fb8e`; 시험 코드 commit `dd0a5a17d97457158598f112a0038563942707da` (문서 정리는 후속 커밋).
- 환경: Windows/PowerShell, Temurin JDK `17.0.20.1+1`, Gradle wrapper `8.10.2`, compile SDK 35 / target 34 / min 28, Vehicle `2.3.2`, Node `v24.22.1`. Android Studio GUI 실행 없음. SDK는 로컬 설치 및 Gradle 캐시를 사용했으며 새 SDK 바이너리를 추가하지 않았다.
- 저장소: 기존 작업 폴더와 감사 사본에는 `.git`이 없어서 최신 dev를 별도 `a01-pleos` 폴더에 clone했다. 원격 네트워크는 sandbox 밖의 허용된 명령으로 접근했다. Git 작성자 설정은 명령 단위 GitHub noreply를 사용했으며 개인 이메일/전역 설정을 저장하지 않았다.

### 실제 명령·결과

| 시험 | 명령/증거 | 결과 |
|---|---|---|
| 기반 확인 | `git clone --branch dev --single-branch ...`, `git fetch origin --prune`, `git switch -c feature/a01-pleos-vehicle-signals` | PASS, dev b66f28e 기준 독립 브랜치 |
| SDK 실물 | 캐시 Vehicle/2.3.2 AAR/sources/javadoc 목록, `Get-FileHash -Algorithm SHA256`, ZIP 내 classes.jar의 `jar tf`, `javap -public` | PASS, SDK 노트 A01 절의 메서드/영역/값/리스너 및 해시. VehicleException 공개 이름 난독화 차이도 확인 |
| 수정 전 테스트, 원래 경로 | `.\gradlew.bat -Pandroid.overridePathCheck=true --offline :app:testMockDebugUnitTest` | 최초 경로 검사 실패. 인자를 작은따옴표로 묶어 검사 우회 후에도 한글 경로에서 기존 3 suite ClassNotFoundException. 기능 실패와 구분 |
| 기준 환경 복구 | `subst R: '<로컬 작업 폴더>'`, `$env:ANDROID_HOME='<기존 Android SDK>'`; `& R:/a01-pleos/gradlew.bat -p R:/a01-pleos --offline :app:testMockDebugUnitTest` | PASS, 기존 28 테스트. 파일 이동 없이 ASCII 경로 별칭 사용. 저장소 설정 변경 없음 |
| 신규 테스트 RED | PleosSignalSessionTest 미구현 참조 컴파일 실패; 정책 helper 미구현 참조 실패. VehicleRepositoryLifecycleTest 2개 AssertionError, SDK 무응답 client 누적 테스트 1개 AssertionError를 실제 재현 | 수정 후 GREEN. 최초 테스트에는 SDK 오류 타입 import 실패도 있었으며 실제 AAR 조사 후 공개 Exception만 사용하도록 수정 |
| 최종 Android / 기본 off | `& R:/a01-pleos/gradlew.bat -p R:/a01-pleos --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` | PASS, mock 44/44, vehicle 44/44, 양 debug 빌드. 신규 16개 포함. 기존 AAOS deprecated API 및 native symbol strip 경고는 남음 |
| debug opt-in + release 차단 | 같은 Gradle 경로에서 `--offline '-PenablePleosSignals=true' :app:testVehicleDebugUnitTest :app:assembleVehicleDebug :app:generateVehicleReleaseBuildConfig` | PASS. 생성 BuildConfig debug=true / release=false 확인. release APK는 만들지 않음. 이후 기본 off 명령으로 복원해 debug=false 확인 |
| 웹 회귀 | `node --test web/test/*.test.mjs` | PASS, 79/79. 웹 코드 변경 없음 |
| 종료·지연 콜백 | PleosSignalSessionTest 14개, VehicleRepositoryLifecycleTest 2개. 타입·권한·미지원·응답/이벤트 경쟁·타임아웃·해제 실패 격리·stop/restart·반복 복귀·실차/release 차단 | PASS(JVM 경계 대역), 실제 Binder/Emulator 시험 아님 |
| 정적 안전/계약 | `git diff --check`, 변경 diff와 SDK 호출·매니페스트 검토 | PASS. 차량 제어 호출/새 권한 0, 공유 VehicleSnapshot 모델 변경 0, 주차 게이트 조건 완화 0 |
| Pleos 준비 상태 | `<SDK>/platform-tools/adb.exe devices -l`, `<SDK>/emulator/emulator.exe -list-avds`, 설치 이미지 source.properties | adb 연결 0, AVD `Medium_Phone_API_37.0` 하나, google_apis_playstore 일반 전화 이미지. Pleos 이미지 없음 |
| Virtual Console → 앱 수신 | 미실행: Pleos AVD 부재 | BLOCKED / NOT_VERIFIED. APK 설치·실행, callback 수신, 운전석/단위/권한, 실제 주차 게이트·라이프사이클 검증을 PASS로 표시하지 않음 |
| 실차 | 미실행, 코드에서도 기본 차단 | NOT_VERIFIED |

로컬 JUnit XML 근거: `app/build/test-results/test{Mock,Vehicle}DebugUnitTest/TEST-*.xml`. suite별 mock 결과는 CoreLogic 20, ParkedVoicePreview 4, AaosSignalDecode 4, PleosSignalSession 14, VehicleRepositoryLifecycle 2이며 실패/오류 0. APK/XML/빌드 캐시·SDK 추출물·local.properties는 커밋하지 않았다.

### 규정/안전 결과 (이번 변경 범위에 한함)

공식 출처/적용 구분은 COMPLIANCE_REGISTER.md 색인 및 SDK 노트 A01 절에 연결한다. **PASS는 아래 정적/JVM 범위의 증거이며 공식 승인·실차 인증이 아니다.**

| S-ID | 상태와 증거/제한 | 적용 근거 |
|---|---|---|
| S01 | PASS(정적): 조회/capability/register/unregister/release만 사용. 차량 제어 호출/CONTROL 권한 추가 0 | 플랫폼 P04 + 팀 읽기 전용 |
| S02 | 공개 API 실물·컴파일 PASS, 공모전 원문 재확인/배포 승인 NOT_VERIFIED. O03 URL 재조회 도구 접근 실패, 기존 근거 대장을 참고. 새 SDK·비공개 API·외부 STT 없음, 기본 off | 공모전 O03 / 플랫폼 SDK 소개·권한 |
| S03 | PASS(JVM): stop 무효화·늦은 callback 차단·주차 게이트 회귀. 기존 P+주차브레이크+UX 조건 보존. 실제 주차/복귀 전달 NOT_VERIFIED | 팀 정책, P02/P03 |
| S04 | NOT_VERIFIED(실환경): 화면/UX 정책 변경 없음, 실제 lifecycle·주행 제한에 따른 중지/잠금 시험 못함 | 플랫폼 P01/P03 |
| S05 | NOT_VERIFIED, 실차 Pleos 경로 차단, 주행 중 서비스/음성 허용 주장 없음. SDK는 foreground 시험에만 사용 | 플랫폼 P02/P03 |
| S06 | NOT_APPLICABLE: 음성 재생 변경 없음. 기존 음성 테스트 4개 회귀 PASS | P09, 기존 B 파트 |
| S07 | PASS(JVM): 타입/null/오류 폐기, source/receivedAt 보존, 가상 신호 대체·압력/비상등 추론 없음. 실제 전달 NOT_VERIFIED | 플랫폼 P06 |
| S08 | PASS(JVM): 기존 벨트 착용 완료/브레이크 시작 후 변화 테스트 회귀, 미검증 실차 opt-in 불가. 실환경 미션 NOT_VERIFIED | 팀 정책 / PRD F3 |
| S09 | PASS(변경 정적): 새 저장·업로드·VIN·GPS·원본 수집 없음, opt-in debug callback의 고정 신호 로그만. 전체 앱 저장소 보안 인증 아님 | 플랫폼 P07/P08 |
| S10 | PASS(보고/빌드): 기본 off·실차 차단·출처 유지. 컴파일과 실수신 결과 별도 표기 | 팀 검증 단계 |
| S11 | 외부 자산 도입 NOT_APPLICABLE. 리소스·SDK 미응답 후 회수는 NOT_VERIFIED: 미응답 client 추가 생성 차단, sink 제거, 한 client는 응답/프로세스 종료까지 잔류 가능 | 팀 정책/P10 |
| S12 | NOT_APPLICABLE(제출): debug 검증만 수행. main/제출 SHA/공개 전환/릴리스/APK 업로드 없음 | 공모전 O01/O02 + 팀 정책 |

- G1 PASS: A 담당/Issue #16, 대상·파일·완료 기준과 공유 계약 보존을 계획 및 본 문서에 기록.
- G2 공개 SDK 근거 PASS / 공모전 재확인 NOT_VERIFIED: 공식 URL·캐시 실물·버전·권한 확보. 미확인 영역을 승인으로 간주하지 않음.
- G3 PASS(기본 차단 설계): 최소 기존 조회 권한, 안전 폴백, 실차/release 차단. 초기 read 이후 이벤트가 이전 get보다 우선, 실패 채널은 재시작 전 잠금.
- G4 로컬 단위/빌드 PASS / Emulator·실차 NOT_VERIFIED: 결과·SHA·명령·환경 분리 기록.
- G5 **기본 off의 개발 통합만 조건부 가능**: 독립 리뷰 중요 지적 3개 수정 후 재검토에서 차단급 결함 없음. 실수신 미검증을 완료로 승격하지 않는다. 원격 병합/CI 결과는 아래 후속 기록에서 확인.

남은 차단: Pleos 이미지/장치 준비, Virtual Console 실제 callback→snapshot→미션 수신, SDK 영구 미응답의 실제 자원 회수와 복구. 공개 취소 API가 없어 진행 중 callback 전 release하지 않는 대신 재연결을 차단한다. 이 제한은 출시/실차 사용 전에 해결·검증해야 한다. Issue #16의 실신호 인수 조건은 열린 상태로 남긴다.
