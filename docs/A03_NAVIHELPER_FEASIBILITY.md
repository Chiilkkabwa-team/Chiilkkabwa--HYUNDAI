# A03 NaviHelper 연동 사전 조사

조사일: 2026-11-13 KST  
담당 범위: A03 연습 코스의 목적지·경유지를 기본 내비에 전달하기 위한 **조사와 설계 문서만**.  
작업 브랜치: `docs/a03-navihelper-feasibility`  
기준: 원격 `origin/dev` = `1140c7487f9e5cde39be1ecc41e7caef8d25e145` (A02 통합 포함). 작성 전 원격 조회로 확인.

## 1. 결론과 검증 단계

**공식 문서·공식 샘플·공식 Maven 배포물에서 NaviHelper 3.0.5와 경유지 포함 경로 요청 API를 확인했다.** 실제 앱 호출, 검색 응답, 기본 내비 안내 시작은 이번에 실행하지 않았다. 따라서 SDK 존재 확인을 A03 기능 완료로 취급하지 않는다.

| 단계 | 현재 상태 | 확보한 증거 / 다음 PASS 조건 |
|---|---|---|
| 1. SDK 클래스·메서드 존재 | **PASS (배포물 정적 확인)** | 공식 AAR·sources·POM 다운로드, `javap -public`와 소스 대조. 앱 컴파일 성공을 뜻하지 않음 |
| 2. 앱에서 API 호출 성공 | **NOT_VERIFIED** | 향후 설치된 앱에서 서비스 연결·권한 확인 후 호출 기록과 오류 관찰. 예외 없는 반환도 원격 전달 보장은 아님 |
| 3. 경로 검색 요청·응답 성공 | **NOT_VERIFIED** | 향후 검색 콜백과 목적지·경유지 응답을 실제 요청과 대조. 검색 시작 이벤트만으로 PASS 금지 |
| 4. 기본 내비에서 실제 안내 시작 | **NOT_VERIFIED** | 향후 요청한 코스가 기본 내비에 표시되고 실제 안내 상태가 시작되는 화면·콜백 증거 확보 |

이번 변경은 이 문서 1개다. 앱 코드, 의존성, 권한, 공유 모델, A01 파일을 변경하지 않았다. Gradle 빌드·앱 실행·ADB·에뮬레이터 조작도 하지 않았다. A01 에뮬레이터의 차량 신호 수신 기록은 **A03 내비 검증 증거가 아니다**. `dev` 병합·푸시·`main` 변경은 이번 범위에 포함하지 않는다.

## 2. 공식 근거와 조사 대상

공식 웹 문서는 조사 당시 내용이며 이후 변경될 수 있다. 샘플은 다음 커밋에 고정했다.

- [공식 샘플 저장소](https://github.com/Pleos-Playground/pleos-sample-navi-app/tree/76ed592487a1eab7ba102b9d4b321705426886fe)
- [샘플 README](https://github.com/Pleos-Playground/pleos-sample-navi-app/blob/76ed592487a1eab7ba102b9d4b321705426886fe/README.md)
- [샘플 ViewModel](https://github.com/Pleos-Playground/pleos-sample-navi-app/blob/76ed592487a1eab7ba102b9d4b321705426886fe/app/src/main/java/ai/pleos/playground/navidevapp/screen/vm/NaviHelperViewModel.kt)
- [샘플 요청 데이터](https://github.com/Pleos-Playground/pleos-sample-navi-app/blob/76ed592487a1eab7ba102b9d4b321705426886fe/app/src/main/java/ai/pleos/playground/navidevapp/data/sample/NaviTestData.kt)
- [SDK 소개·구조](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [이벤트 리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [목적지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [경유지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)
- [경유지 포함 경로 요청](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints)
- [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling)
- [API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/entry)
- [Pleos Emulator 설치·설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

API Reference 일부 페이지의 본문 추출만으로 전체 시그니처를 확인할 수 없어 **공식 배포물의 공개 선언을 대조**했다. 내부 API가 바이너리에 노출되어 있어도 사용 허용 근거로 삼지 않는다.

### 2.1 SDK 버전과 실제 가용성

공식 Maven 저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`, 좌표는 `ai.pleos.playground:NaviHelper:3.0.5`다. 아래 배포물을 프로젝트 밖 조사용 임시 디렉터리에 내려받았으며 HTTP 200을 확인했다. 기존 로컬 Gradle 캐시에서는 NaviHelper를 찾지 못했다. 앱 의존성 추가나 Gradle 캐시 설치는 하지 않았다.

| 배포물 | 확인 결과 |
|---|---|
| NaviHelper 3.0.5 AAR / sources JAR / POM / module | 다운로드 가능, 공개 클래스·선언 확인 |
| navi-data 3.0.4 AAR / sources JAR / POM | 다운로드 가능, 요청·응답 모델과 enum 확인 |
| NaviHelper POM 의존성 | compile: navi-data 3.0.4, Kotlin stdlib 1.9.25; runtime: navi-aidl 3.0.1, core-ktx 1.8.0, coroutines-android 1.6.3 |
| 전체 전이 의존성 Gradle 해석·앱 컴파일 | **NOT_VERIFIED**. 개별 배포물 다운로드와 구분 |
| 현재 에뮬레이터 NaviService·기본 내비 설치 및 호환성 | **NOT_VERIFIED** |
| 공모전/스토어 승인·실차 지원 | **NOT_VERIFIED**. 공개 SDK 확인으로 승인 취득을 주장하지 않음 |

직접 배포물 출처:

- [NaviHelper AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.aar)
- [NaviHelper 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar)
- [NaviHelper POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.pom)
- [navi-data 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar)

SDK AAR manifest의 minSdk는 23이고 navi-data도 23이다. 플랫폼 개발 환경 문서는 **API 28 이상**을 요구한다. 공식 샘플의 minSdk 33은 샘플 설정이므로 SDK나 플랫폼 최소 버전과 혼동하지 않는다. 기존 앱 minSdk 28에서 실제 전이 의존성·manifest 병합·실행 호환성은 **NOT_VERIFIED**다.

Helper manifest에는 서비스 최소 버전 메타데이터 `min_version_name=2.0.1`, `min_version_code=11`, SDK 현재 버전 `3.0.5/75`가 있다. 이는 배포물의 선언이며 현재 기기의 서비스 버전 확인이나 모든 API 지원 보장이 아니다.

## 3. 공식 API 시그니처

아래는 배포물 소스의 **공개 선언을 Kotlin 표기로 정리**한 것이다. 기본 인자·null 허용 여부는 sources JAR로, JVM 메서드·생성자 존재는 `javap`로 대조했다. 실행 예제가 아니며 프로젝트에 구현하지 않았다.

패키지: `ai.pleos.playground.navi.helper.NaviHelper`

~~~kotlin
NaviHelper(context: android.content.Context)

fun initialize(): Unit
fun initialize(
    onConnected: () -> Unit,
    onFailed: (Throwable) -> Unit,
): Unit
fun addListener(listener: NaviHelperEventListener): Unit
fun removeListener(listener: NaviHelperEventListener): Unit
fun release(): Unit

fun requestRoute(routeInfo: RouteInfo): Unit
fun requestRouteWithWaypoints(request: RouteWithWaypointsRequest): Unit
fun getDestinationInfo(): Unit
fun getWaypointInfo(): Unit
fun getRouteStateInfo(): Unit
fun requestNavigationScreen(requestScreen: NavigationScreenRequest): Unit
~~~

`requestRouteWithWaypoints`의 JVM 선언도 `public final void requestRouteWithWaypoints(ai.pleos.playground.navi.data.RouteWithWaypointsRequest)`다. Boolean, RouteResult, requestId 또는 호출별 성공/실패 람다를 반환하지 않는다. `initialize`의 연결 콜백을 경로 요청 콜백으로 오인하지 않는다.

리스너 패키지: `ai.pleos.playground.navi.helper.listener.NaviHelperEventListener`  
모델: `ai.pleos.playground.navi.data` / enum: `ai.pleos.playground.navi.constants`

~~~kotlin
fun onRouteStarted(routeStartInfo: RouteStartInfo): Unit
fun onRoutingCompleted(): Unit
fun onRouteCancelled(): Unit
fun onDestinationChanged(): Unit
fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo): Unit
fun onDestinationInfo(destinationInfo: DestinationInfo): Unit
fun onWayPointInfo(waypointInfoList: List<WaypointInfo>): Unit
fun onRouteStateInfo(routeStateInfo: RouteStateInfo): Unit
fun onNavigationStatus(status: NavigationStatus): Unit
fun onError(errorCode: NaviErrorCode): Unit
~~~

위 메서드는 기본 구현이 있는 인터페이스 콜백의 일부다. `onWayPointInfo`의 대문자 P가 실제 선언이다. 구형 `onRouteEnded()`는 deprecated이므로 신규 설계는 `onRoutingCompleted()`를 기준으로 한다. 콜백 순서·발생 여부는 내비 구현과 상태에 의존한다.

## 4. 목적지·경유지 데이터 모델

navi-data 3.0.4 공개 생성자 선언 기준이다.

~~~kotlin
data class RouteInfo(
    val latitude: Double,
    val longitude: Double,
    val displayLatitude: Double? = null,
    val displayLongitude: Double? = null,
    val poiName: String,
    val poiId: String,
    val address: String,
    val poiSubId: String,
    val routeOption: RouteOption = RouteOption.RECOMMENDED,
    val poiProvider: String = "",
    val isMobileRequest: Boolean = false,
)

data class RequestWaypointInfo(
    val latitude: Double,
    val longitude: Double,
    val poiName: String?,
    val waypointIndex: WaypointIndex,
    val poiId: String,
    val address: String,
    val poiSubId: String,
    val poiProvider: String = "",
)

data class RouteWithWaypointsRequest(
    val destination: RouteInfo,
    val waypoints: List<RequestWaypointInfo>,
)
~~~

- 최종 목적지 1개와 경유지 리스트를 별도로 전달한다. 좌표 단위는 도(degree), 위도 범위 -90~90, 경도 -180~180이다. display 좌표는 지도 표시용 nullable 값이다.
- 공식 경유지 가이드는 **최대 2개**, `WaypointIndex.FIRST`와 `SECOND`를 설명한다. enum의 `ALL`은 제거 요청에 사용하며 경유지 항목의 순서 값으로 사용하지 않는다.
- `poiName`은 목적지에서는 non-null, 경유지에서는 nullable이지만 기본 인자는 없다. `poiId/address/poiSubId`도 생성자에 필수다. 빈 문자열을 넣어 좌표만 전달해도 검색된다는 보장은 **NOT_VERIFIED**다.
- 생성자 선언 자체에는 좌표·개수 검증 코드가 없다. NaN/무한대/범위 초과·3개 이상·중복 index를 앱의 순수 모델 검증 단계에서 차단하는 방안을 제안한다. 0개는 `requestRoute` 사용으로 분기하는 설계 후보이며, `requestRouteWithWaypoints(emptyList())`의 허용 여부는 **NOT_VERIFIED**다.
- 일부 공식 가이드 Java 예시는 Kotlin named argument 예시와 좌표 위치를 혼동할 여지가 있다. 실제 선언은 **latitude 다음 longitude**이므로 Kotlin named argument를 사용하고 변환 테스트로 방어한다.
- 유효한 POI 공급자·ID를 확보하는 방법과 sample POI의 현재 유효성은 **NOT_VERIFIED**다. ID를 창작하지 않는다.

응답은 요청 모델과 다르다. `DestinationInfo`에는 좌표·이름·주소·남은 거리/시간·통행료·POI 필드가 있으며, `WaypointInfo`에는 좌표·이름·주소·남은 거리/시간·POI 필드가 있다. 수신된 목적지·경유지를 요청 내용과 비교하고, 입력했다는 사실만으로 원하는 도로를 반드시 경유한다고 표시하지 않는다.

## 5. 호출·콜백·기본 내비 흐름

`치일까봐 → NaviHelper → NaviService → 기본 Navigation 앱(NaviProvider 구현) → 이벤트 콜백` 구조다. NaviHelper는 앱 안에서 지도·경로 탐색 엔진을 직접 구현하는 SDK가 아니다. 기본 내비 앱과 서비스가 설치·설정되어 있어야 하며 샘플도 이 전제를 설명한다. 특정 패키지명을 하드코딩하거나 일반 지도 Intent로 대체하는 방식은 이번 조사에서 확인하지 않았다.

향후 구현 흐름 제안:

1. 정차 안전 게이트·신선도·사용자의 명시적 코스 전달 의사를 확인하고 요청 데이터를 검증한다. 안전 불명 시 실행하지 않는다.
2. 단일 세션에서 Helper를 생성하고 동일 인스턴스의 리스너를 등록한다. 이벤트 누락을 줄이기 위해 초기화 전 리스너 등록 순서를 시험한다.
3. 콜백형 `initialize`의 `onConnected`로 첫 서비스 연결을 확인한 뒤 요청한다. `onFailed`는 초기 연결 재시도 소진 실패다. 연결 성공을 경로 검색 성공으로 표시하지 않는다.
4. `requestRouteWithWaypoints(request)`를 1회 호출하고 호출 시각·세션·요청 출처를 기록한다. 중복 클릭·동시 요청은 차단한다.
5. `onRouteStarted`는 공식 가이드/리스너 소스에서 **검색 시작**으로 설명한다. 이름만 보고 안내 시작으로 승격하지 않는다. `RouteStartInfo`에 거리·시간 값이 있어도 마찬가지다.
6. `onRoutingCompleted`를 검색 완료 이벤트로 기록하고 `getDestinationInfo/getWaypointInfo/getRouteStateInfo`와 대응 콜백으로 요청 코스를 대조한다. 완료 콜백에는 성공 결과 객체나 requestId가 없다.
7. 필요 시 `requestNavigationScreen(NavigationScreenRequest.Foreground)`로 내비 전면 표시를 요청할 수 있다. 이는 공개 모델에 존재하지만 **전면 표시 요청은 안내 시작 명령이나 증거가 아니다**.
8. `onNavigationStatus(NavigationStatus.ROUTE_DRIVE)` 및 요청한 목적지·경유지가 표시된 기본 내비 화면을 함께 확인한 경우에만 실제 안내 시작 증거로 기록한다. 기존에 안내 중인 다른 경로의 상태일 가능성도 배제한다.
9. 종료 시 등록했던 동일 리스너를 제거하고 `release()`. 다시 사용할 때 새 Helper 생성·초기화로 시작한다. 백그라운드/재연결/해제 후 늦은 이벤트는 세션 기준으로 폐기한다.

`NavigationStatus`의 실제 값은 `INACTIVE, INITIALIZING, FREE_DRIVE, ROUTE_DRIVE`다. `RouteStateInfo`에는 `termsAgreement/naviMode/isRouting/routeOption` 등이 있지만 한 필드만으로 안내 시작이나 요청 코스 일치를 확정하지 않는다.

**요청 상관관계 한계:** 조사한 경로 요청과 위 콜백에는 호출별 requestId가 없다. 앱 자체 세션 ID는 추적 보조일 뿐 SDK의 응답 식별자가 아니다. 기본 내비의 외부 조작·다른 앱 요청과 콜백이 섞이는 상황은 아직 검증하지 않았다.

### 5.1 실패 처리

공식 소스의 권한 검사는 미허용 시 `SecurityException`을 던진다. 서비스 전송의 일부 `RemoteException`과 연결 실패는 SDK 내부에서 로그로 처리되므로 호출자의 try/catch만으로 모든 실패를 탐지할 수 없다. 호출 반환 후 콜백이 없으면 성공을 유지하지 않고 앱이 정한 대기 제한 후 **응답 미확인**으로 표시한다. 이 제한 시간은 SDK 공식 보장값이 아니며 향후 설정·시험 대상이다.

`NaviErrorCode` 실물에는 `CAUSE_LOW_BATTERY`, `CAUSE_SEARCH_ROUTE_OVER_DISTANCE`, `CAUSE_SEARCH_ROUTE`, `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE`, `CAUSE_SEARCH_TRIP`이 있다. 연결 실패와 검색 오류를 별도 상태로 처리한다. 권한 거부·서비스 부재·내비 부재·네트워크 문제를 모두 동일 오류로 추정하지 않는다. 자동 재시도로 기존 경로를 반복 덮어쓰지 않는다.

## 6. 권한·Capability·제한사항

| 공개 API 범위 | 필요한 권한 | 이번 조치 |
|---|---|---|
| `requestRoute/requestRouteWithWaypoints` | `pleos.car.permission.NAVI_ROUTE_SEARCH` | 선언하지 않음 |
| 목적지·경유지·경로 상태 조회 | `pleos.car.permission.NAVI_ROUTE` | 선언하지 않음 |
| `requestNavigationScreen` | `pleos.car.permission.NAVI_CUSTOM_MAP` | 선언하지 않음, 전면 표시 필요 시에만 검토 |

권한 가이드와 Helper의 `@RequiresPermission`·권한 검사 구현을 대조했다. 배포 소스의 경로 요청 KDoc은 보호 수준을 Normal로 설명한다. **실제 에뮬레이터의 권한 정의·grant 상태·기기별 정책은 NOT_VERIFIED**이며 Manifest 선언만으로 모든 환경에서 허용된다고 단정하지 않는다.

공식 샘플 Manifest에는 `NAVI_CUSTOM_ROUTE/NAVI_CUSTOM_ETC`도 있으나 A03 공개 요청에 필요하다는 근거로 사용하지 않는다. Helper 소스는 전자를 과거 호환 용도, 후자를 내부 API 용도로 설명한다. 샘플의 전체 권한이나 private/internal API를 복사하지 않는다. 차량 제어 권한은 필요 범위가 아니며 추가하지 않는다.

공개 `NaviHelper` API 목록에는 `getCapabilities/isSupported` 같은 조회 메서드가 없다. 따라서 Vehicle SDK의 Capability 구조를 그대로 대입하거나 새로운 메서드를 만들어 쓰지 않는다. 별도의 플랫폼 Capability 선언·앱 카테고리 승인·차종별 지원 정책이 필요한지는 **NOT_VERIFIED**다. 서비스 연결 확인은 API별 지원 보장이 아니다.

경로 요청은 **내비의 목적지/경로 상태를 변경**한다. 차량 액추에이터 제어와는 다르지만 SDK 전체를 읽기 전용으로 설명하지 않는다. A03은 사용자가 선택한 코스를 기본 내비로 전달하는 범위로 제한하고 기존 안내 교체·확인 UI는 시험 후 정한다. 자체 Navigation 앱의 카테고리/권한을 자동으로 적용하지 않는다.

## 7. Pleos Emulator 검증 방법 (향후 계획, 실행하지 않음)

공식 NaviHelper 소개는 Pleos Emulator/AVD 개발 절차를 안내한다. 설치 문서의 환경은 Pleos Connect 2.1.0, API 34, Cold boot·RAM 4GB다. 이는 **공식 개발 경로가 있다는 근거**이며 특정 이미지에 정상 동작하는 기본 내비와 지도 데이터가 포함되어 있다는 실측 증거는 아니다.

A01 작업 중인 에뮬레이터를 재시작·재설정하지 않고, 후속 A03 검증 시간을 분리한다.

1. 실행 대상 serial/AVD·이미지·앱 commit/SDK 버전·NaviService/기본 내비 버전을 기록한다. 서비스·기본 내비 설치/설정 방법과 지원 조합을 공식 자료로 재확인한다. 임의 APK·서비스 패치·권한 우회 설치는 하지 않는다.
2. 정차 게이트·앱 카테고리·UX 제한을 확인한다. 지도 데이터·네트워크·약관 동의·현재 위치 유효성을 확인한다. 위치를 주입해야 한다면 공식 지원 방법 확인 전 **NOT_VERIFIED**로 둔다.
3. 허용된 시험용 코스와 1개/2개 경유지를 선택해 기본 내비 단독 검색이 가능한지 먼저 확인한다.
4. 후속 구현 앱에서 단계 2(연결/호출), 단계 3(검색 완료·내용 대조), 단계 4(실제 안내 상태·화면)를 각각 기록한다. 콜백 명·단조 시각·세션과 화면 캡처를 연결한다.
5. 서비스/내비 없음, 권한 없음, 잘못된 좌표/POI, 중복 요청, 검색 지연/오류, 내비 외부 조작, 앱 백그라운드/해제, 이미 안내 중인 상황을 시험한다. 권한 없는 대체 테스트 빌드 등 재현 방법은 후속 구현에서 결정한다.
6. Virtual Console은 A01 차량 컨텍스트 도구다. **그 신호가 도착하는 것과 경로 검색/안내가 작동하는 것은 별도 검증**이다. Virtual Console만으로 내비 경로 응답을 만들 수 있다고 가정하지 않는다.

개인 목적지·정밀 이동 궤적·다른 프로필 정보를 로그나 스크린샷에 남기지 않는다. 시험용 장소와 필요한 최소 이벤트만 보존한다. 에뮬레이터 PASS도 실차 PASS로 승격하지 않는다.

## 8. 예상 수정 파일과 인터페이스 (미구현 제안)

패키지/파일 이름은 **앱 설계 후보**이며 Pleos SDK의 실제 API 명칭이 아니다.

| 후속 후보 | 역할 |
|---|---|
| `core/navigation/PracticeRoute.kt` 신규 | 목적지·최대 2개 경유지·입력 출처를 표현하는 순수 모델과 검증 |
| `core/navigation/NavigationGateway.kt` 신규 | 연결, 경로 요청, 비동기 상태 관찰, 해제 계약. 호출/검색/안내 상태를 분리 |
| `data/navigation/PleosNavigationGateway.kt` 신규 | 공개 SDK만 감싼 어댑터, 세션·권한·오류 처리 |
| `data/navigation/MockNavigationGateway.kt` 신규 | 명시적 MOCK 상태와 오류 시나리오. 실연동 성공과 분리 |
| `app/src/test/.../navigation/*Test.kt` 신규 | 모델·변환·상태 전이·타임아웃·중복/늦은 콜백 테스트 |
| `ui/screens/PracticeRouteScreen.kt` 등 신규 | 정차 시 코스 선택·전달·단계별 상태 표시 |
| `app/build.gradle.kts`, 저장소 설정 파일 | 실제 의존성/공식 Maven 추가 필요 여부. 후속 작업에서 최소 변경 |
| `AndroidManifest.xml` | 사용 API에 해당하는 최소 권한. 후속 작업 |
| `ChiilViewModel.kt` 또는 별도 Navigation ViewModel, 화면 진입점 | 수명·안전 게이트·사용자 요청 연결. 후속 작업에서 영향 범위 결정 |

A01의 `PleosVehicleSource.kt/AaosVehicleSource.kt/VehicleSignals.kt/Signals.kt` 수정은 계획하지 않는다. 안전 게이트의 기존 출력만 소비하고 UNKNOWN/STALE을 임의로 해제하지 않는다. A02 이벤트 엔진과 내비 콜백을 동일 이벤트라고 취급하지 않는다.

입력 계약 후보는 코스 ID·목적지·경유지·출처(MOCK/EMULATOR/실차 구분)·명시적 사용자 요청이다. 출력은 연결 상태, 호출 관측, 검색 시작/완료/오류, 코스 일치 여부, 안내 상태를 분리한다. 이 상태 이름과 앱 세션 ID는 **앱 자체 계약**이며 SDK 반환값으로 표현하지 않는다.

## 9. 구현 순서와 테스트 계획

1. 미확인인 POI 유효성·기본 내비/서비스 호환성·권한/카테고리 조건을 해소한다. 공개 범위 밖 기능이 필요하면 중단하고 근거를 재검토한다.
2. SDK와 분리한 순수 입력 모델/포트·Mock 어댑터부터 만든다. 정상/범위 경계/NaN/무한대/위경도 뒤바뀜/경유지 0·1·2·3개/중복 index/POI 누락을 테스트한다.
3. 호출·검색·안내를 분리하는 상태 처리를 먼저 테스트한다. 늦은/중복/순서 역전 콜백·응답 없음·이미 안내 중·해제 후 이벤트·사용자 재요청·외부 경로 변경을 포함한다. 타임아웃만으로 오류 원인을 확정하지 않는다.
4. 후속 SDK 도입 작업에서만 의존성/권한·어댑터를 추가한다. 모델 변환의 latitude/longitude·nullable/default·경유 순서를 시험하고 권한 거부·연결 실패·서비스 오류 시 성공 표시를 막는다.
5. 관련 단위 테스트와 `.\gradlew.bat :app:testMockDebugUnitTest`, `:app:testVehicleDebugUnitTest`, `:app:assembleMockDebug`, `:app:assembleVehicleDebug`를 실행한다. 웹 변경 시 해당 웹 테스트도 실행한다.
6. §7의 실환경 시나리오를 실행하고 4단계 증거를 각각 기록한다. 마지막 단계가 미검증이면 실제 안내 가능 기능으로 공개하지 않는다.
7. 최신 `origin/dev`·공유 파일 충돌·안전 게이트·관련 테스트를 확인한 뒤 후속 구현의 통합 여부를 판단한다. `main`은 별도 릴리스 승인 대상이다.

**이번 실행 결과:** 단위 테스트/Gradle 빌드/앱 호출/검색/안내 시작은 모두 **미실행 / NOT_VERIFIED**. 문서만 추가하여 앱 실행 경로가 바뀌지 않으므로 이번에는 빌드를 실행하지 않았다.

## 10. 이번 조사 증거와 안전 판정

실행한 조사: Git 상태·HEAD/origin/dev 확인, 원격 dev/main 조회, 공식 웹 문서 열람, 공식 샘플 파일 조회, 공식 Maven 배포물 HTTP 다운로드, ZIP 내부 공개 소스/manifest 열람, JDK 17 `javap -public -classpath <helper classes.jar>;<data classes.jar>`로 Helper·모델·리스너 확인, SHA-256 계산. 기본 샌드박스의 GitHub 네트워크 조회 및 최초 javap 접근 제한은 권한 있는 읽기 전용 재실행으로 확인했다.

배포물 SHA-256 (로컬 계산, 공급자 서명 검증을 뜻하지 않음):

| 파일 | SHA-256 |
|---|---|
| NaviHelper-3.0.5.aar | `15FADD11C7C611B1ABDF7EBF9CE91F2A6CE2728E60B4E38B4343E11BB9C12E97` |
| NaviHelper-3.0.5-sources.jar | `915FEB7246215E6999F4695BA6B51CF31E4C6A5D8A8C1B36F16A3BA353569201` |
| navi-data-3.0.4.aar | `7CA00E592134B53237A48CC3B7864063ECC5E2885465E3CE9C8C2A29789A2E2B` |

기준 지침: [AGENTS](../AGENTS.md), [안전 원칙](SAFETY_RULES.md), [공식 근거 대장](COMPLIANCE_REGISTER.md) §4/§9, [팀 가이드](TEAM_GUIDE.md), [A03 배정·인수 기준](THREE_PERSON_FEATURE_PLAN_V3.md), 기존 SDK 노트·PRD·구현/검증 기록. 과거 문서의 허용 단정이나 A01/A02 시험을 이번 실측으로 재사용하지 않았다.

| 규정/게이트 | 이번 문서 범위 판정 |
|---|---|
| S01 | PASS(변경 정적): 차량 제어 호출·권한·실행 코드 추가 없음 |
| S02 | 공개 SDK 근거 확인 PASS. 공모전 원문 재승인·배포 승인·기기별 지원은 NOT_VERIFIED. 위 공식 SDK 문서와 대장 O03/P13 참고 |
| S03/S04 | 실환경 NOT_VERIFIED. 후속 코스 입력/전달은 정차·신선한 안전 게이트 확인 전 차단 설계. 주행 중 UI 허용을 주장하지 않음 |
| S05/S06/S08 | NOT_APPLICABLE(이번 문서): 음성·미션 실행 변경 없음. 후속 연동 시 내비 안내와 중복 음성 방지 검토 필요 |
| S07/S10 | PASS(보고): 문서·배포물 존재·호출·검색·안내·에뮬레이터·실차 증거 구분 |
| S09 | PASS(변경 정적): 위치 수집·저장·업로드 없음. 후속 경로 로그 최소화 제안. 앱 전체 보안 인증은 아님 |
| S11/S12 | NOT_APPLICABLE: 외부 콘텐츠 도입·제출·배포 없음 |
| G1/G2 | PASS(조사 범위): 배정·단일 산출물·공식 근거·미확인 영역 명시 |
| G3 | 설계 제안만. 권한·불명·연결 실패 차단의 런타임 검증 NOT_VERIFIED |
| G4 | SDK 정적 확인만 PASS. 앱·검색·안내·실차 NOT_VERIFIED |
| G5 | 이번은 문서 브리핑까지. 기능 사용 가능 선언·dev 통합·main 릴리스 없음 |

작성 시 원격 `main`은 `c4c15686a30e788143ccc88d8ebaa52500d3187b`, 기존 로컬 `main`은 `e257cc8832e3f88f915b7e9f73e4511f5d8f4458`로 서로 다르다. 어느 쪽도 이동·갱신하지 않았다.

## 11. 남은 미확인 사항

다음 항목은 모두 **NOT_VERIFIED**다.

- 전체 전이 의존성 해석·두 플레이버 컴파일·실제 APK 실행.
- Emulator 2.1.0에 설치된 NaviService/기본 내비 버전과 SDK 3.0.5의 요청·콜백 호환성.
- 기본 내비 설치·선택의 정확한 절차, 지도/약관/계정/네트워크/위치 요구조건.
- 실제 권한 protectionLevel/grant, 별도 Capability·앱 카테고리·공모전/출시 정책의 적용 조건.
- 좌표만 제공하거나 POI ID/주소를 비운 요청의 허용 여부, POI 공급자 간 호환성.
- 경유지 빈 리스트·중복/순서·최대 개수 초과의 내비별 처리와 오류 전달 방식.
- 검색 콜백 발생 순서·스레드·지연 상한·서비스 재연결 시 동작·외부 경로 변경의 상관관계.
- 기존 안내 교체/사용자 확인/자동 안내 시작 여부. 검색 완료와 전면 표시가 안내 시작을 보장하는지 여부.
- 실제 앱 호출(2단계), 요청 코스 검색 응답(3단계), 기본 내비 안내 시작(4단계), 실차 동작.

후속 착수 지점은 **시험용 코스/유효 POI 확보와 에뮬레이터 내 기본 내비·서비스 조건 확인**이다. 그 후 독립 포트·Mock 테스트, 실제 SDK 어댑터, 정차 UI, 단계별 실측 순으로 진행한다.

