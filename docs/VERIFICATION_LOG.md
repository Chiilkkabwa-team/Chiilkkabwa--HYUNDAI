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

### A01 원격 통합 전 CI (2026-11-13 17:17 KST)

- PR [#17](https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/17), head `aac7ead3bcb77a85f28b06b432feeabd7c6c43f7`, base `dev b66f28e`.
- [Team CI run 43391694682](https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/actions/runs/43391694682): **Web unit tests SUCCESS / Android tests and both debug flavors SUCCESS**. GitHub job/step 상태를 직접 확인했다. 원격 명령은 workflow의 mock JVM 테스트·mock/vehicle debug 빌드이며, 로컬 vehicle JVM 44개 실행은 별도 증거다.
- 안전 검토 및 리뷰 후 **기본 off / debug EMULATOR opt-in 한정 / 실차와 release 차단 상태**의 dev 통합을 진행한다. CI 성공은 Emulator 수신을 검증하지 않는다. Issue #16은 실신호 인수 조건이 남아 자동 종료하지 않는다.
- 통합 전 원격 main은 `c4c15686a30e788143ccc88d8ebaa52500d3187b`. main에 대한 커밋/푸시/PR/병합은 수행하지 않는다. 병합 커밋과 이후 dev CI는 PR 및 GitHub Actions 이력으로 추적한다.

## 2026-11-13 A01 / Issue #16 — Pleos 설치 및 실제 콘솔 신호 검증

- 역할/브랜치: A 담당 요청 / `fix/a01-pleos-emulator-validation`, 기반 dev `e3958dad1bf159479d75e3fc1821969e942b8e59`.
- 시험 코드: `258e4da979e96e2150ce1c5c4ece7465fbb9ba07`. opt-in vehicleDebug APK SHA256 `01F40260E753BD22FC25E63FD2500A7B2FD698439F3C9D66C5E5D7D3C3370C8B`. 후속 커밋은 문서·증거 정리.
- 일시: 2026-11-13 KST 최초18:39 이후, 사용한도 중단 후22:33~22:37 재현. guest 로그는 PST이므로 +17시간이 KST다. 서로 다른 PID/재개 구간을 한 연속 실행으로 표현하지 않는다.
- 환경: Windows, Temurin17, Gradle8.10.2, Intel Arc140T/WHPX, Android Emulator37.2.13, 공식 Pleos Connect2.1.0/API34/x86_64, 신규 AVD `Chiilkkabwa_Pleos_2_1_0_A01`/RAM4096MB/4코어/Cold boot/GPUhost. SDK2.3.2, 실제 Vehicle Service2.3.22(versionCode74), 사용자10, serial emulator-5554. 실차 연결 없음.
- 설치: 공식4,087,751,964 byte ZIP을 기존8개 조각의 길이 다음 바이트부터 HTTP206/Content-Range로 이어받고 결합했다. SHA1 `b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa` 일치 후 추출. source.properties 자동등록 경로와 공식 remotePackage path가 달라 공식 XML의 메타데이터를 로컬 package.xml에 반영했다. 기존 휴대전화 AVD/사용자 프로젝트/SDK 바이너리 미변경.

| 구분 | 실제 명령/입력 | 실제 결과·판정 |
|---|---|---|
| 부팅 | `avdmanager create avd -n Chiilkkabwa_Pleos_2_1_0_A01 -k "system-images;android-34;pleos_car_emulator;x86_64" -d Pleos_Connect_v2_0`, `emulator -avd ... -no-window -no-audio -no-snapshot -memory 4096 -cores 4 -gpu host` | PASS, sys.boot_completed=1, ro.product.device=crp_avd_x86_64, release_connect_v2.1.0 |
| 콘솔 | `adb -s emulator-5554 forward tcp:8088 tcp:8088`, 브라우저 localhost8088 | PASS, HTTP200/DDS ONLINE, 48개 제어. UI를 실제 조작했으며 앱에서 차량 제어 API를 호출하지 않음 |
| APK | `-PenablePleosSignals=true :app:assembleVehicleDebug`, `adb -s emulator-5554 install -r -g ...`, `am start --user 10 -W -n com.chiilkkabwa.app/.MainActivity` | 설치/실행 PASS, USE_MOCK=false/source=EMULATOR. 앱 초기 기어P·UX해제 수신, 주차브레이크false |
| 재현 결함 | 기존 READ_CAR_PEDALS 요청 / `pm grant --user 10 ... READ_CAR_PEDALS` | 권한 정의 없음(Unknown permission), 기존 시작 Activity=GrantPermissionsActivity로 구독 pause/draining 재현. 부여 시도는 실패했고 새 권한을 추가한 것이 아님 |
| 결함 수정 재검증 | PackageManager.getPermissionInfo로 정의 확인 후 없는 권한 요청 생략, 재빌드/설치/같은 am start | PASS, Activity=MainActivity, SDK service 바인딩 및 세 신호 초기 callback/앱 소비 확인. 선택 페달 압력 UNSUPPORTED 유지 |
| 벨트 | 정상 구독 초기 callback, Telltales Seat Belt On/Off, 콘솔 검색 belt | 초기false 수신 PASS. areaId1 getter도false. 전용 체결 컨트롤 없고 경고등은 별개이므로 **true 전환 BLOCKED/NOT_VERIFIED** |
| 브레이크 | Brake Pedal slider Home(0)→End(100)→Home(0) | PASS, callback=false/true/false 및 A01SignalTrace brake=OK/false,true,false |
| 방향지시등 | Blink left→Off→Blink right→Off | PASS, SDK LEFT/OFF/RIGHT/OFF와 앱 LEFT/NONE/RIGHT/NONE |
| 수명 | Blink 시 `CarEvsBlindSpotCameraPreviewActivity`가 앱을 가림, Off 시 복귀 | 해당 시나리오 PASS: unregister/removeRequest/release/clearAllListeners, snapshot DISCONNECTED/null, 복귀 새 초기 callback. 모든 주행/프로세스 재생성 시나리오를 검증한 것은 아님 |
| 안전 게이트 | Parking Brake 경고등On/Off, P/속도0 | 실제parkingBrake=OK/false 유지, gate=PARKING_BRAKE_OFF로 시작 차단 PASS. 실제 PB_ON=true·Ready 및 전체 워밍업 UI는 **BLOCKED**. telltale을 PB_ON으로 대신 사용하지 않음 |
| 화면 | 최초 표시·중단 후 최종 screencap | 최초 정상 화면, 장시간 중단 후 검은 캡처도 관측. 신호 수신 로그는 유효하지만 **장시간 렌더링 안정성 NOT_VERIFIED** |
| 회귀 | `.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` (R: ASCII 별칭 경로), `node --test web/test/*.test.mjs` | PASS: mock44/44, vehicle44/44, 양debug빌드, 웹79/79. 테스트/빌드는 기본off 상태로 복원했고 실기 설치 APK만 opt-in이었다 |
| 실차/릴리스 | 실행·제출 안 함 | NOT_VERIFIED/NOT_APPLICABLE. 실차/release 및 기본off 정책 보존 |

### 보존 증거와 재현 경계

[evidence 안내](evidence/a01-2026-11-13/README.md), [최종 원본 수집 로그](evidence/a01-2026-11-13/console-reproduction.log), [해제 로그](evidence/a01-2026-11-13/sdk-release-reproduction.log), [최초 안전잠금 화면](evidence/a01-2026-11-13/app-gate-locked.png), [장시간 재개 후 검은 캡처](evidence/a01-2026-11-13/app-display-after-resume-black.png).

- 최종 재현 로그의05:35:00(BRAKEtrue),05:35:18(BRAKEfalse),05:35:25(LEFT),05:35:52(OFF),05:36:05(RIGHT),05:36:25(OFF)에 SDK callback과 앱 소비trace가 함께 있다. UI입력과 로그를 매 단계 대조했으며 선택한 앱 태그를 파일로 직접 연속 수집했다. 앞선 부분 로그의 순환 누락을 완전한 원본이라고 주장하지 않는다.
- 증거 파일의 required 값/callback/소비trace 존재를 스크립트로 검사해 PASS. SDK 바이너리·APK·AVD·전체 로그·호스트 계정 경로/개인정보는 커밋하지 않는다.
- 종료 시 P/브레이크0/방향지시등Off, 경고등Off로 복원. 시험 앱·로그 수집·에뮬레이터 프로세스 정상 종료. 설치 AVD 보존, 다음 실행 Cold boot 필요.

### S-ID·Gate 판정 (실측 범위 한정)

- S01 PASS: 읽기 전용 호출·권한 유지, 시뮬레이터 UI 입력만 사용. 제어 API/직접VHAL/새권한 없음.
- S02 공개SDK 실물/실행 PASS. 공모전·스토어 승인을 새로 받은 것은 아님. 공개 설치/콘솔/SDK 링크는03_PLEOS_SDK_NOTES 최신절, COMPLIANCE_REGISTER의O03/P04/P06 참고.
- S03 PASS(잠금·중지/복귀 시나리오), PB_ON=true/안전게이트해제는 NOT_VERIFIED. 조건 완화 없음.
- S04 카메라에 가려질 때 중지 PASS, 장시간 렌더링/전체 주행UX정책은 NOT_VERIFIED.
- S05 NOT_VERIFIED: 실제 주행 중 수집·음성·백그라운드 유지 허용을 주장하지 않음. 실차 경로 차단 유지.
- S06 NOT_APPLICABLE: 음성 변경 없음, 기존 음성테스트 회귀 PASS.
- S07 PASS(관측): bool/enum/출처/실수신과 앱 소비 구분. 미측정 값을 추론하지 않음.
- S08 실차 미션차단/기존 안전벨트 정책 보존 PASS, 실제 체결전환·미션UI완료 NOT_VERIFIED.
- S09 PASS(변경): opt-in debug EMULATOR 고정신호 진단만, 새 저장/업로드/PII 수집 없음. 전체 앱 개인정보 인증 아님.
- S10 PASS(보고): 컴파일/초기수신/상태전환/UI/실차 단계 구분.
- S11 NOT_VERIFIED(성능/화면): GPU 공식 최소목록 밖, 최초 플랫폼 profile/inputmethod 크래시와 장시간 검은 캡처 관측. 신호 기능 결과와 별개. 외부 자산 추가 없음.
- S12 NOT_APPLICABLE: 공식 제출·release APK·main/공개 전환 없음.
- G1/G2: 배정된A01·공식 SDK/설치/권한 증거/독립브랜치 PASS. G3: 기본off/실차차단/오류폴백/잠금 PASS. G4: 위 단위·빌드·실기 범위 PASS, 벨트전환·전체UI 등은 명시적BLOCKED. G5: 원격CI/최신dev 충돌 확인 후 해당 안전한 변경만 통합. Issue#16은 남은 실기 조건이 있어 열린 상태 유지.

## A02 독립 속도 변화 이벤트 엔진 — 2026-11-13 KST

- 담당: 사용자 배정 A 파트 / Codex A02. Worktree: `Chiilkkabwa-A02`, 브랜치 `feature/a02-speed-events`.
- 착수 SHA `e3958dad1bf159479d75e3fc1821969e942b8e59`. 작업 중 A01 PR #18이 dev에 병합되어 **최신 origin/dev `b47e96d9f28437e6ac218ec1140a66e14de480c6`로 fast-forward 후 다시 검증**했다. A02 시험 코드/계약 문서 commit: `11d43ab6435938a1d3323a1b0884a52149b5b220`.
- 범위: 신규 `core/event/SpeedEventEngine.kt`, `SpeedEvents.kt`, 신규 JVM `SpeedEventEngineTest.kt`. 상세 [입출력·설정·오류·연결 계약](A02_SPEED_EVENTS.md). 앱/SDK/차량/주행 UI/음성/저장소에 연결하지 않은 순수 Kotlin 엔진.
- 환경: Windows PowerShell, Temurin JDK `17.0.20.1+1`, Gradle wrapper `8.10.2`, compile SDK 35, 기존 로컬 Android SDK·Gradle 캐시, Node `v24.22.1`. 새 의존성/권한 추가 없음. Gradle은 명령 내 `ANDROID_HOME`만 사용하고 저장소 설정을 바꾸지 않았다.

| 실제 실행/시나리오 | 결과 및 증거 |
|---|---|
| 수정 전 `.\gradlew.bat --offline :app:testMockDebugUnitTest` | PASS, 기존 44개. 이후 테스트부터 신규 A02 파일 포함 |
| 신규 테스트 먼저 실행 | 모델/엔진 미구현으로 컴파일 실패 확인 후 구현. 추가 회귀로 처리 시계 역전·km/h 기본 감소 경계 누락을 실제 재현하고 수정 |
| 최종 `.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` | **PASS**, 최신 dev b47e96d 기반. mock 78/78, vehicle 78/78(기존 44 + A02 34), 두 debug 빌드. 최종 명령 BUILD SUCCESSFUL / 18s |
| `node --test web/test/*.test.mjs` | **PASS 79/79**. 웹 변경 없음. 새 dev의 A01 변경에도 웹 파일은 동일 |
| JUnit XML | `app/build/test-results/testMockDebugUnitTest/TEST-*.xml`, vehicle 동일 경로. 각 flavor CoreLogic 20 / ParkedVoicePreview 4 / AaosSignalDecode 4 / PleosSignalSession 14 / VehicleRepositoryLifecycle 2 / SpeedEventEngine 34, failures/errors 0 |
| A02 테스트 범위 | 첫 표본, 정상·일정 속도, 증가/감소 임계값 및 바로 아래, m/s↔km/h 혼합, 원본 근거·ID·출처, 모든 non-OK 상태, 누락·null·NaN/Infinity·음수/상한, freshness/미래·시각 역전·Long 경계, 최소/최대 간격, 이상 변화, 지속 구간·히스테리시스·쿨다운 경계/소급 방지, 출처·세션 격리, reset 우회 방지, 설정 오류, 수치 정밀도 |
| 금지 파일 비교 | 최신 origin/dev와 **7개 모두 내용 변경 없음**: PleosVehicleSource, AaosVehicleSource, VehicleSignals, Signals, ChiilViewModel, AndroidManifest, app/build.gradle.kts. A01 갱신분은 dev에서 상속했으며 A02 수정이 아님 |
| 정적 검토 | `git diff --check`/staged 검사 PASS. 신규 엔진의 Android/Pleos SDK/제어 API import·호출 0, 새 권한/의존성 0. 기존 공유 VehicleSnapshot/이벤트 소비자 변경 0 |
| A02 에뮬레이터/실차/SDK 신호 | **NOT_VERIFIED / NOT_RUN**. 테스트의 EMULATOR/VEHICLE 출처 값은 provenance 전달용 합성 입력이며 실수신 증거가 아님. A01의 별도 에뮬레이터 검증 결과를 A02 PASS로 대체하지 않는다 |
| 원격 CI·통합 | push CI **PASS** ([run 43438750304](https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/actions/runs/43438750304): Web unit tests 및 Android tests/both debug flavors SUCCESS). PR [#19](https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/19), base=dev 생성. PR CI 별도 실행 중, dev 병합은 아직 안 함. |

기존 AAOS deprecated API 및 native symbol strip 경고는 앞선 전체 빌드에서 확인되었으며 A02 코드 오류가 아니다. APK/XML/빌드 캐시·개인 설정은 커밋하지 않는다.

### 규정 게이트 (A02 변경 범위)

| ID | 상태/근거 |
|---|---|
| S01 | PASS(정적): 차량 제어/SDK 호출 없음. 독립 계산만 수행 |
| S02 | NOT_APPLICABLE: 새 SDK/비공개 API/권한/외부 STT 도입 없음. 공식 SDK 가용성을 새로 인증하지 않음 |
| S03/S04 | NOT_APPLICABLE: 주차 게이트·UX·화면·입력 변경 없음. 속도 0으로 주차를 판단하지 않음 |
| S05 | NOT_VERIFIED: 주행 중 수집·생명주기·기록 허용을 검증하지 않음. 엔진의 실신호·앱 경로는 연결하지 않음 |
| S06 | NOT_APPLICABLE: 음성 재생·발화 의사결정 변경 없음 |
| S07 | PASS(JVM): 숫자/시간/단위/상태 검사, 원본 표본·출처·계산 근거 보존. 브레이크 조작·위험·주변 차량·운전 능력 추론 없음. COMPLIANCE_REGISTER P06 및 팀 정책 연결 |
| S08 | NOT_APPLICABLE: 차량 미션/페달 조작 요청 없음 |
| S09 | PASS(정적): 새 저장/업로드/개인정보·정밀 위치 수집 없음. 이전 표본·고정 크기 중복 상태만 메모리에 보유. 전체 앱 보안 인증은 아님 |
| S10 | PASS(JVM/문서): MOCK/EMULATOR/VEHICLE 출처 전달·혼합 구간 차단. 단위 테스트·빌드·실수신 검증을 분리 |
| S11 | 외부 자산·의존성 도입 NOT_APPLICABLE. 실제 장비 CPU/RAM 성능은 NOT_VERIFIED |
| S12 | NOT_APPLICABLE: 제출·공개 전환·릴리스·APK 업로드 없음. main 참조 변경 없음 |

- G1 PASS: 사용자 A02 배정, 수정 범위·완료 기준·보호 파일 명시.
- G2 PASS(코어 범위): SAFETY_RULES/COMPLIANCE_REGISTER/분업안 검토, 기존 SignalStatus/SignalSource만 사용. 개발 임계값을 공식/실차 안전 기준으로 표현하지 않음.
- G3 PASS(격리): 검증되지 않은 값은 버리고 원인별 판정 불가 반환. 실차/UI 경로 미연결. A01/B/C 통합 조건 별도 문서화.
- G4 PASS(로컬): 관련 신규·회귀 테스트와 두 debug 빌드 성공. SDK/Emulator/실차와 원격 CI는 미검증.
- G5 **통합 준비**: 최신 dev 기반·보호 파일 무변경·검증 성공. `dev`/`main` 병합은 이번에 수행하지 않는다.

남은 일: 실제 속도 공급의 지원·권한·단위·획득 시각 검증, 단일 시계 계약/누락 timeout/세션 수명 연결, 실제 주기·측정 노이즈에 맞는 임계값·필터 보정, B/C eventId/시간 매핑과 정책 검증. 현재 결과는 두 표본 사이의 평균 속도 변화이며 브레이크 사용이나 도로 위험을 확정하지 않는다.

## 2026-11-13 A04 — 웹 가상 교차로 판정과 시연 회귀

- 역할/격리: 사용자 배정 A04, 독립 Worktree `feature/a04-intersection-turns`, 기반 `1140c7487f9e5cde39be1ecc41e7caef8d25e145`. 코드 `def35ebb897747d4a17901729f52a04cc4ee92c0`. 문서 후속 커밋은 실행 코드 변경 없음.
- 환경/일시: Windows, Node24.22.1, Chrome157.0.8103.51/Playwright headless. 2026-11-13 KST 약23:14~23:34. **웹 Mock만** 실행. Android 신호·A02 엔진·Manifest/Gradle·음성 우선순위 정책 무변경.
- [기존 분석·판정·공유 계약·안전 한계](A04_INTERSECTION_TURNS.md), [실제 브라우저 출력](evidence/a04-2026-11-13/browser-results.json). 화면은 로컬 저장소 밖 `output/a04`에 보존.

| 실행 | 결과 | 판정 |
|---|---|---|
| 변경 전 `node --test web/test/*.test.mjs` | 기존79/79 | PASS(기준) |
| 추가 교차로 테스트를 기존 코드에 실행 | 초기20개 중18실패, 반대 신호·근거/출처·저속/중복 등 재현 | 결함 확인 |
| 최종 `node --test web/test/*.test.mjs` | 105/105, 실패0/건너뜀0. A04 전용26개 포함 | PASS |
| `node --check web/app.js`, `node --check web/test/junction.browser.cjs`, `git diff --check` | 구문·공백 오류 없음 | PASS |
| `NODE_PATH=<기존 Playwright 경로> node web/test/junction.browser.cjs` | 독립 Chrome/임시 localhost, 1440×900 및390×900의 진행/깜빡이 독립 입력·화면 넘침 없음·2D Mock 문구 확인 | PASS |
| 기존 평가·강습 자동 시연3× | 첫 실행: 감속이 늦어 목표 교차로를 직진 통과, 정상 회전 검사 실패. 먼 교차로 선택/150m 전 감속/저속 유지/대기 보완 후 각각 TURN_GOOD 및 주차 후 report 도달 | 수정 후 PASS |
| 기존 연습 자동 시연3× | 복기 저장 후 goal 화면 도달 | PASS |
| 브라우저 오류 수집 | 위5시나리오 모두 pageerror/console error0, 요청 실패0 | PASS |
| 실제 음성·실차/Emulator | 테스트 브라우저 음성/효과음 끔. Android/실차 실행 안 함 | NOT_VERIFIED/NOT_APPLICABLE |

정상 좌/우·무신호 직진·반대 신호 양방향·무신호 회전·구간/차로 불일치·차체 경계·과속25km/h 경계·정지/저속 접근·중복·다음 교차로·프레임 건너뜀·저속 차로 변경 오인 방지·진입점 보간·JSON 복기 근거/Mock 출처 보존을 검사했다. 시뮬레이션 `t`는 경과 초, 진입 속도/차로는 2D 프레임 선형 보간이다. 법규/실차 안전 속도·실제 위험 판단으로 표현하지 않는다.

- **S07 PASS / S10 PASS(웹 Mock 한정):** 방향·시각·단위·가상 근거와 출처 보존, 콘솔/리포트/문서에 2D Mock 표시. 실제 회전/차선 위험 감지 주장 없음. 근거: SAFETY_RULES S07/S10, COMPLIANCE_REGISTER P06; 공식 SDK/차량 지원 승인을 새로 받은 것이 아님.
- S01/S09 PASS(변경 정적): 차량 제어/권한/SDK·외부 전송/새 개인정보 수집 없음. 나머지 S-ID의 변경 범위와 NOT_APPLICABLE 사유는 A04 문서 참조. 제품 전체 규정/성능 인증 아님.
- G1~G4 PASS: 배정 범위/독립 브랜치·공유 계약·Mock 설계·단위/브라우저 실행 증거 확보. G5는 최신 origin/dev와 충돌 및 CI 확인 후 웹 변경만 통합. `main=c4c15686a30e788143ccc88d8ebaa52500d3187b` 보존.
- 남은 한계: 3D/실제 회전 물리·보행자·신호등 통행권·실차 차로 위험은 범위 밖. 시연 점수는 가상 교통에 따라 달라짐. 브라우저 검사는 Chrome/Playwright 설치가 필요하며 기존 CI의 Node 단위 검사와 구분한다.

## 2026-11-13 KST23:20~23:33 — A01 신뢰성 후속 / Issue #16

- 브랜치 `fix/a01-signal-reliability`, 기반/시험 코드 `1140c7487f9e5cde39be1ecc41e7caef8d25e145`. 앱 코드 변경 없음. [결과·공식 근거·재현 조건](A01_RELIABILITY_FOLLOWUP.md), [증거 목록](evidence/a01-2026-11-13-reliability/README.md).
- Windows/Arc140T/WHPX, Pleos2.1.0 API34, Emulator37.2.13, Vehicle SDK2.3.2/Service2.3.22, 사용자10, emulator-5554, 기존 전용 AVD Cold boot/GPUhost/4코어/RAM4096MB. 실차 없음.
- 최신 opt-in APK SHA256 `4BCDFF4F362E018AD22273CB96040E1443531EFCDA7D9F1635C32FD7C664FDC5`. `-PenablePleosSignals=true :app:assembleVehicleDebug` 별도 실행/설치 성공. 초기 경고등 비교 PID24255/30692는 PR18 APK, 중단/재개 PID17157은 최신 dev APK. A01 코드 동일.

| 시험 | 실제 실행 | 결과 |
|---|---|---|
| 회귀 | Temurin17/Gradle8.10.2, R: ASCII 별칭 경로에서 `.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` | mock78/78,vehicle78/78,양debug빌드 PASS. A02 코어 테스트 포함 |
| 웹 | `node --test web/test/*.test.mjs` | 79/79 PASS |
| 벨트 | 전체48개 콘솔 카드/공식 가이드 확인, belt 검색 No matching controls, Seat Belt telltale On 후 새 연결 | SDK BELT=false 및 앱 OK/false. 실제 체결true 전환 BLOCKED/NOT_VERIFIED |
| 주차 브레이크 | Parking Brake와 Electronic Parking Brake 경고등 각각 On/Off, 앱 새 구독 | 실제 parkingBrake=OK/false,gear=PARK,ux=false. PARKING_BRAKE_ON=true 전환 BLOCKED/NOT_VERIFIED |
| 전체 워밍업 | 실제 주차 게이트 상태와 화면 확인 | PARKING_BRAKE_OFF 잠금, 시작 불가. BLOCKED. 경고등·속도0·Mock/미지원 값 대체 없음 |
| 실제 중단 | HOME은 pause되지 않아 Settings Activity를 `am start --user 10 -W -a android.settings.SETTINGS`로 실행 | 앱 visibility=8, snapshot 전체 DISCONNECTED/null. 미션 성공 없음 |
| 325.96초 후 복귀 | 동일 MainActivity `am start --user 10 -W -n com.chiilkkabwa.app/.MainActivity`, 중단 중 콘솔Brake100% | 같은 PID17157/HOT,Awake/Display ON,visibility=0,화면 표시. 새 callback/app brake=true; 콘솔0% 후 false. 한정 시나리오 PASS |
| 화면 전원 대조 | `input keyevent KEYCODE_SLEEP`, 안정 상태 진단/screencap, `KEYCODE_WAKEUP`, 같은 앱 실행 | Asleep/Display OFF와 RGB전체0 재현. Wake 후 같은 PID/Awake/ON/화면·신호 복구 PASS |
| 기존 검은 화면 원인 | 기존PNG와 이번PNG RGB 비교, power/display/window/gfxinfo/호스트 로그 대조 | 기존도 RGB전체0. 당시 전원·Surface 기록 없어 원인 확정 NOT_VERIFIED. Awake 검은 화면은 이번 시험 미재현. GPU A/B·더 긴 중단·호스트절전 미검증 |

호스트 ColorBuffer 및 EGL 오류는 정상 화면에서도 나타났고 Settings에도 EGL 경고가 있었다. 이 문자열을 원인 확정이나 앱 수정 근거로 삼지 않았다. 화면 데이터·시계·PID·경과 시간은 실제 수집 자료이며 편집한 이미지로 대체하지 않았다. 단계별 상태만 발췌하고 SDK/APK/AVD/전체 로그/위치·개인정보는 커밋하지 않는다. 콘솔 경고등Off,브레이크0,기어P를 유지한 뒤 앱·우리 로그 수집·AVD 종료,기존 데이터 보존.

규정/게이트(이번 변경 범위): S01 PASS(제어API/권한/VHAL 추가0), S02 PASS(기존공개API·공식콘솔 입력만/공식 승인 새 주장 없음), S03 PASS(잠금·중단 폐기/게이트해제는 NOT_VERIFIED), S04 중단/복귀 한정PASS·전체주행정책NOT_VERIFIED, S05 NOT_VERIFIED(실차·주행·음성차단 유지), S06 NOT_APPLICABLE(음성 변경 없음), S07 PASS(실측·출처·불명 구분), S08 잠금유지PASS·전체미션NOT_VERIFIED, S09 PASS(합성 고정신호·필요 진단만/계정·위치·비밀정보 배제), S10 PASS(빌드·실수신·화면·실차 분리), S11 NOT_VERIFIED(장시간/GPU/성능 미확정), S12 NOT_APPLICABLE(제출·릴리스 없음/main 보존). 공식 근거 범위는 SAFETY_RULES/COMPLIANCE_REGISTER와 상세 보고서 링크 참조. G1/G2/G3 PASS(배정·공식근거·제한 유지), G4 위 표 범위만PASS 및 나머지 명시적BLOCKED, G5 문서·증거만 통합하며 실차/미션 활성화 없음. Issue #16은 열린 상태 유지.

### A04 최신 dev 통합 전 충돌 확인

PR #20 최초 CI에서 웹 및 Android 테스트/양debug빌드 SUCCESS를 확인했다. 병합 직전 A01 문서 PR #21이 dev `1e690022d725a1f65ea171f55194e8c68200c989`로 먼저 들어와 두 검증 대장의 끝부분이 충돌했다. 양쪽 A01/A04 절을 모두 보존해 해결했다. 웹·Android 실행 코드 차이는 없으며 웹105/105를 다시 확인했다. 갱신 head의 CI와 최종 병합 상태는 PR #20/Actions 이력으로 추적한다.


## 2026-11-13 C01 / E3 — 복기 게이트 잠금 누락 수정

- 담당 역할: C(복기·기록·UI)
- 브랜치 / 기준: `fix/c01-review-gate` ← `origin/dev` 64d25b1
- 환경: Linux 클라우드 컨테이너, JDK 21, Gradle 8.10.2, Android SDK platforms;android-35, Playwright Chromium. 에뮬레이터 없음
- 문제: `REVIEW` 단계에서 주행이 아닌 잠금(주차 브레이크 해제, 신호 UNKNOWN/STALE/DISCONNECTED/UNSUPPORTED)이 오면 상태를 그대로 둬서 복기·장면 재현 창이 열린 채 남았다(S03 위반)
- 수정: 웹 `core.js onGate`와 Android `SessionReducer.onGate`의 REVIEW 분기. 주행이 아닌 잠금도 `lockReason`을 세워 잠그고, 단계는 REVIEW로 두어 작성 중 복기를 지킨다. 다시 주차가 확인되면 `lockReason`만 지워 같은 복기로 돌아온다. 주행 제한·기어 변경은 기존대로 DRIVING_LOCK. 화면: 웹 `screenKey`가 멈춤 화면으로, 잠기는 순간 장면 재현 창(`RP.closePip`)과 오버레이를 닫는다. Android `AppRoot`는 잠긴 REVIEW에서 GateScreen
- 게이트 조건 완화: 없음

| 시험 | 명령/증거 | 결과 |
|---|---|---|
| 웹 단위 | `node --test web/test/*.test.mjs` (신규 4개: 브레이크 해제 잠금, UNKNOWN/DISCONNECTED/STALE/UNSUPPORTED 잠금, 주행 제한→DRIVING_LOCK, 재주차 시 같은 복기 복귀) | PASS 109/109 |
| Android 단위 + 빌드 | `./gradlew :app:testMockDebugUnitTest :app:assembleMockDebug` (신규 `review_locks_on_non_driving_lock_and_resumes`) | PASS 79/79, mock debug 빌드 |
| 브라우저 시나리오 | Playwright: 연습 주행(속도 0→35→0) → 주차 → 복기 → 사건 점으로 장면 재현 창 열기 → 콘솔 주차 브레이크 해제 → 다시 걸기 | PASS: 해제 즉시 '복기를 잠시 닫았어요' 멈춤 화면, 장면 재현 창 0개, 재주차 시 복기 화면 복귀. 콘솔 오류 0 |
| 에뮬레이터 / 실차 | 미실행(Pleos AVD 없음) | NOT_VERIFIED |

규정: **S03 PASS(웹·JVM 단위 범위)**, S04 PASS(웹 화면 범위: 잠기면 장면 재현 즉시 종료) / 에뮬레이터 NOT_VERIFIED, S07·S10 NOT_APPLICABLE(출처 표기 변경 없음)


## 2026-11-14 C04 — 온보딩 문구·잠금 이유·하단 바 안내

- 담당 역할: C(복기·기록·UI)
- 브랜치 / 기준: `feature/c04-onboarding-lock-menu` ← `origin/dev` a27bc5a
- 환경: Linux 클라우드 컨테이너, Node 22, Playwright Chromium. Android 코드 변경 없음
- 근거: 팀 채팅 피드백 5·6·7번([FEEDBACK_C_PART_2026-11-13.md](FEEDBACK_C_PART_2026-11-13.md))
- 변경:
  - 온보딩 5장면 문구를 해요체로 통일, 구호식 문장 정리, 하단 바가 장식이라는 설명 추가. `INTRO_V` 3으로 올려 한 번 더 보이게
  - 머리말 '소개'·'사용법'(웹 체험판 설명, 차 화면 밖)은 주행 중에도 열림. 열린 동안 가상 주행 시간 정지 + "실제 차에서는 주행 중에 뜨지 않아요" 안내
  - '내 기록'·'나'(앱 안의 창)는 주행 중 잠금 유지. `aria-disabled`·이유 설명. 주행 중 누른 것이 주차 순간 갑자기 뜨던 버그 수정
  - 주행 잠금 화면에 "잠깐 멈춘 건 주차가 아니에요" 정적 안내
  - 하단 바 항목을 버튼으로(키보드 포함), 누르면 데모 미지원 이유를 하단 바 위 말풍선으로
- 게이트 조건 완화: 없음(속도 0은 여전히 주차 아님)

| 시험 | 명령/증거 | 결과 |
|---|---|---|
| 웹 단위 | `node --test web/test/*.test.mjs` | PASS 109/109 |
| 브라우저 시나리오 | Playwright: 첫 방문 온보딩 문구 / 하단 바 내비·홈·공조 누르기 / 주행 중 '내 기록'(잠김)·'사용법'(열림+정지 안내) / 주차 후 지난 클릭이 뜨지 않는지 | PASS: 조사 '내비는·홈은' 정상(처음 '내비은' 발견 후 수정), 주행 중 내 기록 모달 0, 사용법 모달 1, 주차 후 모달 0. 콘솔 오류 0 |
| 접근성 | axe-core WCAG 2 A/AA, 라이트·다크 각각 | PASS 위반 0 |
| 드라이빙 뷰 겹침 | `web/test/overlap.check.js` 30조합 | PASS 0건 |
| Android / 에뮬레이터 | 해당 없음(웹 UI 변경) | NOT_APPLICABLE |

규정: S03 PASS(조건 완화 없음, 웹), S04 PASS(웹: 앱 안 창은 주행 중 잠금 유지, 체험판 안내창은 차 화면 밖이며 '실제 차에서는 뜨지 않음' 명시), S10 PASS(데모 미지원을 명시), S01 NOT_APPLICABLE(차량 제어 없음)


## 2026-11-14 C 파트 마무리 — 온보딩·코치 마크·내비식 주행 정보·내 기록

- 담당 역할: C(복기·기록·UI) / 브랜치 `feature/c-ux-polish` ← `origin/dev` e5f4179
- 환경: Linux 클라우드 컨테이너, Node 22, Playwright Chromium. Android 코드 변경 없음
- 변경: 온보딩 단계 숫자·다음 장면 이름·넘김 방향 움직임·터치 좌우 넘기기 / 온보딩 끝 코치 마크 / 주행 정보 내비식 배치(DOM 순서 = 보이는 순서) / 내 기록 '다음 목표'·'최근 7일'
- 게이트·주행 잠금 동작 변경: 없음

| 시험 | 명령/증거 | 결과 |
|---|---|---|
| 웹 단위 | `node --test web/test/*.test.mjs` | PASS 109/109 |
| 브라우저 | Playwright: 온보딩 1/5→5/5, '다음' 버튼 다음 장면 이름, 마지막 '직접 해 보기' → 코치 마크가 '연습 시작'을 짚음 → 클릭 시 사라짐 / 내 기록에 다음 목표·최근 7일(3일) / 평가 모드 주행 화면 캡처 | PASS, 콘솔 오류 0 |
| 접근성 | axe-core WCAG 2 A/AA 라이트·다크 | PASS 위반 0 |
| 드라이빙 뷰 겹침 | `web/test/overlap.check.js` 30조합(새 배치 포함) | PASS 0건 |
| Android / 에뮬레이터 | 해당 없음(웹 UI) | NOT_APPLICABLE |

규정: S03·S04 PASS(웹, 잠금 조건 변경 없음, 코치 마크는 정차 중 온보딩 직후에만), S09 PASS(기록은 브라우저 저장소만), S10 PASS(웹 체험판 주석 유지), S12 NOT_APPLICABLE

## 2026-11-14 A02 — AAOS 속도 관측과 이벤트 브리지

- 담당/브랜치: Codex1 / `feature/a02-aaos-speed-bridge`, 전용 managed worktree `chiilkkabwa-codex1-a02-speed/a01-pleos`. 착수 기반 `1acc54036c5660ff05d572dd904d250762f0dad5`.
- 구현 d3b19c1, 리뷰/플래그 수정 및 실측 코드 `8166fe443b4bd479b12c978be9928c1585cb14c3`. [공개 API·데이터/시계·B/C 계약·S-ID/Gate](A02_SPEED_BRIDGE.md), [원본 증거 구간](evidence/a02-2026-11-14/README.md).
- Temurin17/Gradle8.10.2/Android SDK35 stub, 실제 Pleos2.1.0 API34/Service2.3.22/Emulator37.2.13/Arc140T/WHPX/ranchu/user10/emulator-5554. 실차 없음. 실기 APK SHA256 `882487C30FC4F72E485C24AD596FF90BA481BFC036E2AC6DAA82417DA75386D6`, A02 opt-in=true/A01=false/USE_MOCK=false.

| 시험 | 실행·증거 | 실제 결과 |
|---|---|---|
| 기준 단위 | 최신 dev1acc540에서 두 flavor JVM | 각79/79 PASS |
| TDD | 순수 계약 부재 및 integration 부재 RED → 구현 GREEN. 독립 리뷰의 inline receipt/미래/등록대기 stale/ns역전 네 회귀 RED→GREEN | 신규28개 추가, 기존 engine 정책/테스트 유지 |
| 최종 로컬 | `.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` | 코드8166fe4에서 각107/107, 양 debug 빌드 PASS |
| 웹 | `node --test web/test/*.test.mjs` | 기반 dev 웹109/109 PASS. 웹 파일 수정 없음 |
| API/stub | javap android.car.jar의 PERF_VEHICLE_SPEED/Car.PERMISSION_SPEED/createCar(...lifecycle)/config/register/unregister/getTimestamp/getStatus/getValue | 실물·컴파일 PASS, 하드코딩 ID/제어/VHAL/GPS0 |
| 권한 정의/부여 | dumpsys package permissions, user10 pm grant CAR_SPEED | com.android.car.updatable의 dangerous 정의 및 exit0/granted=true. 단순 pm list 필터 누락을 미지원으로 오진하지 않음 |
| Emulator 속도 | 콘솔 D/ACCEL/BRAKE, A02SpeedTrace observation 및 consumed | 0→증가→감소→0, Baseline/Evaluated와 RAPID_SPEED_INCREASE/DECREASE 실수신 파이프라인 PASS. OK1733개에 미래propertyNs0개 |
| R/lifecycle | R 카메라→P 복귀 | 같은PID에서 DISCONNECTED/null 후 새 sessionId/Baseline PASS. 실제 후진 음수 수신은 카메라 중단으로 NOT_VERIFIED |
| 권한 거부 | pm revoke user10 후 앱 새 실행 | PERMISSION_DENIED/null 및 Rejected PASS. OS권한창 Allow/Deny 전체조작/자동재부여 복구는 NOT_VERIFIED |
| 플래그 | opt-in 소스true/bytecodefalse 증분 잔류 재현 → debug Boolean.parseBoolean/GETSTATIC, 기본OFF 재빌드/재설치 | 실제 opt-in 콜백 PASS, 기본복원 새PID1349 A02 trace0 bytes. release 생성 상수false. A01 플래그 무변경 |
| 실차/주행/정확성 | 활성화·실차 실행 안 함 | NOT_VERIFIED. 양자화된 속도 단계와 짧은dt가 이벤트를 유발하므로 임계값/센서 보정 검증 전 B/C·실사용에 연결하지 않음 |

JVM 검증은 정상 증가/감소·정속·단위·후진·null/타입/NaN/Infinity·권한정의부재/거부·property부재·서비스/factory/등록실패·타임아웃·출처/세션·늦은콜백·중복/쿨다운/히스테리시스·정밀ns/등록중시각 보존을 포함한다. 실제 시험과 fake client 시험을 상호 대체하지 않는다.

독립 리뷰의 ns 역전 방어 항목은 이 작업의 명시적 시간 역전 차단 계약에 따라 중요 수정으로 처리했다. 잘못된 시계에서 표본을 거부하는 편을 택했으며 임계값이나 엔진 판정을 완화하지 않았다. 공개 API를 이용한 앱 수신까지만 성공이며 실제 급출발·충돌·차간거리 위험을 확정하지 않는다.

종료: 공식 콘솔P/0km/h 복원, 임시 사용자10 CAR_SPEED 철회, 기본OFF APK로 복원, 앱/우리adb로그수집/우리AVD 종료. SDK/APK/AVD/전체 장치 로그·실차/위치/계정정보는 커밋하지 않았다. main은 기존 c4c1568 보존. 최신 dev 통합 회귀/PR/CI는 후속 기록과 Actions 이력으로 추적한다.
## 2026-11-14 B01 — 평가·강습 리포트의 발화 표시

- 브랜치 `fix/b01-report-voice-events` / 기준 `dev` `1acc540`. B01의 우선순위·비상등·발화 완료 필터는 PR #11에서 반영됨.
- `web/app.js`의 가상 주행 원본 사건에 같은 ID를 붙여 평가·강습의 사건 목록과 속도 시간표가 완료한 발화만 찾아 표시한다. 멘토 모드의 기본 음성 끔 문구를 실제 설정과 맞췄다.
- Windows / Node 24.18.1: `node --check web/app.js`, `node --test web/test/*.test.mjs` 109/109 PASS. 기존 E4~E6, 취소·시간 초과·늦은 콜백을 포함한다.
- 브라우저 실행·실제 청취는 NOT_VERIFIED. S06은 웹 Mock 로직에서만 PASS, S07·S10은 가상 출처 유지. 차량 연동 및 주행 음성(S05)은 NOT_VERIFIED. Android·권한·외부 음원 변경 없음(S01·S02·S11 해당 없음). 공식 근거는 [SAFETY_RULES.md](SAFETY_RULES.md)의 P02·P09 참조.


## 2026-11-14 B02 — Android 코칭 판단·발화 결과 코어

- 담당 Codex2 / feature/b02-coaching-policy-core / Chiilkkabwa-Codex2-B02-Coach.
- 최초 origin/dev 1acc54036c5660ff05d572dd904d250762f0dad5. 통합 준비 중 B01 PR#22의77af260015981305e7b24891bd76afa29a8e961c를 fast-forward로 반영. 다른 팀원 Worktree/미커밋 A03 조사 문서는 보존.
- 환경: Windows PowerShell, Temurin17.0.20,Gradle8.10.2,Android SDK35,Node24.18.1. 공식 SDK/의존성/권한 추가 없음. 기존캐시 --offline.
- 변경: 독립 Kotlin3파일+JVM 테스트1파일, B02 계약 문서. 기존 차량/A02/웹/미리듣기/저장소/UI/Manifest/Gradle 변경0. [상세 설계·계약](B02_COACHING_POLICY.md).
- TDD: 변경전 Mock79 PASS → 신규 타입 부재로 테스트 컴파일 실패 → 구현 후27개 신규 PASS. 자체 리뷰 회귀 추가 후31개 중1개 FAIL(극단적인 유한 가속도 통과) → finite뿐 아니라 개발 범위 검사 추가 →31/31 PASS. 독립 동료/에이전트 리뷰를 수행한 것으로 주장하지 않음.

| 검증 | 실제 명령/결과 |
|---|---|
| 두 JVM | .\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest — mock110/110,vehicle110/110 PASS,실패/오류0 |
| 두 debug빌드 | 같은 실행의 :app:assembleMockDebug :app:assembleVehicleDebug — BUILD SUCCESSFUL |
| 기존 미리듣기 | 양 JVM ParkedVoicePreviewTest 기존4개 PASS,음성 구현 무변경 |
| B02 | CoachingDecisionEngineTest 양 플레이버31/31. 목표·적격성 우선/우선순위/800ms/45초/5회/동일2회/멘토/중복/세션·시간·신선도·상태·단위·이상치/실패·늦은완료/출처차단/A02변환/칭찬/메모리 경계 포함 |
| 웹 음성 포함 회귀 | node --test web/test/*.test.mjs —109/109 PASS |
| 정적 범위 | git diff --check 및 통합기준 dev 대비 보호파일 diff0. 차량제어·TTS실행·네트워크·새저장 경로 추가0 |
| 실제 Android/Pleos/실차 음성 | 미실행 NOT_VERIFIED. 합성 COMPLETED는 기기 utterance 완료가 아님 |

남은 검증: 실제2.5초/오디오 포커스·내비양보/DO·주행 수명/정차 게이트 전달/프로세스 재생성·동일주행 횟수보존/C01저장·프로필 정책. 기존 AAOS deprecated 경고는 기준과 동일하며 보호파일을 수정하지 않았다.

규정: S01/S02 PASS(변경 정적:제어API·새SDK·권한·외부STT0), S03 PASS(JVM:defaultoff/정차Mock허용·실시간허용상실시stop계약), S04 PASS(정적:UI무변경)·실환경NOT_VERIFIED, **S05 NOT_VERIFIED·실제주행음성미연결/live출처BLOCKED**, S06 PASS(JVM:빈도/양보실패폐기/길이추정)·실포커스/실측NOT_VERIFIED, S07 PASS(원본근거/단위/상태·출처보존·추론금지), S09 PASS(정적:새저장/위치/서버0·메모리상한), S10 PASS(합성/빌드/실음성분리). S08/S11/S12 NOT_APPLICABLE(미션·외부자산·제출없음/main보존). 공식근거는 SAFETY_RULES/COMPLIANCE_REGISTER O03,P02,P03,P06,P07,P09 및 B02문서 링크; 새로운 플랫폼허가/공식승인 주장 없음.

G1/G2 PASS(배정·범위·근거·계약),G3 PASS(기본차단·순수코어·오류폐기),G4 위표범위만PASS/실환경NOT_VERIFIED,G5 차단된 독립 코어만 통합. 병합 전 최신dev 반영/관련검증 재실행과 원격CI 확인을 수행하며 최종 PR·병합SHA·CI는 후속 기록 또는 PR이력으로 추적.
## 2026-11-14 B02·B03 — 기존 구현 재검증

- B02(PR #12): Windows Android Studio Kotlin 컴파일러에서 `ParkedVoicePreviewTest` 4개를 JVM으로 실행했다(로컬 JUnit 호환 단언 사용). 주차 조건/포커스 거부, 포커스 상실/취소, 늦은 완료 콜백, 재생 시작 실패 테스트 PASS. `AndroidPreviewAudio.kt`와 컨트롤러는 설치된 Android API 37 스텁에 대해 컴파일했다. 전체 Gradle 단위 테스트와 두 debug 빌드는 PR #22의 Android CI에서 PASS. 이 PC에는 Pleos AVD가 없으며 Pleos SDK 실행·재생·포커스 상호작용은 NOT_VERIFIED(S02·S05·S06).
- B03(PR #13): Node 24.18.1에서 로컬 한국어 음성 우선 선택/미지원 테스트 PASS. 회전 두 문구·칭찬의 추정 길이는 1.60~1.75초. 실제 재생 2.5초 실측과 청취 품질은 NOT_VERIFIED(S06). 외부 음원은 사용하지 않았다(S11 해당 없음).
- 공식 근거: [주행 규제](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy), [오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development), [Pleos TTS SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro).

## 2026-11-14 B04 — 웹 도움말·운전 노트의 주차 조건과 벨트 설명

- 브랜치 `fix/b04-parked-learning` / 기준 `dev` `77af260`. 도움말과 노트는 열 때와 주차 상태를 다시 확인할 때 모두 주차 게이트를 적용한다. 콘솔의 주차 브레이크 미지원 옵션과 관계없이 학습 창에는 기어 P·주차 브레이크 ON·UX 제한 해제가 필요하다.
- Windows / Node 24.18.1: `node --check web/app.js`, `node --test web/test/*.test.mjs` 110/110 PASS. 기어 D, 브레이크 해제/미지원, 제한 활성, 신호 끊김/불명과 벨트 미지원 안내를 검증했다. 브라우저의 로컬 시험 페이지 연결이 시간 초과되어 UI 실측은 NOT_VERIFIED.
- S03 주차 조건 및 S08 벨트 안내는 웹 단위 범위 PASS, S04 UI 전환은 NOT_VERIFIED. S10 웹 Mock 범위. Android·권한·외부 자산 변경 없음(S01·S02·S11 해당 없음). S03·S04 근거는 [SAFETY_RULES.md](SAFETY_RULES.md)의 P01·P02 참조.

### B02 최신 dev 반영 후 재검증

- 기능 코드 커밋50a95d9898e8b013be508d652a28b2a57da07eed. dev b758c2b4ae7d9711608fb71fb7dfbd82f9ab9b5b(B01/B04)를 반영한 시험HEAD ff9e4d8ea5781d9c288dca88cefac18c0c0bd9fb.
- 충돌은 VERIFICATION_LOG 말미의 append 충돌1개. B02 신규·기존B02/B03·B04 기록을 모두 보존했다. latest dev 대비 변경은 신규코어3/테스트1/문서3의7파일이며 보호파일·웹 diff0.
- `.\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` —86 tasks 실제 재실행, BUILD SUCCESSFUL(24초), 양JVM110/110·신규31/31·양debug PASS.
- `node --test web/test/*.test.mjs` —B04 추가 시험 포함110/110 PASS. 이전109 결과를 최신110 결과로 갱신한다.
- A03 문서 SHA256 14C861DF11C3823018429D5DA4E6E8062084DB2C76706155DA50897D21173FC0 보존. main 참조 이동·실차/실음성 활성화 없음.
## 2026-11-14 B01 — 평가·강습의 가상 종합 연습 음성

- 평가·강습에는 목표 선택 화면이 없으므로 웹 가상 주행의 음성 규칙에 기존 '그냥 몸풀기' 목표를 공급한다. 연습 모드의 사용자 선택 목표와 음성 반복 제한은 유지된다.
- Windows / Node 24.18.1: 웹 단위 110/110 PASS. Node VM에서 실제 앱 초기화·시뮬레이터·리포트 연결을 실행해, 같은 유형과 시각으로 기록한 두 교차로 중 재생이 완료된 사건만 시간표에 표시됨을 확인했다. B04 학습 창은 미지원 신호에서 닫히고 복구 시 자동으로 열리지 않는 것도 확인했다. 브라우저 음성·Pleos는 미시험.
- S06 웹 Mock 범위 PASS, S07·S10 가상 출처 범위 PASS. S05 차량 주행 재생 NOT_VERIFIED. 공식 근거는 [SAFETY_RULES.md](SAFETY_RULES.md)의 P02·P09 참조.

### B02 PR #25 최종 dev 동기화

- PR: https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/25 (base=dev). PR 생성 중 dev에 B01 목표 연결 #24가 추가되어0b35baab8328d83e92ff12761d5f10ace655c475까지 반영했다. 변경은 웹/문서이며 B02 코드는50a95d9와 동일하다. 로그 append 충돌 양쪽 보존.
- 최종 후보에서 두 JVM/두 debug를 --offline --rerun-tasks로86작업 실제 실행, BUILD SUCCESSFUL(15초). 양JVM110/110 및 신규31/31,웹110/110 PASS. 보호파일은 최신dev대비diff0. 이후 원격CI/병합 결과는 PR #25 이력으로 추적한다.

### A02 최신 dev44bfe68 통합 후 회귀

B01/B02/B04가 먼저 병합된 `44bfe68c7aec5dd4cfe97cd20b1fa839c8b9ec05`를 기능 브랜치에 반영했다. 상태·검증 대장의 말미 append 충돌 두 곳에서 A02와 B01/B02/B04의 모든 절을 보존했다. 차량/이벤트 코드 충돌은 없으며 최신 dev 대비 B02 음성 정책·웹 diff는0이다. 기존 엔진 정책과 UI·저장소·게이트도 무변경이다.

`.\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` — **86/86 tasks 실제 실행, BUILD SUCCESSFUL(56초), JVM mock138/138·vehicle138/138, 두debug빌드 PASS**. `node --test web/test/*.test.mjs` — 최신 웹 **110/110 PASS**. A02 신규28개, B02 신규31개를 함께 검증했다. 기본debug flag는 Boolean.parseBoolean(false), release 상수false 유지. 실기 결과는 코드8166fe4의 명시적 opt-in APK에 한정하고 이 통합 단위/빌드를 실차 증거로 승격하지 않는다.

## 2026-11-14 A05 — 웹 2D Mock 조작 반응·입력 충돌·상태 표시

- 담당/브랜치: 사용자 A05 배정 / feature/a05-web-control-polish / 별도 Chiilkkabwa-Codex3-A05-Controls Worktree. 최초 dev44bfe68c7aec5dd4cfe97cd20b1fa839c8b9ec05, 시험 코드5cb0612. 최신 dev02091872cf77650051318aa844517c0f3c033d19를 c20197d6f99b4cd5b77485dbcf144f4c2f2da5e2에 반영. 웹 코드는 시험 코드와 동일하며 A02 Android 변경을 그대로 보존했다.
- 환경: Windows / Node24.22.1 / Chrome157.0.8103.51 / 기존 Playwright / 독립 localhost·headless 브라우저. A05는 Android/실차/AVD를 실행하지 않았다. C02를 읽거나 조작하는 검증 절차를 사용하지 않았다.
- 상세: [설계·수치·안전 한계](A05_CONTROL_POLISH.md). 실제 [A05 브라우저 결과](evidence/a05-2026-11-14/controls-results.json), [A04/자동 시연 결과](evidence/a05-2026-11-14/junction-results.json). 화면은 git 제외 build/a05-evidence 및 build/a05-junction-evidence에 보존.

| 실행 | 결과 | 판정 |
|---|---|---|
| 변경 전 node --test web/test/*.test.mjs | 110/110 | 기준 PASS |
| 페달 새5회귀를 기존 코드에 실행 | 포인터 취소/캡처 상실 잔류·소유권·늦은 콜백·보조 버튼 모두 실패 확인 | 결함 재현 |
| 최종 node --test web/test/*.test.mjs | 126/126, 실패0/건너뜀0. 새 입력11+페달5 포함 | PASS |
| node --check web/app.js, git diff --check | 오류 없음 | PASS |
| NODE_PATH=<기존 Playwright 경로> node web/test/controls.browser.cjs | 1440×900/390×900 조작, 슬라이더 우선, 포커스/동시 키, 페달, PRND, 정지 요청/감속/정지/해제. 온보딩5단계·코치 마크·GNB 회귀. pageerror/console error0 | PASS |
| NODE_PATH=<기존 Playwright 경로> node web/test/junction.browser.cjs | 평가/강습 정상 회전 및report, 연습 복기 저장 후goal. 390/1440px, 브라우저 오류0/요청 실패0 | PASS |
| 최신 dev0209187 동기화 후 웹 동일성·단위 재실행 | 웹diff0 및126/126 | PASS |
| Android 로컬 실행 / 실제 음질·실차 | A05 범위 외. 브라우저 음성/효과음은 껐음 | NOT_RUN / NOT_VERIFIED |

초기 브라우저 검사에서 제가 추가한 콘솔 초기화 선택자 오타를 발견해 수정했고 최종 오류0을 확인했다. 예전 A04 검사기의 온보딩 버전2 고정값을 현재 버전 읽기로 보완했으며 제품 온보딩은 변경하지 않았다. 주행/교차로 물리와 이벤트 계약, B/C 음성/복기/학습/소개·저장 코드는 최신 dev 대비 diff0이다.

규정: S01 PASS(웹 Mock/실제제어0), S03/S04 PASS(기존 게이트/잠금 회귀), S07/S10 PASS(Mock·요청/실제 가상 속도 구분), S11 자산/라이브러리 추가0·실장비 성능NOT_VERIFIED. S02/S05/S06/S08/S09/S12는 이번 변경 대상 기능 없음(NOT_APPLICABLE), 기존 제품 전체를 인증하지 않음. 근거는 SAFETY_RULES/COMPLIANCE_REGISTER와 A05 문서의 P04/P06 링크. G1~G4 PASS, G5는 최신dev/보호diff/CI 확인 후 웹 변경만 dev 통합한다. main 보존. 병합·원격CI 최종 상태는 A05 PR/Actions 이력으로 추적한다.

## 2026-11-14 A03 — NaviHelper 3.0.5 독립 정차 요청 어댑터

- 담당A/Codex2 / feature/a03-navihelper-integration / Chiilkkabwa-Codex2-A03-Navi.
- 최초dev44bfe68c7aec5dd4cfe97cd20b1fa839c8b9ec05, 병합 전 최신dev02091872cf77650051318aa844517c0f3c033d19(A02 PR#27) 반영. 구현커밋992c36f, dev반영1867848; debug 비상수필드 소비 보완은 후속커밋/PR 참조.
- B02/C02/A02/A04 기능코드 및 기존Worktree·브랜치 원형 보존. 원본 A03조사 문서 SHA256 14C861DF11C3823018429D5DA4E6E8062084DB2C76706155DA50897D21173FC0와 동일 사본을 추적. 역사적 사본의 Markdown hard-break/EOF 공백은 그대로 보존하고 새 코드·통합문서 whitespace 검사는 PASS.
- Windows/PowerShell,Temurin17.0.20,Gradle8.10.2,Android35,Node24.18.1. 실제 공식 Maven SDK 해석 후 offline 회귀.
- NaviHelper3.0.5 캐시 AAR SHA256 15FADD11C7C611B1ABDF7EBF9CE91F2A6CE2728E60B4E38B4343E11BB9C12E97. 실제 initialize(onConnected,onFailed)/requestRoute/requestRouteWithWaypoints/리스너/remove/release 컴파일. 내부API/리플렉션/제어/자동취소0. [시그니처·권한·상태계약](A03_NAVIHELPER_INTEGRATION.md).

| 실행 | 결과 |
|---|---|
| TDD | 타입부재 compile FAIL→신규23 PASS. signed-zero중복 누락 회귀26중1 FAIL→정규화 후26/26 PASS |
| 초기기준dev44bfe68 | 양JVM136/136(기존110+A03 26),양debug빌드 PASS |
| 최신dev0209187 | .\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug —양JVM164/164,실패/오류0,양debug BUILD SUCCESSFUL(11초) |
| Web | node --test web/test/*.test.mjs —110/110 PASS. Web변경0 |
| debug플래그 | 상수 인라이닝 RED(getstatic없음)→Java非상수필드+소비코드 재컴파일→opt-in true 및 이후기본false 모두 javap getstatic PLEOS_NAVI_ENABLED 유지. debugON→OFF 증분빌드 검증 |
| release플래그 | -PenablePleosNavi=true :app:generateVehicleReleaseBuildConfig 에도PLEOS_NAVI_ENABLED=false |
| 실제앱/SDK/Emulator/실차 | 초기화/경로호출/탐색/안내 전부NOT_VERIFIED. 합성Fake콜백은 실제검색증거 아님 |
| 장치조회 | adb devices -l: 타작업 일반Android emulator-5560만. AVD목록에PleosA01존재하나비실행. 기기점유/설치/주차조건대체 없음 |

신규 JVM26: 기본off/실차·release차단,초기화결과·재시작,명시적동작/strict기어P·브레이크ON·UX해제,모든신호상태/신선도/출처,좌표·필수필드·순서·중복/부호0,실제SDK변환,Unit/검색/nav단계분리,중복/ID상한,권한·미지원·끊김오류,게이트상실/timeout/stop/release후지연/세대격리,cleanup불명차단,단조시계역전,원본리스트변경방어.

Gradle은A02 AAOS_SPEED_ENABLED와A03 PLEOS_NAVI_ENABLED의 삽입위치2곳이 충돌했고 양쪽을 보존. Manifest는자동병합 후 A02속도조회와A03경로요청권한 각각 유지. 최신dev대비 A02·B/C·웹diff0. 단순정적파일경고를 플랫폼성공으로 바꾸지 않았다.

규정: S01 PASS(제어/자동취소0),S02 공개SDK·실컴파일PASS/공모전O03재열람도구접근실패·기기권한/재배포승인NOT_VERIFIED,S03 PASS JVM(strict/fresh/source/상실차단)·실기게이트NOT_VERIFIED,S04 UI/주행터치변경0·실환경NOT_VERIFIED,S07 PASS(관측·상관관계·단계분리),S09 PASS(새위치수집/저장/업로드0·메모리상한),S10 PASS(합성/빌드/실환경구분),S12 main/기존작업보존·제출/릴리스NOT_APPLICABLE. S05/S06은음성/주행서비스변경없음. 공식SDK링크·O03/P02/P03/P06/P07/P08 적용범위는A03문서/SAFETY_RULES/COMPLIANCE_REGISTER참조.

G1/G2 PASS(배정·독립범위·공식근거/미확인),G3 PASS(기본off/실차·release차단/최소권한/실패격리),G4 JVM·빌드·Web만PASS/실초기화·검색·안내·자원회수NOT_VERIFIED,G5 기본off연동기반만 자체통합. 실제코스안내완료 주장 없음. 최종PR·병합SHA·CI는 후속 기록 또는 해당PR이력으로 추적.

### A03 최종 출처 계약 회귀

NaviProgress.signalSource/requestDataSource 부재 compile RED→최소필드 추가→양JVM164/164(신규26)·양debug BUILD SUCCESSFUL(5초). 기존 차량/코칭/프로필/웹 코드는 무변경. 출처·초기화/함수/검색/nav 단계 분리를 유지한다.

## 2026-11-14 A05 — A03 병합 충돌 해결 재검증

기준dev9a729698f87f93d78fb4a948f63db49ea8307277, 병합dbce4354d54093e519ea7d402712e2213d5e7e5c. 두 공통 문서 양쪽 기록 전부 보존, 제품코드 충돌0. [명령·환경·안전·검증 상세](A05_CONTROL_POLISH.md#최신-dev-a03-충돌-해결-재검증-2026-11-14). 웹126/126, 양JVM164/164,두debug빌드 PASS(offline rerun86tasks/26초). A05조작390/1440 PASS. A03/Android/Gradle/B·C보호diff0. 전체회귀/최종PR·CI는 아래 후속결과와 PR#28/Actions로 추적.

최종 후속 결과: node web/test/junction.browser.cjs 전체 PASS(평가/강습 TURN_GOOD 기록 후 report, 연습 reviewsSaved 후 goal, 390/1440 조작). 브라우저 오류0/요청실패0. A04 26개를 포함한 웹126개 PASS. 원본 결과는 build/a05-junction-evidence/browser-results.json 및 추적 evidence/a05-2026-11-14/junction-results.json. 최종 코드 dbce435 기준이며 이후 커밋은 이 문서/증거만 변경한다. G4/G5 로컬 회귀·보호 범위 검사는 PASS; 원격CI·병합은 PR#28의 최종 HEAD와 Actions 결과로 확인한다.

## 2026-11-15 A03 Debug Probe — 실행 진입점·합성 검증

- 최초기준dev2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8,feature/a03-debug-emulator-probe,전용Chiilkkabwa-Codex2-A03-Probe. 기존A03/B02/조사원본/main 보존. [구현·실행명령·규정](A03_EMULATOR_VERIFICATION.md).
- 초기조회후A01점유가발견되어읽기전용실행계획을자동중단(우리AVD시작0). C02 port5560/A01 port5554는 타작업. 여유메모리3.76GB로추가4GBAVD미실행. 사용자답변 “아직 사용 중 — 실측은 미검증으로 진행” 이후장치설치/초기화/요청0.
- 코드: src/debug owner,vehicleDebug exported=false Activity,androidTestVehicle stock Instrumentation. 기존 createDebugNaviAdapter/SDK3.0.5/strict Gate 재사용. SDK객체·initialize·callback·remove/release 고정 trace hook만 추가,기본no-op. vehicle testInstrumentationRunner 지정1곳,새의존성0.
- TDD: owner부재 compileRED→10PASS→기어끊김누락11중1FAIL→차단보완,view닫기계약compileRED→12PASS. 자체리뷰이며실기수명/동료리뷰로표시하지않음.
- 실행: .\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest :app:processVehicleReleaseMainManifest :app:compileVehicleReleaseKotlin — BUILD SUCCESSFUL. XML mock176/vehicle176,failures0/errors0,owner각12.
- node --test web/test/*.test.mjs —126/126 PASS. git diff --check PASS. 보호영역(Main/A01/A02/B/C/Web/main Manifest) diff0.
- vehicleDebug merged manifest Activity exported=false,vehicleRelease manifest/컴파일class의NaviProbe미포함 확인. test APK targetPackage=com.chiilkkabwa.app. 이정적보호/컴파일은실제장치실행증거아님.
- 실제SDK생성/initialize/onConnected/onFailed/timeout/remove/release,실제정차신호·권한·경로함수/검색callback/기본내비안내: 전부NOT_VERIFIED. 장비사유실제요청BLOCKED. 브레이크false/불명/STALE/권한거부/수명/순서·중복은합성JVM으로만PASS.
- S01/S02(공개SDK·컴파일)/S03(JVM)/S04(정적·JVM)/S07/S09/S10 PASS 범위명시. S02실권한·승인/S04실기/S05 NOT_VERIFIED;S06/S08/S11 변경없음NOT_APPLICABLE;S12 main/기존작업보존·제출없음. G1~G3 PASS(배정·공식근거·Debug차단),G4로컬만PASS/실기NOT_VERIFIED,G5기본off Debug기반만통합. 공식근거·S01~S12상세는전용문서참조.
## 2026-11-14 A02 — 속도 표본 품질 Shadow 및 오프라인 재현

- 배정A/Codex1, `fix/a02-speed-sample-quality`, 전용worktree chiilkkabwa-codex1-a02-quality/a01-pleos, 기준dev2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8. 최종코드/PR/병합SHA는 이 항목의 후속 및 PR이력으로 추적한다. main 기준c4c15686a30e788143ccc88d8ebaa52500d3187b.
- Windows PowerShell/Temurin17.0.20.101/Gradle8.10.2/AndroidSDK35/Node24.18.1. 기존 캐시 사용 `--offline`. 새 SDK/대형의존성/제어/권한/앱 연결0. [전체방법·비교·S/G등급](A02_SAMPLE_QUALITY.md), [재현자료](evidence/a02-quality-2026-11-14/README.md).

| 실제 실행 | 결과 |
|---|---|
| 변경 전 양JVM/양debug | 각164/164 PASS, BUILD SUCCESSFUL(13초) |
| 변경 전 web | node --test web/test/*.test.mjs —126/126 PASS |
| TDD 신규타입/파서 | compile RED(Unresolved reference)→구현 후15/15 GREEN. 초기 gentle-decrease 기대오류 수정: 1km/h/100ms는 감소threshold미만. 임계값 변경0 |
| 변경 후 .\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug | 양JVM179/179,실패/오류0,두debug BUILD SUCCESSFUL(9초,86tasks) |
| 변경 후 node --test web/test/*.test.mjs | 126/126 PASS |
| 리플레이 filtered JUnit | 원본29관측/29consumed 분리, 원본로그변경0, cold 재현/CSV/합성10시나리오 PASS |
| 신규 Emulator/실차/정답 정확도 | NOT_VERIFIED. 이번 실행은 오프라인 증거 재사용이며 실제 오탐률 인증 아님 |

기존 증가1@699902ms는101ms/2.750275m/s²로 재현되며 창평균/지속성Shadow는0. 실측 감소는기존799168ms→창799404455800ns→지속성799779340800ns(약611ms지연). 감소발췌의기록된증가시각798801ms와cold재현798204ms는미기록이전cooldown/episode가없어동일시각보장불가. 로그에없는처리시각은receiptNs를분석now로쓴다고명시. 소비결과로과거상태추측안함.

합성의일정참값+계단노이즈/1kmh양자화/불균일100–200ms/완만±1/강한±5/단발스파이크/정속/누락콜백/동일·역전시각/invalid상태·권한거부/음수/세션·출처·종료후지연을검증. +3m/s²는Shadow누락이므로EXPERIMENTAL독립후보만유지. 원본근거·시계·legacy동일성·메모리32상한·cooldown우회차단도PASS. 기존엔진/bridge/활성화gate·A02_SPEED_EVENTS·A01/A03/B/C/Web/MainActivity/Manifest/Gradle변경0.

S01/S02/S07/S09/S10/S12 변경범위PASS; S03/S04/S06/S08/S11 오프라인범위NOT_APPLICABLE(실환경성능NOT_VERIFIED); S05주행수집/서비스정책NOT_VERIFIED로기존차단유지. 출처·공모전/플랫폼/팀적용범위·제한은전용문서표참조. G1/G2/G3 PASS, G4단위/빌드/웹/오프라인만PASS·새실환경NOT_VERIFIED. G5리뷰/원격CI/통합은후속기록. 빌드성공을실신호/실차PASS로전환하지않음.

작업중환경차단: 기본sandbox는기존Gradle캐시lock쓰기불가. 승인된캐시접근으로RED진행후자동승인검토서비스가크레딧부족으로한차례실행거절. 사용자재개후같은테스트를정상실행했고상기결과확인. 이거절은기능FAIL이나안전거절이아님. 로컬로그는.gitignore의.gradle/a02-quality/에보존하며캐시/산출물은커밋하지않음.

A02 Shadow 리뷰 후속(2026-11-15 KST): 독립 read-only 리뷰 Critical/Important0. 리뷰에서 지적된 정규200ms의 최소5표본 미충족을 추가테스트1 FAIL로 재현한 뒤 창 축소 시5개를 보존하도록 수정. 200ms +5m/s²는800ms에후보1개로GREEN. 기존실측비교동일. 최종재실행은양JVM180/180(신규16),양debug빌드 BUILD SUCCESSFUL(5초,86tasks),실패/오류0. 생성합성비교11개. 정상입력메모리상한검증을32개절단분기실행증거로과장하지않도록설명보완. 최종실행로그.gradle/a02-quality/review-final-android.log. 원격통합은다음후속기록.

## 2026-11-14 A01 — 입력 제약 재조사·워밍업 관측 회귀

기준dev2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8,전용fix/a01-emulator-input-blockers,최종코드8469119f48c5ed60db9b3d5eb6d721dacf536f80. [공식근거/재현/등급/지원질문](A01_INPUT_BLOCKERS.md),[최소증거](evidence/a01-2026-11-14-inputs/README.md).

Windows/Temurin17.0.20/Gradle8.10.2/Node24.22.1. .\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug — BUILD SUCCESSFUL20초/86tasks실행,XML양178/178·오류실패0. node --test web/test/*.test.mjs —126/126·실패건너뜀0. RED13중6 assertion→GREEN13후 중단전true회귀1 RED→최종14모두PASS. 초기테스트fixture의JVM SystemClock예외는기존주입시계로분리해해결. A02/A03테스트도전체회귀에포함. git diff --check PASS;보호범위diff0.

실환경: 코드50bddf1 opt-in APK를기존A01 AVD에install-r,새COLD PID31744. SDK BRAKEfalse/BELTfalse/TURNOFF와앱snapshot OKfalse/NONE,gearP/uxfalse/PBfalse→PARKING_BRAKE_OFF 잠금·시작비활성확인. 원본시각은epoch로그,Signal.receivedAtMs개별필드는trace미포함. exactcapabilityconfig는이번로그미포함,실제읽기권한granted및기존capabilitygating구독수신만확인. 버클/PBON입력없음은공식48카드/실제48카드/동일공식이미지metadata대조;경고등토글반복0. 세실완료조건은BLOCKED/NOT_VERIFIED. Mock JVM6미션성공은sourceMOCK,실기/실차와분리.

Awake/DisplayON/resumed/정상화면확인;검은화면미재현·기존원인해결NOT_VERIFIED. A01앱/우리forward18088/우리AVD만정상종료,data/snapshot보존. C02/다른Worktree수정0. 기본debug·release/A02/A03플래그false확인. 안전S01~S12/G1~G5는전용문서표참조:실제미확인경로를PASS로승격하지않고기본차단유지. 최종PR HEAD/CI/dev병합·병합후CI는PR/Actions이력으로추적한다. main변경0.

### A03 Probe 최신 dev 통합 회귀 (2026-11-15)

최초코드61ae3dab06a66ecd049e2d6284fdc7b58a59ca67, A01 PR#30의dev91413da9e581c0fd4d18adc056221c63605079c7를c159d15에반영. 충돌은공통대장append2곳이며A01/A03모두보존. 최신기준양JVM190/190(기존178+Probe12),양debug/시험APK/release manifest·Kotlincompile PASS(5초),웹126/126 PASS. git diff --check PASS. 기존core/차량/저장소/UI/mainManifest/Web의최신dev대비diff0. 실제AVD미사용·실초기화/요청/검색/안내NOT_VERIFIED유지. 최종PR/병합SHA/CI는해당PR이력으로추적.
A02 최신dev 통합(2026-11-15): PR#31 생성 직전 dev가91413da9e581c0fd4d18adc056221c63605079c7(A01 PR#30)로진행하여기능브랜치에반영. 공통문서2곳만충돌했고A01/A02기록모두보존,제품코드충돌0. 위전체Android명령재실행양JVM194/194(최신dev178+A02신규16),양debug BUILD SUCCESSFUL(5초),웹126/126 PASS,git diff --check PASS. 코드기준4ca8d6310dce860050810475a0c02332855aebb8+dev91413da. 최신dev대비A01/A03/B·C/Web/활성화gate/기존엔진diff0. G5 로컬통합·안전검증PASS; 원격PR CI통과후자체병합하며병합SHA·최종HEAD·dev push CI는 https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/31 및 연결Actions가정본이다. 원격CI를로컬PASS로대체하지않는다.

### A03 Probe PR #32 최종 동기화

PR#32의원격CI진행중 A02 PR#31이dev8993d25c3b93a0d0b70c13f604b540196a42bbab에통합되어반영. 두공통대장각append2블록충돌을A01/A02/A03전부보존해해결했고새기능코드충돌0. 최종전체명령재실행 BUILD SUCCESSFUL(11초/129tasks),양JVM206/206(기존194+owner12),양debug·시험APK·release manifest/Kotlin PASS,웹126/126 PASS. git diff --check 및최신dev대비보호범위diff0. 실제장치시험NOT_VERIFIED유지. 최종head/병합SHA/dev CI는 https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/32 와연결Actions이력이정본이다.
## 2026-11-15 A02 — Pleos 주행 생명주기·실행 가능성 검증

배정A02/Codex1, `test/a02-driving-lifecycle`, 기준dev01eff12a2ecdbd4ae3e0fdb78f6031504aa13540. 전용worktree chiilkkabwa-codex1-a02-lifecycle/a01-pleos. [감사·공식근거·실측·계약·S/G전표](A02_DRIVING_LIFECYCLE.md), [증거색인/JSON](evidence/a02-lifecycle-2026-11-15/README.md). 최종HEAD/최신dev통합/PR·병합·CI는후속및연결PR기록참조.

공식P02/P03을2026-11-15재열람. DO미선언Drive사용불가와실제백그라운드수집예외승인부재를구별. 실제화면차단시Activity가pause된다고원문으로추정하지않음. S05수집허용/양산수명NOT_VERIFIED,주행제품활성화BLOCKED유지. 제어/비공개API/VHAL/DO/FGS/자동수집우회/B·C연결/Shadow승격없음.

| 실제 실행/환경 | 결과 |
|---|---|
| Windows/Temurin17.0.20.101/Gradle8.10.2/SDK35/Node24.18.1 | 기존cache사용offline |
| 기준선 양JVM206/두debug | BUILD SUCCESSFUL(30초) |
| 신규 합성 repo/session lifecycle 회귀4 | PASS(중복start/ready,close중재진입·stop뒤callback,새세션/Baseline/근거,권한오류terminal·재시작) |
| .\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug | 양JVM210/210·실패오류0,양debug BUILD SUCCESSFUL(14초) |
| node --test web/test/*.test.mjs | 126/126 PASS |
| -PenableAaosSpeed=true :app:generateVehicleReleaseBuildConfig | AAOS_SPEED_ENABLED=false、A01/A03false |
| 실제 dedicated Pleos2.1.0/API34/hardware ranchu,AVD5558/user10 | 새공식AVD.우리시작후다른5562가나타났으나설치/설정/종료명령0 |
| 시험opt-inAPK | SHA2566FD8228174FC8583B8353CD5B475EBC7B6B3E0E375D7ECCE33A40B36A6830FE6,A02만true |
| D→moving→P | UXfalse0→true80→false0/DrivingState0→1→2→1→0. 차단화면확인·MainActivity는RESUMED유지,3416OK표본·최대0.8333334m/s·이번RAPID후보0 |
| 실제pause/stop | HOME미작동은PASS아님. P에서시스템설정열기시pause/stop. logepoch1794671236.158~1794671258.151의A02관측/계산0,복귀새UUID·Baseline |
| CPU sample | uptime521.07→535.20,utime1146→1160/stime1712→1732,CLK_TCK100.0.34CPU초/14.13초약2.4%단일코어. 전체프로세스가0이아니며A02성능인증아님 |
| 권한 | 표준debug grant→revoke/PID17603denied·null·권한창→test regrant/명시coldrestart/PID18172새baseline. 사용자Allow/Deny자동화미수행 |
| 최종복원 | 기본OFFAPK재설치·CAR_SPEED철회·새PID15536의5초A02trace0.우리5558/28088/logPID14092만종료·데이터보존 |

P복귀는pause가없어동일세션이었으며새Baseline을거짓주장하지않았다. 실제Binder지연콜백고의주입NOT_VERIFIED,합성차단PASS와분리. unregister/close함수반환은서비스자원회수ACK가아니다. 주행중실제새급변후보발행·정답정확도·연속여행수명·실차NOT_VERIFIED. 기존ViewModel의200msdelay등은남으므로A02중단을프로세스CPU0으로표현하지않음.

규정: S01/S07/S09/S10 변경범위PASS; S02공개API실행/빌드PASS·공식주행수집승인NOT_VERIFIED; S03기존gate회귀PASS/실제PBON NOT_VERIFIED; S04실제플랫폼차단화면PASS/전체UI인증NOT_VERIFIED; S05수집허용·양산수명NOT_VERIFIED; S06/S08 NOT_APPLICABLE; S11CPU관측만PASS/장시간·성능NOT_VERIFIED; S12main/기존자료보존PASS·제출NOT_APPLICABLE. 공식URL/플랫폼·공모전·팀적용범위는전용문서참조. G1/G2/G3 PASS,G4등급별증거만PASS,G5비활성진단·검증자료통합만허용.

실측중기본OFF복원명령은자동승인검토서비스크레딧부족으로실행전1회거절,사용자재개후동일복원을실제완료했다. 안전위반판정이아니며우회하지않음. rawlog/AVD/빌드cache는ignored.gradle/a02-lifecycle보존. 최초am start -W timeout뒤실제RESUMED·수신으로실행을확인한경계도기록했다.


## 2026-11-15 A01 — 선택 센서 공개 SDK·오프라인 워밍업 회귀

기준dev01eff12a2ecdbd4ae3e0fdb78f6031504aa13540,코드19e4ec3e8e4cad17012984a10b7c2124dc6efb3a,feature/a01-optional-vehicle-signals/새전용Worktree. [보고서·S01~S12/G1~G5](A01_OPTIONAL_SIGNALS.md),[원본public선언·hash·XML집계](evidence/a01-optional-2026-11-15/README.md). Windows/Temurin17.0.20/Gradle8.10.2/Node24.22.1.

실물sources+javap/컴파일로Wiper/LowBeam/Passenger/Trip의공개읽기·capability·동일listener/area해제·권한·타입·단위를확인. 새권한0. 미구현channel/helper compile RED→선택센서11 GREEN후14확장,선택미션6/6 assertion RED→GREEN. 기존필수3종·Mock6미션·SDK수명/오류와A02/A03전체JVM포함.

.\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug —최종기본복원BUILD SUCCESSFUL42초/86tasks모두실행/양226/226·실패오류0/양DebugPASS. node --test web/test/*.test.mjs —126/126·실패건너뜀0. git diff --check PASS. -PenablePleosSignals=true assembleVehicleDebug+generateVehicleReleaseBuildConfig —6초PASS/debugtrue·releasefalse,이후기본false복원. 기존deprecated/strip경고는유지하며실패로숨기지않음.

실환경: 사용자미인계지시로AVD조회/접근/설치/시작/종료0,새센서실수신·실선택미션전부NOT_VERIFIED. steeringDeg는전륜각/핸들각계약차이,carDimensions는publicAPI부재로BLOCKED;fallback은실제차량capabilityfalse의증거가아님. P/PB/UX게이트필드쓰기0·실차/release/default차단유지. 기존벨트true/실PBON/전체Emulator워밍업BLOCKED유지. main/기존A01/A05/C02/AVD데이터보존. 최신dev/충돌·최종HEAD CI후dev만통합하고최종PR·merge·사후CI는PR/Actions이력참조.

### A03 Pleos Emulator 실제 SDK runtime (2026-11-15 KST)

실행코드/최신기준dev01eff12a2ecdbd4ae3e0fdb78f6031504aa13540, branch test/a03-navihelper-runtime.
제품/SDK/권한/게이트 수정0. [결과 및 S01~S12/G1~G5](A03_RUNTIME_RESULTS.md), [원본 최소증거](evidence/a03-runtime-2026-11-15/README.md).
시작 전 장비/프로세스0 확인 후 기존 A01 AVD를 -read-only/-port5562/-no-snapshot로 실행, serial5562/user10 직접확인.
Pleos2.1.0/API34/NaviHelper3.0.5/Service3.0.4에서 기존 inspect→initialize→lifecycle runner 실제실행.
총3회 객체생성→초기화→실제연결callback→listener제거→release반환. 첫Activity 종료/재진입 자동초기화0/명시재시작 PASS.
실PBfalse·선택POI없음으로 mode=request/API호출0 BLOCKED, 검색/안내 NOT_VERIFIED. 지연callback/실제실패/timeout/권한거부/주행전환은 미유발 NOT_VERIFIED.
opt-in 빌드19초/66tasks; defaultOFF --offline --rerun-tasks 전체Android BUILD SUCCESSFUL1분8초/129tasks,
양JVM206/206 failures/errors/skipped0, 양debug/시험APK/release manifest·Kotlincompile PASS, release Probe제외.
node --test web/test/*.test.mjs 126/126 PASS. defaultOFF APK재설치 후 실제runner BLOCKED, SDKtrace15→15.
우리5562만정상종료, 별도5558/기존Worktree/AVD원본/main보존. G5 원격PR/병합SHA/CI는base=dev PR과연결Actions이력참조.

A02 lifecycle 최신 통합·리뷰: 코드22fe372(실측된 A02 진단 마커와 동일 source),최신dev2bea4d3(PR#33 A03 runtime 문서)를반영했다. 제품코드충돌0,공통대장append2곳충돌은A02/A03기록모두보존. 독립read-only리뷰결함0,원본실측3416/최대0.8333334/급변0/명시pause관측0·양JVM210실패0을별도재집계확인. 새evidence최초stage검사에서state덤프13개의EOF빈줄이검출되어끝빈줄만정리했고원본EOF사본도ignoredraw-state-snapshots에보존. 값·시각·PNG변경0. 최신변경은문서뿐이므로전체Android명령재실행은86tasks UP-TO-DATE/BUILD SUCCESSFUL(1초),XML210/210유지,웹126/126새실행PASS. 전체최종diff검사및PR/CI/병합은후속정본참조. 초기diffcheck는미추적증거를검사하지못했으므로최초stage EOF경고를누락해통과라고판정하지않는다.

A02 생명주기 PR 정본: https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI/pull/35 (base=dev). 실측source22fe372, 최신dev2bea4d3303b241cd10f3b592ea7734980edfd8b3 통합f5e5b0c555c8b51d02ab29ea3cc80c74b5899dba. 최종range git diff origin/dev --check PASS. PR최종HEAD/원격CI/병합SHA/병합후dev CI는연결Actions와PR최종결과를정본으로추적한다. G5는원격성공확인후진행하며로컬210개결과를CI성공으로대체하지않음. main기준c4c15686a30e788143ccc88d8ebaa52500d3187b보존.

A02 PR#35 최종 동기화: CI 대기 중 A01 PR#34 dev3c0c824ca35951891f72d9c8fb24d2c098b7694b를 반영했다. 제품코드 충돌0, 공통대장 A01/A02/A03 기록 모두 보존. 필수 전체회귀 재실행: 양JVM230/230(최신dev226+A02신규4)·양debug BUILD SUCCESSFUL(13초), 웹126/126, 최종diff check PASS. 실측source22fe372 기록은 그대로이며 최신A01 선택센서의 실수신을 새로 주장하지 않는다. 추가조사/실험 없이 새HEAD의 CI→dev자체병합→사후CI만 진행한다.


## 2026-11-15 C02 — Android 프로필 분리·전환·삭제·복원

- 담당 역할: C / 브랜치 `feature/c02-profiles` ← `origin/dev` 3d9777a
- 환경: Linux 클라우드 컨테이너, JDK 21, Gradle 8.10.2, Android SDK 35. 에뮬레이터 없음
- 문제: 저장소는 프로필별 저장이 가능했지만 앱은 항상 첫 프로필만 열었다(두 번째 가족이 쓰고 재실행하면 첫 사람 기록이 뜸). 전환·추가·삭제 수단 없음. 저장 JSON의 모르는 미션/배지 이름 하나로 앱 시작이 예외로 죽을 수 있었다
- 수정: `PracticeStore` 마지막 프로필 기억(`activeId`), 깨진/모르는 항목은 그 항목만 건너뜀, 순수 규칙 `pick`·`newProfile`. `ChiilViewModel` 전환·추가·삭제(안전 확인 단계 CHECKING/READY에서만). 안전 확인 화면에 프로필 칩(글자 입력 없이 '나·가족 1~3'), 기록 지우기는 두 번 눌러 확인
- 기존 유지: `allowBackup=false`(기기 밖 백업 없음), 서버·계정 없음. **암호화 Room은 여전히 미구현**(SharedPreferences JSON)

| 시험 | 명령 | 결과 |
|---|---|---|
| Android 단위 + 빌드 | `./gradlew :app:testMockDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` | PASS 235/235(신규 `ProfileRulesTest` 5개), 두 플레이버 빌드 |
| 기기·에뮬레이터 화면 | 미실행 | NOT_VERIFIED |

규정: S09 PASS(기기 내 저장·프로필 분리·삭제, 백업 꺼짐) / 암호화 저장 NOT_IMPLEMENTED, S03·S04 PASS(주행·연습 중 프로필 변경 차단), 에뮬레이터 NOT_VERIFIED


## 2026-11-15 C01 — Android 주차 후 속도 시간표

- 담당 역할: C / 브랜치 `feature/c01-android-timeline` ← `origin/dev` 8e3e9ab
- 환경: Linux 클라우드 컨테이너, JDK 21, Gradle 8.10.2, Android SDK 35. 에뮬레이터 없음
- 구현: `core/review/DriveTimeline.kt` — 주행 잠금 동안 스냅샷의 `speed`(A02 `VehicleSpeedObservation`)를 약 1초 간격(최대 3600점)으로 모으고, 사건은 A02 `SpeedEventBridge`를 그대로 써서 판정. 다시 주차하면 복기 화면에 정적 그래프·사건 점·글 목록·출처 라벨. mock 디버그 패널에 속도 0/20/50 버튼
- **수집 경계(S05)**: 새 구독·백그라운드 수집을 만들지 않았다. A02가 켜 둔 속도 신호가 앱에 들어올 때만 쌓이고, vehicle 기본값(A02 opt-in 꺼짐)에서는 "속도 기록 없음"이 뜬다. 공식상 DO 미선언 앱의 주행 중 사용은 NOT_ALLOWED, 백그라운드 수집 허용은 NOT_VERIFIED(A02_DRIVING_LIFECYCLE.md) — 이 판단을 바꾸지 않는다
- 저장: 메모리만(S09). 복기를 저장해도 시간표는 저장하지 않는다
- 발견해 막은 함정: 스냅샷은 속도와 무관한 값이 바뀌어도 다시 온다. 같은 표본이 A02 엔진에 다시 들어가면 `NON_INCREASING_TIME`으로 거부되며 기준이 초기화돼 사건을 놓친다 → 같은 sessionId·같은 timestamp는 건너뛴다(테스트로 고정)

| 시험 | 명령 | 결과 |
|---|---|---|
| Android 단위 + 빌드 | `./gradlew :app:testMockDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` | PASS 241/241(신규 `DriveTimelineTest` 6개: 1초 간격, A02 급감속 판정, 중복 표본, 신호 없음, 새 세션 새 기준, 초기화·상한), 두 플레이버 빌드 |
| 화면 렌더 / 에뮬레이터 | 미실행 | NOT_VERIFIED |

규정: S03·S04 PASS(시간표는 REVIEW에서만, 주차 확인이 깨지면 E3 잠금으로 함께 닫힘), S05 경계 유지(NOT_VERIFIED 그대로), S07 PASS(“속도 변화만 본 기록, 페달·주변 상황 판단 아님” 표기, 출처 라벨), S09 PASS(메모리만), S10 PASS

## 2026-11-15 제출 전 점검 (AI — 웹 안전 수정·접근성·README·블라인드)

- 일시(KST): 2026-11-15 04~06시
- 브랜치 / 기준: `claude/confident-wright-u9iqdb` ← `dev` 52e551f
- 환경: Linux 클라우드 컨테이너, Node 22, Playwright Chromium(헤드리스), axe-core 4.10.2. Android SDK 35 설치했으나 Gradle 의존성 받기가 Maven Central 429로 실패
- 명령/시나리오: `node --test "web/test/*.test.mjs"` / axe WCAG 2 A·AA(라이트·다크 × 안전 확인·소개·온보딩, 평가 리포트·장면 재현 창) / 평가 모드에서 핸들을 돌린 채 출발해 주행 잠금 중 앱 화면 알림 여부 / 연습 자동 시연 끝까지 / Git 이력·파일 블라인드 검색
- 결과:
  - 웹 단위 **126/126 PASS**
  - 주행 잠금 중 앱 화면 알림: 수정 전 '핸들을 가운데로…' 토스트가 앱 화면에 뜸(S04 **FAIL**) → 콘솔 쪽 표시로 옮긴 뒤 앱 화면 0건 **PASS**
  - axe: 수정 전 꺼진 경고등 글자 대비 2.57:1 위반 → 토큰 수정 후 8개 화면 위반 **0**
  - 연습·평가 자동 시연 끝까지, 브라우저 오류 0
  - Android 로컬 빌드 **BLOCKED**(Maven Central 429, 코드 문제 아님). 대신 GitHub CI `dev` 52e551f 런 #159: 웹·Android 단위·mock/vehicle debug 빌드 **SUCCESS**
  - release 빌드: `assembleMockRelease`/`assembleVehicleRelease` **NOT_RUN**(위 BLOCKED). release에서 `PLEOS_SIGNALS_ENABLED`·`AAOS_SPEED_ENABLED`·`PLEOS_NAVI_ENABLED`가 false로 고정된 것은 `app/build.gradle.kts`로 확인
  - 블라인드: 파일 내용 실명·연락처·키 미발견. **Git 이력 작성자 정보에 실명 형태 1개·개인 메일 3개** → 공개 방식 결정 필요(docs/08 §6)
- 규정: S04 PASS(웹, 수정 후) · S10 PASS(README에 확인 범위 분리) · S12 NOT_VERIFIED(release APK 설치·실행 미확인, 공개 방식 미결정)
