# A03 Debug 시험 진입점과 Emulator 검증

작업일: 2026-11-14~15 KST / A 파트 Codex2
최초 기준 dev: 2a70830a83b1f1ae2a1453cbf2131bb0b52e96b8
통합 반영 dev: 8993d25c3b93a0d0b70c13f604b540196a42bbab
브랜치: feature/a03-debug-emulator-probe
Worktree: Chiilkkabwa-Codex2-A03-Probe

**Debug 진입점·자동화 runner·안전 소유자를 구현하고 빌드/합성 시험을 통과했다. 실제 기기 초기화·경로 요청·검색·안내는 실행하지 않았다.**

## 1. 장비 제약과 실제 수행 경계

초기 adb 목록에는 C02용 emulator-5560만 있었다. A01 AVD를 읽기 전용·별도 포트로 시작하려고 점유를 재확인한 순간, 다른 Codex의 Pleos A01 프로세스(port5554)가 발견되어 명령이 중단됐다.
우리 Emulator는 시작되지 않았고 다른 기기를 종료·재시작·설치·초기화하지 않았다. 새 AVD도 만들지 않았다.
호스트 여유 RAM은 당시 약3.76GB로 공식4GB AVD를 추가 병행하지 않았다.

사용자는 장비 질문에 **“아직 사용 중 — 실측은 미검증으로 진행”**을 선택했다. 이후 실제 AVD 시험을 하지 않고 구현/빌드/합성 테스트만 수행했다.
과거 A01의 parkingBrake=false 기록을 이번 관측값으로 재사용하지 않았다. 현재 실제 정차신호·권한·SDK 연결 상태는 NOT_VERIFIED다.

| 단계 | 이번 판정 |
|---|---|
| 실제 SDK 객체 생성/initialize 호출 | NOT_VERIFIED: 실기 진입점 미실행 |
| 실제 onConnected/onFailed/초기화 timeout | NOT_VERIFIED: 기기 미실행. 합성 처리는 JVM PASS |
| 실제 요청 전 정차 게이트 | NOT_VERIFIED: 현재 실신호 재측정 없음. false/불명/오래됨 차단은 JVM PASS |
| 실제 requestRoute/requestRouteWithWaypoints | 장비 사유 BLOCKED, SDK 호출 자체 NOT_VERIFIED |
| 실제 onRouteStarted/onRoutingCompleted | NOT_VERIFIED |
| 기본 내비 화면/안내 시작 | NOT_VERIFIED |
| SDK stop/removeListener/release 실기 및 재진입 | NOT_VERIFIED. 소유자 합성 테스트·컴파일만 PASS |

기기 접근 실패를 SDK 연결 실패로 표현하지 않는다. 빌드 성공/시험 APK 생성은 설치·실행 성공이 아니다.

## 2. 구현과 기존 코드 재사용

- src/debug/.../NaviProbeOwner.kt: Debug-only 순수 소유자. 명시 시작, foreground·신호·권한·사용자 POI 검사, pause/상실/stop, 기존 어댑터의 결과 표시.
- src/vehicleDebug/.../NaviProbeActivity.kt: B/C 화면과 분리된 비공개 Activity. SDK 초기화/종료 버튼과 고정 신호 상태 출력. 기존 VehicleRepository + AaosVehicleSource를 읽기 전용 재사용.
- src/vehicleDebug/AndroidManifest.xml: Activity exported=false, excludeFromRecents=true. MainActivity/launcher/Intent filter에 연결하지 않음.
- src/androidTestVehicle/.../NaviProbeInstrumentation.kt: 공개 android.app.Instrumentation 기반의 명시 실행 runner. 추가 시험 라이브러리 의존성 없음.
- src/testDebug/.../NaviProbeOwnerTest.kt: 양 debug 플레이버에서 실행되는 신규 JVM12개.
- 기존 NaviClient/NaviHelperClient: 데이터 없는 고정 trace hook만 추가. 기존 SDK 연결·생성자·메서드·어댑터·정차 게이트를 재작성하지 않음.
- app/build.gradle.kts: vehicle flavor의 testInstrumentationRunner 이름만 지정.
- 기존 core/navigation, A01 센서 구현/SafetyGate, A02, B/C, Web, main Manifest는 무변경.

release 컴파일에 ProbeOwner/Activity/Instrumentation은 포함되지 않는다. main에 남는 trace enum/기본 no-op 인자는 실행 진입점이 아니다.
실제 앱의 기존 createDebugNaviAdapter를 그대로 호출하며 선택적 trace sink만 전달한다.

## 3. 실행·수명·보호 계약

Activity 생성/onResume/re-entry는 Navi initialize를 자동 실행하지 않는다.
runner도 -e a03 true 없으면 차단하고, actualRuntime()의 debug+EMULATOR+enablePleosNavi opt-in을 확인한 후에만 Activity를 연다.
release/물리기기/default OFF는 기존 클라이언트와 runner/owner에서 차단한다.

Activity는 exported=false이므로 일반 외부 앱/일반 Intent에서 실행할 수 없도록 구성했다. Intent extra로 초기화나 경로를 요청하지 않는다.
instrumentation은 별도 test APK의 개발용 진입점이며 Android의 shell/권한 있는 instrumentation 실행 경계를 사용한다. test APK를 제품 APK로 배포하지 않는다.
정적 Manifest 보호는 PASS, 실제 무단 실행 침투 시험은 NOT_VERIFIED다.

- begin(explicit=true): foreground, 동일 EMULATOR 출처의 신선한 P/UX비제한을 확인한 뒤 adapter.start.
- 초기화는 경로 요청이 아니므로 parkingBrake=true를 초기화 조건에 대신 넣지 않는다. **경로 게이트는 그대로 strict**다.
- 실제 요청은 기존 RouteRequestGate의 P/주차브레이크ON/UX해제 + 모든신호OK/동일출처/신선도, 명시적동작, NAVI_ROUTE_SEARCH grant, 유효한 USER_SELECTED POI가 모두 필요.
- SYNTHETIC_TEST 요청은 실제 Probe에서 거부한다. 테스트 좌표로 실제 요청하지 않는다.
- null POI/권한거부/주차브레이크false/불명/STALE은 SDK 경로 함수 전에 차단한다.
- 타이머는 Activity main Handler에서200ms마다 poll. 기존15초 initialize/response timeout을 재사용한다.
- onPause/onDestroy는 poll 중지→adapter.stop→repository.stop. 신호와 pending callback 세대를 폐기한다.
- 재진입은 자동 재연결하지 않으며 다시 명시 시작해야 한다.
- 초기화-only 세션에서도 UX/기어 유효성 상실 시 SDK를 중지한다. UX 제한 또는 P가 아닌 기어가 실제 확인되면 Activity도 닫는다.
- 이미 전달된 내비 경로를 강제로 cancel/replace/foreground 하지 않는다.

초기화 직전의 P/UX 신선도는 기존1500ms와 동일한 개발 기준이다. 실제 기기의 on-change 재수신 특성 때문에 오래된 snapshot이 차단되더라도 시각을 갱신해 우회하지 않는다.
SDK 동기 호출 자체가 메인 스레드를 막으면 Handler timeout이 선점하지 못한다는 기존 제약도 유지된다.

## 4. 관측 출력

SDK hook:
SDK_OBJECT_CREATED / INITIALIZE_CALLED / CONNECTED_CALLBACK / FAILED_CALLBACK /
LISTENER_REMOVED / RELEASE_RETURNED.

상태 출력:
IDLE / INITIALIZING / CONNECTED / REQUEST_CALLED / SEARCH_STARTED /
SEARCH_COMPLETED / NAVIGATION_STATUS_OBSERVED / FAILED / BLOCKED / TIMEOUT / RELEASED.

Unit 반환은 REQUEST_CALLED일 뿐 검색 성공이 아니다.
초기화/함수반환/검색/nav boolean은 누적 **관측 이력**이며 RELEASED 상태에서 현재 연결됨을 뜻하지 않는다.
전역 SDK callback과 로컬 requestId의 상관관계는 확인되지 않으며 guidanceConfirmed는 계속 false다.
RELEASE_RETURNED도 Unit 반환을 관측한 것으로 서비스의 자원 회수 ACK/인증은 아니다.

Activity report는 고정 상태·권한 bool·필수신호 값/상태/출처/receivedAtMs·cleanupFailed만 출력한다.
runner checkpoint에는 관측 wall time도 포함한다. 좌표/POI/원문 route JSON/route ID/VIN/계정/Throwable 메시지는 출력하지 않는다.
trace 메모리는64개로 제한한다. SDK hook은 비공개 SDK API가 아니라 앱 내부 관측 인자다.

## 5. 자동화 사용 방법 (미실행 절차)

아래는 **향후 장비 인계 후 개발자가 명시적으로 실행할 절차**다. 이번 작업에서 기기에 설치/실행하지 않았다.
대상 serial과 사용자를 직접 확인하고 다른 Codex가 점유 중이면 중단한다. 초기화 시험에는 경로 POI가 필요하지 않다.

~~~powershell
.\gradlew.bat -PenablePleosNavi=true :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest

# 아래 serial/user는 실제 인계받은 Pleos 장치 값으로 지정한다.
$probeSerial = '<인계받은 Pleos serial>'
$probeUser = '<현재 사용자 번호>'
adb -s $probeSerial install --user $probeUser -r app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk
adb -s $probeSerial install --user $probeUser -r -t app/build/outputs/apk/androidTest/vehicle/debug/app-vehicle-debug-androidTest.apk

adb -s $probeSerial shell am instrument --user $probeUser -w -e a03 true -e mode inspect com.chiilkkabwa.app.test/com.chiilkkabwa.app.data.navigation.NaviProbeInstrumentation
adb -s $probeSerial shell am instrument --user $probeUser -w -e a03 true -e mode initialize com.chiilkkabwa.app.test/com.chiilkkabwa.app.data.navigation.NaviProbeInstrumentation
adb -s $probeSerial shell am instrument --user $probeUser -w -e a03 true -e mode lifecycle com.chiilkkabwa.app.test/com.chiilkkabwa.app.data.navigation.NaviProbeInstrumentation
~~~

mode=inspect: Activity 생성 후 자동 initialize가 없음을 관측한다.
mode=initialize: 신선한 초기화 조건을 최대3초 기다리고 명시 시작, 최대18초 내 실제 결과를 관측한다.
mode=lifecycle: 명시 시작→finish의 실제 Activity pause/destroy→새 Activity 생성 시 자동 시작0 확인→명시 재시작.
mode=request: 같은 초기화 후 실제 게이트를 검사한다. routeJson을 주지 않으면 null POI로 차단되며 기본/합성 POI는 없다.
유효한 실제 개발용 POI와 실제 strict 게이트를 확보했을 때만 routeJson을 명시 공급한다.
구조: requestId, destination{latitude,longitude,poiName,poiId,address,poiSubId,poiProvider?},
waypoints[{position:1|2,point:{같은 필드}}]. 0/1/2개만 허용한다.
정차브레이크가 false이면 실제 요청하지 않는다. 호출된 경우에만 검색 응답을 추가 대기한다.

기본 OFF 검사는 opt-in 없이 빌드 후 명시 runner를 실행해 BLOCKED 및 SDK trace0을 확인한다.
실제 권한 거부·서비스 부재 시험은 인계받은 전용 기기에서만, 타 사용자의 권한/서비스를 변경하지 않고 수행한다.
runner의 PROBE_COMPLETED/RESULT_OK는 **자동화 절차 종료**이며 SDK/search/guidance 성공을 인증하지 않는다. 각 checkpoint를 별도로 판정한다.

## 6. 실제 수행한 로컬 검증

Windows PowerShell / Temurin17.0.20 / Gradle8.10.2 / Android SDK35, Node24.18.1.
장치조회만 수행했고 사용자 확인 이후 설치·AVD 실행·am instrument는 하지 않았다.

| 검증 | 실제 결과 |
|---|---|
| 두 JVM | 최초dev2a70830 양176/176,중간dev91413da 양190/190; 최종dev8993d25 양206/206, failures0/errors0. 기존194+A03 owner12 |
| owner 회귀 | 10개 PASS→초기화중 gear DISCONNECTED 누락11중1 FAIL→차단 수정. 주행시 view닫기 계약 compile RED→구현 후12 PASS |
| 두 debug | assembleMockDebug/assembleVehicleDebug PASS |
| 자동화 APK | assembleVehicleDebugAndroidTest PASS. 생성된 manifest targetPackage=com.chiilkkabwa.app |
| release 제외 | processVehicleReleaseMainManifest/compileVehicleReleaseKotlin PASS. Manifest와 release class에서 NaviProbe 미포함 |
| 비공개 구성 | vehicleDebug merged Manifest의 NaviProbeActivity exported=false 확인 |
| 웹 회귀 | node --test web/test/*.test.mjs 126/126 PASS |
| 정적 검사 | git diff --check PASS. A01/A02/B/C/Web/main Manifest 변경0 |
| 실제 기기 | 사용자 요청에 따라 NOT_VERIFIED. SDK 초기화/검색/안내 PASS 아님 |

실행 명령:
~~~powershell
.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest :app:processVehicleReleaseMainManifest :app:compileVehicleReleaseKotlin
node --test web/test/*.test.mjs
git diff --check
~~~

JVM12개는 기본OFF/물리·release 차단/자동시작0/브레이크false 초기화와 요청분리/불명·STALE·끊김/권한·POI/요청·검색·nav분리/중단·재진입·지연/실패·timeout/UX·기어 상실/주행시 view닫기를 포함한다.
기존 A03 26개가 경유지0/1/2·중복ID·좌표·callback순서·세대·cleanup불명 등을 계속 검증한다.
Activity 실제 수명과 SDKtrace는 컴파일만 확인했으며 JVM 호출을 실제 Android 수명 실측으로 표시하지 않는다.
서브에이전트/동료 리뷰 없이 자체 리뷰했다.

## 7. 규정·Gate

근거: [기존 SDK 연동 근거](A03_NAVIHELPER_INTEGRATION.md),
[공식 초기화/해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release),
[Android Instrumentation](https://developer.android.com/reference/android/app/Instrumentation),
SAFETY_RULES/COMPLIANCE_REGISTER의 O03,P02/P03/P06/P07/P08.

| 항목 | 이번 판정 |
|---|---|
| S01 | PASS 정적: 차량제어/VHAL/자동취소/새제어권한0 |
| S02 | 기존 공개 SDK3.0.5·공개 Android instrumentation 사용/컴파일 PASS. 실제 플랫폼 권한/승인 NOT_VERIFIED |
| S03 | PASS JVM: strict 요청게이트 유지·초기화/요청 분리·불명시차단. 실제 신호 검증 NOT_VERIFIED |
| S04 | PASS JVM/정적: foreground상실·UX/주행확인시 view종료. 실제 DO/플랫폼 실측 NOT_VERIFIED |
| S05 | NOT_VERIFIED: DO 미선언 실제 실행 정책. 주행·백그라운드 서비스를 추가하지 않음 |
| S06 | NOT_APPLICABLE: 음성/오디오 변경0 |
| S07 | PASS 보고/합성: SDK/Unit/검색/nav/출처/시각을 분리. 코스·안내 상관관계 미확정 |
| S08 | NOT_APPLICABLE: 미션·운전조작 안내 변경0 |
| S09 | PASS 정적: 좌표/원문JSON/POI/계정 로그·위치수집·새저장·서버0,trace상한64. 전체앱 개인정보 인증 아님 |
| S10 | PASS 보고: 실제 장비 미실행/합성/컴파일을 구분 |
| S11 | NOT_APPLICABLE: 새 외부라이브러리·음원·자산 없음. 실성능은 미측정 |
| S12 | main/기존브랜치·Worktree 보존. test APK 로컬 생성만, 제출/공개/릴리스 없음 |
| G1/G2 | PASS: 사용자 A03 배정·최신dev·범위·공식근거·미확인 명시 |
| G3 | PASS JVM/정적: Debug-only·비공개·기본off·물리/release차단·stop·최소정보 |
| G4 | 로컬 JVM/빌드/Web PASS, 실환경 NOT_VERIFIED |
| G5 | 비활성 Debug 기반만 dev 통합 가능. 실제 주행/실차 미검증 경로를 열지 않음 |

후속 과제: 안전하게 인계된 Pleos 장비에서 inspect→initialize→lifecycle, 실제 권한/서비스/UX 확인,
실제 브레이크ON+신선한 strict 신호·유효 POI를 확보한 뒤 request→callback→기본내비 화면 대조.
외부 장비 제약은 코드 완성/테스트를 중단시키지 않으며 실제 검색·안내 완료로 둔갑시키지 않는다.
최종 PR/병합 SHA/CI는 PR 이력과 공통 검증 로그로 추적한다.

PR #32 검증 중 A02 PR#31이dev8993d25에통합되어추가반영했다. 공통대장의append충돌은A01/A02/A03전부보존했고코드충돌0. 양JVM206/206·양debug·시험APK·release제외 PASS(11초),웹126/126 PASS. 실제AVD미실행결정과NOT_VERIFIED상태는그대로다.
