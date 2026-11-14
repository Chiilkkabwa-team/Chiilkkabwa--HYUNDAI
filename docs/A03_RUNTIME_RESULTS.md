# A03 NaviHelper 실제 SDK 실행 검증

2026-11-15 KST / A 파트 Codex 2
기준 origin/dev 및 실행 코드: `01eff12a2ecdbd4ae3e0fdb78f6031504aa13540` (PR #29/#32 포함).
브랜치 `test/a03-navihelper-runtime`, 독립 Worktree `Chiilkkabwa-Codex2-A03-Runtime`.

**Pleos Emulator에서 기존 Debug Probe의 실제 SDK 연결 및 Activity 종료·재진입·재연결을 확인했다.
실제 경로 호출은 BLOCKED, 검색 응답과 기본 내비 안내는 NOT_VERIFIED다. 제품 코드·안전 게이트는 변경하지 않았다.**

## 1. 범위와 공식 근거

기존 구현을 재사용했으며 A01 차량 입력·A02 엔진·B/C·사용자 화면·권한·SDK 의존성·release는 수정하지 않았다.
모든 기존 Worktree와 main을 보존한다. AGENTS/SAFETY_RULES/COMPLIANCE_REGISTER,
[A03 연동](A03_NAVIHELPER_INTEGRATION.md), [Probe 구조·명령](A03_EMULATOR_VERIFICATION.md) 및 해당 Kotlin 소스를 확인했다.

공식 문서 재확인:
- [초기화와 해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release): initialize 연결 callback과 실패 callback, 종료 시 release.
- [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling): SDK 오류와 연결 상태를 구분.
- [목적지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations): 목적지 전달과 실제 실행 결과를 구분.
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions).

사용자 제공 `/api-reference/connect-sdk-pleos/NaviHelper/...` URL은 조사 도구에서 읽을 본문이 없어
같은 주제의 위 공식 `/docs/pleos-only/...` 경로를 사용했다. 공개 SDK3.0.5 기존 클래스/메서드를 재사용했다.
문서 확인·빌드·실제 연결·경로 검색·안내를 서로 대체하지 않는다. 공식 승인/출시 적합성 인증도 아니다.

## 2. 실제 환경과 장비 보존

| 항목 | 실제 확인값 |
|---|---|
| 호스트 | Windows, Temurin17.0.20, Gradle8.10.2; 시작 시 여유 RAM 약12.1GB |
| AVD | Chiilkkabwa_Pleos_2_1_0_A01, Pleos2.1.0, RELEASE.release_connect_v2.1.0 test-keys |
| 장비 | emulator-5562 / 사용자10 / Android14 API34 / x86_64 / Connect on x86_64 emulator |
| 앱 SDK | ai.pleos.playground:NaviHelper:3.0.5; 기존 navi-data3.0.4/navi-aidl3.0.1 |
| 서비스 | ai.pleos.playground.service.navi, versionName3.0.4/versionCode55 |
| 기본 내비 패키지 | ai.umos.maps.android.navigation.app, versionName3.3.2-rc5-hotfix.3/versionCode1; 화면 안내 미검증 |
| 경로 권한 | pleos.car.permission.NAVI_ROUTE_SEARCH, prot=normal, 대상 앱 granted=true |
| 실측 신호 | gear=PARK/OK/EMULATOR, parkingBrake=false/OK/EMULATOR, ux=false/OK/EMULATOR |

시작 전 `adb devices -l` 연결0 및 emulator/qemu 프로세스0으로 기존 장비 종료를 확인했다.
원본 AVD를 변경하지 않는 **read-only 임시 인스턴스**를 별도5562 포트에서 시작했다.
삭제·초기화·스냅샷 저장·권한 조작·서비스/내비 강제 종료·VHAL 입력은 하지 않았다.
종료 전에 AVD 이름과 호스트 PID7704/자식11932를 대조하고 **emulator-5562만 emu kill로 정상 종료**했다.
그때 별도 A02 Lifecycle 인스턴스5558이 관측됐으나 명령 대상으로 사용하지 않았다.
설치 경로·APK 해시·원본 출력·시간대는 [최소 증거](evidence/a03-runtime-2026-11-15/README.md)에 기록했다.

## 3. 단계별 실제 결과

| 단계 | 판정 | 증거와 한계 |
|---|---|---|
| E1 준비 | PASS | 최신dev에서 기존 opt-in 앱/시험APK 빌드, 실제 serial/user 조회 후 두APK install 성공 |
| E2 inspect | PASS | CREATED_NO_AUTO_INITIALIZE=IDLE, initializationObserved/callReturned/searchStarted/searchCompleted=false; SDK trace0. 실제 신호 수신. 종료 RELEASED |
| foreground | PASS (Probe gate 범위) | onResume 이후 owner의 foreground+fresh P/UX 초기화 조건 통과. driving/DO 플랫폼 정책 전체 검증 아님 |
| E3 initialize | PASS | 실제 SDK_OBJECT_CREATED→INITIALIZE_CALLED→CONNECTED_CALLBACK. INITIALIZATION_OBSERVATION=CONNECTED/initializationObserved=true |
| E4 lifecycle | PASS (관측 범위) | FIRST_RELEASED, 새 Activity REENTRY_NO_AUTO_INITIALIZE=IDLE/attempt 없음/trace 비어있음, 명시 SECOND_INITIALIZATION=CONNECTED, 마지막 RELEASED |
| listener/release | PASS (반환 관측) | 초기화3회 모두 LISTENER_REMOVED 및 RELEASE_RETURNED, cleanupFailed=false. SDK 내부 전체 자원 회수의 계측 인증은 아님 |
| 과거 callback 폐기 | JVM PASS / 실환경 NOT_VERIFIED | 새 Activity 상태·trace 분리는 실측. 종료 후 지연 callback을 실제 서비스에서 유발하지 못했으므로 실제 폐기는 미검증 |
| E5 경로 요청 | BLOCKED | 실제 PB=false, 사용자 선택 POI 미제공. mode=request 및 requestRoute/requestRouteWithWaypoints 실행0 |
| E6 기본 OFF | PASS | opt-in 없이 재빌드·재설치 후 initialize runner는 Activity/SDK 생성 전 BLOCKED. SDK trace 누계15→15 |

초기화는 명시 동작 + 신선하고 유효한 실제 EMULATOR 기어P/UXfalse로만 진행했다.
주차 브레이크 OFF는 초기화 자체를 차단하지 않는 **기존 계약을 유지**했다.
이 조건을 정차 학습/복기·경로 요청 허용으로 확대하지 않았다. 경로 요청의 PB ON 포함 strict gate는 그대로다.

### 검증 수준

| 검증 수준 | 결과 |
|---|---|
| 1. SDK 클래스/메서드 존재·컴파일 | PASS (PR29/32 및 이번 실제 compile) |
| 2. 앱에서 SDK initialize 연결 callback 수신 | PASS (이번 실제 EMULATOR); 경로 API 호출은 BLOCKED |
| 3. 경로 검색 요청 및 응답 | NOT_VERIFIED (선행 요청 BLOCKED) |
| 4. 기본 내비 실제 안내 시작 | NOT_VERIFIED |

callReturned/searchStarted/searchCompleted/guidanceConfirmed는 모든 checkpoint에서 false였다.
CONNECTED는 SDK 연결만 뜻한다. onRouteStarted/onRoutingCompleted/onError 및 실제 안내 화면은 관측하지 않았다.
경로 함수의 Unit 반환과 검색·안내를 동일하게 취급하지 않는다.

## 4. 오류·제약 검증

| 조건 | 결과 |
|---|---|
| FAILED_CALLBACK / 실제 서비스 연결 실패 | NOT_VERIFIED: 정상 연결만 관측; 타 서비스 중지로 실패를 만들지 않음 |
| 실제 초기화 TIMEOUT | NOT_VERIFIED:3회 모두 연결되어 timeout 미발생; 합성 JVM timeout PASS |
| 실제 권한 미허용 | NOT_VERIFIED: 현재 normal 권한 granted=true. 정상 권한을 pm revoke로 런타임 거부할 수 있다고 가정하지 않으며 Manifest/다른 사용자의 권한 조작0 |
| 중단 후 명시 재시작 | 실제 PASS: 첫 Activity 종료/release 후 새 Activity의 명시 초기화로 재연결 |
| 재진입 지연 callback | 실제 NOT_VERIFIED; JVM 이전 세대 callback 무시 PASS |
| 미지원 플랫폼 | 실제 NOT_VERIFIED; 기본OFF는 실제 PASS, 물리기기/release/지원불가 합성 검사와 구분 |
| 주행 전환·UX 제한 변화·신호 상실 | 이번 실제 NOT_VERIFIED; 정차 상태만 수신. 기존 JVM 차단/close-view 회귀 PASS |
| 실제 차량 / 성능 / 공식 출시 승인 | NOT_VERIFIED; 실제 차량 접근·성능 계측·출시 요청0 |

PB ON 신호를 안전한 공식 입력으로 얻고, 유효한 사용자가 선택한 실제 POI를 받은 뒤 별도 검증해야 한다.
SDK 연결만 성공한 현재 상태에서 임의 좌표·합성 POI·게이트 완화·자동 요청을 추가하지 않는다.

## 5. 실행 명령과 회귀

다음 serial/user는 **이번 장비에서 조회한 실제 값**이다. 다음 시험에서는 다시 조회한다.

~~~powershell
# opt-in (실제 시험 전)
.\gradlew.bat --offline -PenablePleosNavi=true :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest
adb -s emulator-5562 emu avd name
adb -s emulator-5562 shell am get-current-user
adb -s emulator-5562 install --user 10 -r app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk
adb -s emulator-5562 install --user 10 -r -t app/build/outputs/apk/androidTest/vehicle/debug/app-vehicle-debug-androidTest.apk
adb -s emulator-5562 shell am instrument --user 10 -w -e a03 true -e mode inspect com.chiilkkabwa.app.test/com.chiilkkabwa.app.data.navigation.NaviProbeInstrumentation
adb -s emulator-5562 shell am instrument --user 10 -w -e a03 true -e mode initialize com.chiilkkabwa.app.test/com.chiilkkabwa.app.data.navigation.NaviProbeInstrumentation
adb -s emulator-5562 shell am instrument --user 10 -w -e a03 true -e mode lifecycle com.chiilkkabwa.app.test/com.chiilkkabwa.app.data.navigation.NaviProbeInstrumentation

# opt-in 없이 전체 회귀; --rerun-tasks로 기존 결과 재사용 없이 실행
.\gradlew.bat --offline --rerun-tasks :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest :app:processVehicleReleaseMainManifest :app:compileVehicleReleaseKotlin
node --test web/test/*.test.mjs
git diff --check
~~~

opt-in 빌드19초/66tasks PASS. 기본 OFF 전체 회귀 BUILD SUCCESSFUL 1분8초/129tasks 실행.
Mock JVM206/206, Vehicle JVM206/206, failures/errors/skipped0. 웹126/126, 실패/skip0.
양debug·시험APK·release Manifest/Kotlin compile PASS. Release Manifest/클래스에 NaviProbe 없음,
기본 두debug 및 release BuildConfig.PLEOS_NAVI_ENABLED=false 확인.
기본 OFF 앱·시험APK를 위와 같이 재설치하고 동일 initialize runner로 차단을 실측했다.
새 코드·계약 변경이 없어 새 테스트는 추가하지 않았고, 기존 A03 owner12/adapter26을 전체 회귀로 유지했다.

## 6. 안전·통합 Gate

PASS는 이번 A03 변경/관측 범위에 한정하며 앱 전체·실차·출시 적합성 인증이 아니다.
출처/적용 범위는 [COMPLIANCE_REGISTER](COMPLIANCE_REGISTER.md)의 O03/P02/P03/P06/P07/P08과
[SAFETY_RULES](SAFETY_RULES.md)를 따른다. 공개 SDK=공모전/플랫폼 근거, strict 정차 요청=보수적 팀 정책이다.

| 항목 | 판정 및 증거 |
|---|---|
| S01 | PASS: 차량 조회만, 제어/VHAL/자동경로/취소/새권한0. 제품코드 diff0 |
| S02 | PASS (사용 공개 API·빌드·해당 기기 연결); 전체 플랫폼/출시 승인 NOT_VERIFIED |
| S03 | PASS (JVM strict gate/실측 PBfalse로 요청 미실행); 실제 PBON→요청 허용은 NOT_VERIFIED |
| S04 | PASS (기존 foreground/UX gate 회귀 및 정차 Activity 종료/재진입); 실제 주행/UX 전환은 NOT_VERIFIED |
| S05 | NOT_VERIFIED: DO/주행/백그라운드 정책; 해당 새 실행 경로 추가0 |
| S06 | NOT_APPLICABLE: 음성/오디오 변경0 |
| S07 | PASS: 출처·status·timestamp와 연결/검색/안내를 원본 증거로 분리 |
| S08 | NOT_APPLICABLE: 미션·위험 조작 안내 변경0 |
| S09 | PASS (이번 증거): 고정단계/신호·시각만, POI/좌표/개인정보/원본 전체로그 커밋0 |
| S10 | PASS: JVM/빌드/EMULATOR/실차·BLOCKED/NOT_VERIFIED 구분 |
| S11 | NOT_APPLICABLE: 자산·의존성 변경0; 실제 성능 NOT_VERIFIED |
| S12 | PASS (이번 범위): main/타인 Worktree/AVD 데이터 보존, 릴리스/제출/비밀정보 변경0 |
| G1 | PASS: A03 실행 검증만, 기존코드 재구현0 |
| G2 | PASS: 공식 근거 및 기존 SDK 실제 연결, 미확인 명시 |
| G3 | PASS: 기존 기본OFF/실차·release 차단/strict gate 유지, 실제 종료/명시 재진입 관측 |
| G4 | PASS (JVM/빌드/웹/위 실측 범위); 검색/안내/오류 미유발 조건은 NOT_VERIFIED |
| G5 | 로컬 PASS: 안전 FAIL·코드 변경0, 최신dev·증거 자체 리뷰. 원격 PR/CI/병합 결과는 아래 추적 기준 참조 |

## 7. 변경 파일과 원격 통합 추적

변경은 이 문서·A03 최소 증거와 공통 IMPLEMENTATION_STATUS/VERIFICATION_LOG의 최신 A03 결과 기록뿐이다.
기존 A03 문서의 당시 NOT_VERIFIED 기록은 과거 사실로 보존하며 현재 실측 상태는 본 문서가 기준이다.
NaviHelperClient/NaviProbeOwner/NaviProbeActivity/NaviProbeInstrumentation/PleosNaviAdapter/RouteRequestGate 변경0.
A01/A02/B/C/Web/Gradle/Manifest/release 변경0.

base=dev PR의 최종 HEAD·병합 SHA·PR CI·dev push CI는 해당 PR 및 연결 Actions 이력을 정본으로 삼는다.
실제 검색/안내 미검증과 기본 비활성은 문서 통합 후에도 유지한다. main은 병합 대상이 아니다.
