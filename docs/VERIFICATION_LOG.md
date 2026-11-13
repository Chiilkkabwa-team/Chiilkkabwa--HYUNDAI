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
