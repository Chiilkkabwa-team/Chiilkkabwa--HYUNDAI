# A03 NaviHelper 3.0.5 Android 연동

2026-11-14 KST / A 파트 Codex 2
최초 기준 origin/dev: 44bfe68c7aec5dd4cfe97cd20b1fa839c8b9ec05
브랜치: feature/a03-navihelper-integration / Worktree: Chiilkkabwa-Codex2-A03-Navi

**실제 SDK를 컴파일하는 독립 어댑터·정차 요청 경로를 구현했다. 앱에서 실제 초기화/요청/검색/안내를 수행한 것은 아니다. 기본 비활성화, 실차/release 차단이다.**

## 1. 보존·범위

A03만 구현했다. 기존 B02/C02·음성·프로필·저장소·복기/UI·A02 속도·A04 웹을 수정하지 않았다. 기존 Worktree/브랜치 및 main 보존.

기존 A02 Worktree의 A03_NAVIHELPER_FEASIBILITY.md 원본을 읽기 전용으로 확인하고 내용 동일 사본을 이 디렉터리에 추가했다. 원본/사본 SHA256:
14C861DF11C3823018429D5DA4E6E8062084DB2C76706155DA50897D21173FC0.
이 사본은 11/13 당시 조사 기록이다. 당시 “의존성 미추가/컴파일 미검증”은 역사적 상태이며 이번 구현 상태는 본 문서가 기준이다.

수정 파일:
- core/navigation/RouteRequestModels.kt: 목적지/최대2경유지/출처·입력 검증, runtime·진행 상태.
- core/navigation/RouteRequestGate.kt: 기존 SafetyGate strict + 신선도·출처 검사.
- data/navigation/NaviClient.kt: 앱 소유 테스트 포트. 추가 Pleos API를 뜻하지 않음.
- data/navigation/NaviHelperClient.kt: 실제 공개 SDK 연결·모델 변환·리스너·해제, 비활성 생성 factory.
- data/navigation/PleosNaviAdapter.kt: 명시 요청·세대별 콜백·타임아웃·실패 관리.
- test/.../data/navigation/PleosNaviAdapterTest.kt: 합성 입력/Fake 포트 JVM 테스트.
- app/build.gradle.kts: SDK 의존성1줄, debug opt-in/release false 필드2줄.
- AndroidManifest.xml: 요청 권한1개와 설명만 추가.
- A03 문서2개 및 공통 구현/검증 대장 A03 기록.

## 2. 공식 근거·실제 SDK 시그니처

기존 조사를 재사용하고 변경 관련 공식 가이드와 배포물 선언만 재확인했다.

- [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [경유지 포함 요청](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints)
- [콜백](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)
- [공식 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app/tree/76ed592487a1eab7ba102b9d4b321705426886fe)

의존성은 공식 Maven의 ai.pleos.playground:NaviHelper:3.0.5.
POM의 navi-data3.0.4/navi-aidl3.0.1 포함 전이 의존성 해석과 두 플레이버 실제 컴파일을 통과했다.
Gradle 캐시 AAR SHA256은 과거 조사와 동일:
15FADD11C7C611B1ABDF7EBF9CE91F2A6CE2728E60B4E38B4343E11BB9C12E97.
벤더 AAR/소스/다운로드 로그를 저장소에 넣지 않았다.

사용한 공개 진입점 ai.pleos.playground.navi.helper.NaviHelper:
~~~kotlin
NaviHelper(context: Context)
fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit): Unit
fun addListener(listener: NaviHelperEventListener): Unit
fun requestRoute(routeInfo: RouteInfo): Unit
fun requestRouteWithWaypoints(request: RouteWithWaypointsRequest): Unit
fun removeListener(listener: NaviHelperEventListener): Unit
fun release(): Unit
~~~

리스너에서 사용하는 공개 선언:
~~~kotlin
fun onRouteStarted(routeStartInfo: RouteStartInfo): Unit
fun onRoutingCompleted(): Unit
fun onError(errorCode: NaviErrorCode): Unit
fun onNavigationStatus(status: NavigationStatus): Unit
~~~

검색 콜백에 requestId 또는 검색 성공 결과 객체가 없다. Unit 반환·초기 연결 callback을 검색/안내 성공으로 대체하지 않는다.

모델은 ai.pleos.playground.navi.data의 RouteInfo, RequestWaypointInfo, RouteWithWaypointsRequest다.
실제 navi-data 생성자에서 **latitude: Double 다음 longitude: Double**이며 모두 named argument로 변환했다.
목적지 필수 poiName/poiId/address/poiSubId, 선택 poiProvider(default empty)를 전달한다.
경유지의 실제 poiName은 nullable이나 앱 입력에서는 빈 이름을 허용하지 않는 보수적 검증을 한다.
WaypointIndex.FIRST/SECOND만 요청에 사용하고 ALL은 사용하지 않는다.
코드에서 SDK 내부 API·리플렉션·위치 조회·기본 앱 강제 전면화·cancelRoute·안내 시작 API를 호출하지 않는다.

## 3. 최소 권한·공식 허용의 경계

추가한 권한은 pleos.car.permission.NAVI_ROUTE_SEARCH 하나다.
권한 가이드/실물 @RequiresPermission과 대조했고 실제 클라이언트는 호출 전 checkSelfPermission으로 재확인한다.
거부 시 PERMISSION_DENIED로 끝내며 우회하거나 가짜 성공을 만들지 않는다.

get*Info/requestTrip과 requestNavigationScreen을 사용하지 않아 NAVI_ROUTE/NAVI_CUSTOM_MAP이 필요하지 않다.
NAVI_CUSTOM_ROUTE/NAVI_CUSTOM_ETC/CONTROL_CAR_*도 추가하지 않았다.
SDK AAR manifest의 서비스 조회 queries/버전 메타데이터는 정상 manifest merger로 들어오며 지원 보장을 뜻하지 않는다.

배포 소스의 요청 KDoc은 Normal 권한으로 설명하나 **실제 Pleos 이미지의 protectionLevel/grant·앱 카테고리·승인은 NOT_VERIFIED**다.
공식 가져오기/릴리즈 가이드의 공개 SDK를 사용한다. 공모전 O03 재열람은 도구 접근 오류로 실패하여 기존 COMPLIANCE_REGISTER의 근거를 참고했으며, 새 공모전 승인·스토어 승인·상업 배포/재배포 라이선스를 취득했다고 주장하지 않는다.
이번은 비활성 개발 통합이며 APK 제출·공개 배포는 하지 않았다.

## 4. 입력·안전 게이트

PracticeRouteRequest는 requestId, 최종 RoutePoint, 순서가 명시된 RouteWaypoint 목록, RouteDataSource(USER_SELECTED/SYNTHETIC_TEST)로 구성된다.
시험 좌표37/127 등과 SYNTHETIC_* ID는 합성 데이터다. 유효한 실 POI라는 뜻이 아니며 실제 요청에 사용하지 않았다.
생성/선택한 요청을 메모리에서 SDK로 전달할 뿐 위치를 수집하거나 정밀 궤적을 저장하지 않는다.

검사:
- 비어 있거나128자를 넘는 요청ID 거부(앱 개발 제한).
- 유한 위도[-90,90]/경도[-180,180], 필수 문자열 nonblank.
- 경유지0~2개. 목록 순서/position은 [1] 또는 [1,2]만 허용하며 임의 자동 정렬하지 않음.
- 목적지 포함 동일 좌표·동일(provider,poiId,poiSubId) 거부. signed zero도 같은 좌표로 처리.
- 전달 전 리스트 snapshot으로 호출자의 사후변경과 분리.
- 이 검사는 POI 존재/지도·도로 적합성/실제 주행 안전을 인증하지 않는다.

RouteRequestGate는 VehicleSnapshot의 gear/parkingBrakeOn/uxRestricted를 사용한다.
**모두 isUsable(OK+nonnull), 동일 EMULATOR 출처, receivedAtMs가 미래가 아니고1500ms 이내일 때**, 기존 SafetyGate 기본 strict 정책으로 P + 주차브레이크ON + UX비제한을 확인한다.
속도0·미지원브레이크 예외·경고등·Mock 신호로 대체하지 않는다.
미지원/불명/권한거부/끊김/STALE은 차단한다.1500ms는 개발용 설정이며 차량 정책의 공식 숫자가 아니다.

요청 중 게이트 상실/신선도 만료 시 로컬 세션을 BLOCKED로 종료하고 해제한다.
이미 전달된 요청의 기본 내비 처리는 계속될 수 있다. **기존 내비 경로를 지우거나 되돌리는 cancelRoute를 자동 호출하지 않는다.**

## 5. 생명주기·콜백·호출 계약

모든 소유자 호출은 한 실행 문맥(Android main)에서 수행한다. 실제 SDK callback도 main Handler에 전달한 뒤 클라이언트/어댑터 세대를 각각 검사한다.

- createDebugNaviAdapter(context)는 생성만 하며 초기화/요청하지 않는다.
- start(): 허용된 환경에서 새 클라이언트 생성·리스너 등록·콜백형 initialize. Connected가 와야 initialized 관측값이 true.
- updateSafety(snapshot): A03 소유자가 받은 실제 스냅샷 전달. 연결 시작 시 이전 스냅샷은 폐기한다.
- request(route, userInitiated=true): 사용자 명시 동작·연결·현재 안전·데이터 확인 후1회만 SDK 요청. 경유지0개는 requestRoute,1~2개는 requestRouteWithWaypoints.
- 반환 true/callReturned는 **함수 반환 관측**이다. 검색 성공이 아니다.
- searchStarted/searchCompleted/navigationStatus는 각각 별도 callback 관측이다. 늦게 온 Started가 Completed를 지우지 않는다. 요청 전의 검색 이벤트는 무시한다.
- 실제 SDK 전역 이벤트에는 요청ID가 없으므로 requestId는 로컬 추적 키일 뿐이다. callbackCorrelationVerified/guidanceConfirmed는 항상 false다.
- ROUTE_DRIVE 상태가 관측되어도 기존 경로나 다른 앱 요청일 수 있으므로 해당 코스의 실제 안내 시작을 확정하지 않는다.
- stop(): 세대 무효화→동일 리스너 제거→release. 중복 stop과 해제 이후 callback은 무해하다.
- 재시작은 사용자/소유자의 명시적 stop/start만 가능. 한 연결에1요청이며 자동 재연결/재요청하지 않는다.
- 소비된 ID256개까지 보존하고 상한 도달 시 신규 요청 차단. 실패 요청을 ID 재사용으로 자동 재생하지 않는다.
- close에서 예외가 나면 cleanupFailed를 보존하고 새 클라이언트 생성을 차단한다. Unit release가 실제 서비스 자원 회수를 증명하는 것은 아니므로 실환경 해제 실측은 미검증이다.

초기화15초/검색 callback15초를 기다린 뒤 tick()에서 timeout 처리한다. 이는 SDK 보장값이 아닌 개발 설정이다.
소유자가 tick을 폴링해야 하며 이 순수 어댑터가 타이머나 서비스를 자동 시작하지 않는다. 동기 SDK 호출이 실제로 블록하면 tick이 이를 선점하지 못한다.
Signal 신선도는 벽시계(receivedAtMs와 같은 기준), timeout은 SystemClock.elapsedRealtime을 쓴다.
단조 시계 역전 시 세션을 폐기한다.

권한거부/UnsupportedOperationException/관측한 RemoteException은 각각 분리한다.
공개 SDK에 서비스 끊김 callback/기능별 Capability 조회가 없으므로 이를 만들어 사용하지 않는다.
SDK가 전송 오류를 내부 로그로 삼키는 경우 어댑터는 이를 즉시 알 수 없고 RESPONSE_TIMEOUT으로 처리한다. 타임아웃을 연결 끊김/미지원의 확정 증거로 표시하지 않는다.
onError에는 고정 enum 이름만 보존하고 Throwable 메시지·목적지·좌표를 진단 로그로 출력하지 않는다.

## 6. 기본 차단·시험 진입점

NaviRuntime 기본값은 disabled다. debug + 명시 -PenablePleosNavi=true + EMULATOR가 모두 필요하다.
실제 SDK 클라이언트도 BuildConfig.DEBUG/PLEOS_NAVI_ENABLED와 실제 Build.HARDWARE(ranchu/goldfish)를 독립 재확인한다.
이는 개발기기 식별 기준이며 보안 attestation/실차 인증으로 표현하지 않는다. release 플래그는 opt-in을 주어도 항상 false다.

공통 B/C 화면·ViewModel·Activity에 연결하지 않았다. 독립 factory/API만 제공하며 실제 UI 시험 진입점은 보류했다.
현재 연결된 emulator-5560은 타 작업용 일반 Android이며 점유/설치하지 않았다. Pleos A01 AVD는 목록에 있지만 실행중이 아니었다.
이전 A01 기록에서도 실제 주차브레이크ON 전달이 미검증이므로, 실제 정차 게이트를 확보하지 못한 상태에서 조건을 완화해 경로를 호출하지 않았다.
따라서 현재는 테스트 가능한 요청 경로·SDK 연결 코드까지 완성했고 실제 앱 호출은 NOT_VERIFIED다.

후속 명시 시험 순서:
1. 다른 작업과 분리한 Pleos2.1.0 환경·NaviService/기본내비 버전·권한·지도/네트워크/약관·유효 POI 확보.
2. 실제 P/브레이크ON/UX비제한과 출처·신선도를 확인할 A03 전용 소유자 구성. 부재하면 중단.
3. 소유자가 factory→start→tick/updateSafety를 관리하고 사용자가 명시 요청할 때만 request 호출.
4. 초기 연결/함수 반환/검색시작/검색완료/nav상태를 별도 증거로 저장. 개인정보·실제 궤적은 배제.
5. 기본 내비 화면·목적지/경유지 대조 및 안내 활성화를 직접 관찰한 뒤 실제 안내 시작을 별도 판정.
6. gate/lifecycle 상실·권한거부·서비스 부재·실패·timeout·외부경로변경을 시험하고 종료 시 stop. 자동취소/자동안내 없음.

## 7. 검증 등급과 결과

| 검증 | 결과 |
|---|---|
| SDK 클래스/메서드 존재·실제 생성자 컴파일 | PASS: 공식 AAR/소스/javap 기존증거+이번 양플레이버 실컴파일·모델 매핑시험 |
| 1. 앱 실제 SDK 초기화 성공 | NOT_VERIFIED. Fake Connected 처리만 JVM PASS |
| 2. 앱 실제 경로 함수 호출 완료 | NOT_VERIFIED. Fake 호출/Unit 상태 분리만 JVM PASS |
| 3. 실제 탐색 시작 callback | NOT_VERIFIED |
| 4. 실제 탐색 완료 callback/요청코스 검색 응답 | NOT_VERIFIED |
| 5. 실제 안내 시작/기본내비 상태 변화 | NOT_VERIFIED. 합성 ROUTE_DRIVE는 실제 관측 아님 |
| JVM mock/vehicle | 최신dev0209187에서 각각164/164 PASS, 신규 A03 26개 포함 |
| 두 debug APK | assembleMockDebug/assembleVehicleDebug PASS |
| 웹 회귀 |110/110 PASS, 웹 변경 없음 |
| opt-in/release | -PenablePleosNavi=true로 vehicleDebug 플래그true / vehicleRelease false 생성 확인 |
| 실제 Pleos/실차 | 미실행 NOT_VERIFIED. 앱 설치·SDK 호출 없음 |

실행 명령:
~~~powershell
.\gradlew.bat :app:testMockDebugUnitTest --tests '*PleosNaviAdapterTest'
.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug
.\gradlew.bat --offline -PenablePleosNavi=true :app:generateVehicleDebugBuildConfig :app:generateVehicleReleaseBuildConfig
node --test web/test/*.test.mjs
~~~

환경 Windows/PowerShell, Temurin17.0.20, Gradle8.10.2, Android35, Node24.18.1.
TDD: 타입 부재 compile FAIL→23개 PASS→signed-zero 회귀 포함26개 중1개 FAIL→수정 후26개 PASS.
동료/서브에이전트 리뷰로 주장하지 않고 전체 변경 자체 리뷰를 수행했다.
기존 AAOS deprecated 경고는 보호파일을 수정해 처리하지 않았다.

## 8. 규정·게이트와 통합 경계

| 항목 | 판정(이번 범위 한정) |
|---|---|
| S01 | PASS 정적: 차량 제어/취소/설정 API0, CONTROL_CAR 권한0 |
| S02 | 공개 SDK·버전·사용 선언·실컴파일 PASS. O03 원문 재조회·실차 지원·권한승인/배포권리 NOT_VERIFIED. 비공개/리플렉션 없음 |
| S03 | PASS JVM: strict 정차·상태·신선도·출처·게이트상실 차단. 실제 게이트 전달 NOT_VERIFIED |
| S04 | PASS 정적: 공통 UI·주행터치·DO 변경0. 플랫폼 실환경 UX NOT_VERIFIED |
| S07 | PASS JVM/보고: 함수/검색/nav 관측·상관관계·출처 구분. 안내성공 추측0 |
| S09 | PASS 정적: 위치수집/저장/서버/원본로그0. 메모리 ID상한. 앱전체 개인정보 인증 아님 |
| S10 | PASS 보고: 합성 테스트/실컴파일/앱/Emulator/실차 분리 |
| S12 | main/기존작업 보존, 제출·공개·릴리스 없음. 제출 적합성 인증 NOT_APPLICABLE |
| G1/G2 | PASS 조사·범위·공개근거·미확인 명시. 원문 접근 실패를 승인으로 둔갑시키지 않음 |
| G3 | PASS 비활성 설계·JVM: live/release/default 차단, 게이트/실패/세대격리 |
| G4 | JVM/빌드/웹만 PASS. 실제 초기화/검색/안내·자원회수 NOT_VERIFIED |
| G5 | 기본off의 독립 기반만 조건부 dev 통합. 최신dev·충돌·관련검증·PRCI 확인 후 병합 |

규정근거는 SAFETY_RULES/COMPLIANCE_REGISTER의 O03,P02/P03(주행제한),P06(API가용성),P07/P08(개인정보·보안) 및 위 공식 SDK 문서.
공통 대장은 최신 dev 확인 후 기존 기록에 append한다. PR/병합/병합 후 CI 결과는 검증 로그와 PR 이력으로 추적한다.
남은 과제는 실제 정차/플랫폼/기본 내비 환경·유효 POI·안전한 독립 시험 소유자·실초기화/검색/코스대조/안내 실측이다.


## 최신 통합 재검증

A02 PR#27의dev02091872cf77650051318aa844517c0f3c033d19를 반영했다. Gradle충돌은두기능의플래그를모두보존했다. A03 debug필드는 Boolean.parseBoolean으로비상수화했고 실제소비자바이트코드의 getstatic을 ON→OFF 전환양쪽에서확인했다. 생성소스만바뀌고이전인라인소비자가남은초기검증실패도재컴파일후해소했다. release는opt-in에도false다. 최신기준양JVM164/164·양debug·Web110/110 PASS. A02·B/C 기능변경은없다. 기존조사사본의Markdown줄바꿈/EOF공백은원형보존하고새코드·통합문서의whitespace만검사했다.

최종 출력 계약: NaviProgress에도 signalSource와 requestDataSource를 보존한다. 합성 코스가 실제 실차 요청으로 오인되지 않도록 출처 필드 부재 RED를 확인한 뒤 추가했으며, 최신 양JVM164/164·양debug PASS(5초).

## A03 Debug Probe 후속 (2026-11-15)

[Debug 시험 소유자·Activity·instrumentation·단계별 판정](A03_EMULATOR_VERIFICATION.md)을 추가했다. 기존의 진입점 보류는 vehicleDebug 비공개 Activity와 명시 runner로 보완했다. SDK/어댑터/strict 요청 게이트는 재작성하지 않았고 default OFF·실차/release 차단 유지. 실제 Pleos AVD는 다른 Codex가 사용 중이며 사용자가 실측 미검증 진행을 선택하여 설치/초기화/요청/검색/안내를 실행하지 않았다. 로컬 양JVM176/176(신규12),양debug·시험APK·release제외,웹126/126 PASS. 이 결과는 실제 onConnected/검색/안내 증거가 아니다.