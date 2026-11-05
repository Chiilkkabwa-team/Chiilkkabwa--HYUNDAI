# 치일까봐 (Chiilkkabwa)

**주차된 내 차가 운전 연습을 확인해 주는 Pleos Connect 강습모드 앱** — 연습 전 5분, 주행 중 0터치, 연습 후 3분.

> 이름의 뜻: 길을 건널 때는 ‘치일까 봐’, 운전석에 앉으면 ‘칠까 봐’. 누구나 처음엔 겁이 나는 그 마음.

- **연습 전 5분(주차 중)**: 안전벨트·브레이크·방향지시등 같은 조작을 하면 **차량 신호가 확인**해 줍니다.
- **주행 중 0터치**: 차가 움직이면 앱은 아무것도 요구하지 않습니다.
- **연습 후 3분(다시 주차)**: 오늘 연습(거리·시간)을 돌아보고 다음 목표 하나를 정합니다.

원칙: **확인은 차가, 판단은 사람이.** 앱은 차량을 제어하지 않고(읽기 전용), 운전 실력을 판정하지 않습니다.

---

## 1. 실행 방법

```bash
./gradlew :app:testMockDebugUnitTest     # 순수 로직 단위 테스트
./gradlew :app:assembleMockDebug         # 모의 차량 APK (SDK·에뮬레이터 없이 전체 흐름)
./gradlew :app:assembleVehicleDebug      # 실제 신호 APK (Pleos Connect 에뮬레이터)
```

```bash
adb install -r app/build/outputs/apk/mock/debug/app-mock-debug.apk
```

| 플레이버 | 내용 | 쓰는 때 |
|---|---|---|
| `mock` | 모의 차량 + 디버그 패널 | SDK·에뮬레이터 없이 전 흐름 확인 |
| `vehicle` | AAOS Car API + Pleos Vehicle SDK | 에뮬레이터·실차 신호 연결 |

브라우저로 흐름만 먼저 보려면 `web/` 폴더(모의 신호 전용, 설치 불필요). [`web/README.md`](web/README.md) 참고.

## 2. 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| 개발 환경 | Android Studio (최신 안정판), JDK 17 |
| 빌드 | Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0 |
| SDK | compileSdk 35 / targetSdk 34 / **minSdk 28**(Pleos 요구: Android API 28 이상) |
| 실행 대상 | **Pleos Connect Emulator v2.1.0 (Automotive, API 34)** 또는 Pleos Connect 탑재 차량 |
| 화면 | 가로 기준, 세로 1창 / 가로 2창 모두 동작 |

**에뮬레이터 설치**
1. Android Studio → Tools → SDK Manager → SDK Update Sites → `+`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - macOS: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms → API 34 → *Show Package Details* → **Pleos Connect system Image** 체크
3. Device Manager → Create device → Automotive → Pleos Connect v2.1.0 → Advanced: **Cold boot**, RAM 4GB

**Pleos Vehicle SDK**: `settings.gradle.kts`에 저장소가 이미 들어 있습니다.
`maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }` → `ai.pleos.playground:Vehicle:2.3.2`

**Virtual Console(차량 신호 시뮬레이션)** — 시연·테스트의 핵심 도구
```bash
adb forward tcp:8088 tcp:8088
```
브라우저에서 `http://127.0.0.1:8088/` → Motion(기어·브레이크·스티어링), Safety(주차 브레이크·안전벨트), Convenience(시트·조명·와이퍼), ADAS(크루즈) 값을 바꾸면 앱이 반응합니다.

**권한**: 조회(읽기) 권한만 선언합니다. 제어 권한(`CONTROL_CAR_*`)은 선언하지 않습니다.
`pleos.car.permission.CAR_SEATS / CAR_DRIVE / CAR_LIGHTS / CAR_WINDOWS / CAR_INFO`, `android.car.permission.CAR_POWERTRAIN`,
`android.car.permission.READ_CAR_PEDALS`(위험 권한 → 런타임 요청, 미허용 시 해당 미션만 건너뜀)

**신호 로그 확인**
```bash
adb logcat -s AaosVehicleSource PleosVehicleSource
```

## 3. APK 파일 위치

| 구분 | 위치 |
|---|---|
| 제출용 release APK | `apk/` 폴더 *(빌드 후 업로드 예정 — 파일명 `팀명_치일까봐_release.apk`)* |
| 로컬 빌드 산출물 | `app/build/outputs/apk/<flavor>/<buildType>/` |

> 현재 `apk/` 폴더는 비어 있습니다. release 빌드를 마치면 이 자리에 올라가며, 용량이 크면 GitHub Releases 링크로 대신합니다.

## 4. 사용 SDK / API

| 구분 | 사용 내용 |
|---|---|
| AAOS Car API | `CarUxRestrictionsManager`(주행 제한), `CarPropertyManager` — `GEAR_SELECTION`, `PARKING_BRAKE_ON`, `BRAKE_PEDAL_COMPRESSION_PERCENTAGE`(지원 시) |
| Pleos Vehicle SDK (조회) | Seat(안전벨트·착석), Brake(브레이크 페달·주차 브레이크), TurnSignal, Wiper, Light, SteeringWheel(핸들 각도), Odometer(트립 거리·시간), CarInfo(차체 크기) |
| 음성 | Pleos TextToSpeech(ON_DEVICE 모드) → 미지원 시 Android 기본 TextToSpeech, **정차 중에만** |

모든 신호는 사용 전에 지원 여부(capability)를 확인하고, 미지원·권한 거부·끊김은 각각 다른 문구로 안내하며 **성공으로 세지 않습니다**.

## 5. 지금 상태

| 항목 | 상태 |
|---|---|
| `core/` 순수 로직(안전 게이트·미션 판정 7종·세션 상태·요약·배지·가상 주차) | **단위 테스트 17개 통과** (2026-11-05, JVM에서 실행) |
| `data/` 차량 신호 계층 | **컴파일 검증 완료** (2026-11-05, AAOS 스텁 기준) — Pleos SDK 실연결은 `TODO(sdk)` |
| `ui/` Compose 화면 8개 | 에뮬레이터 빌드 전 |
| `web/` 웹 프로토타입 | 단위 테스트 22개 통과, 접근성 검사(axe-core WCAG 2 A/AA) 위반 0건 |

## 6. 데모

| 자료 | 링크 |
|---|---|
| 웹 프로토타입(모의 신호, 설치 불필요) | *(제출 시 기재)* |
| 시연 영상 | *(준비 중)* |

---

> 개발 문서: [`CLAUDE.md`](CLAUDE.md), [`docs/`](docs/) — PRD, 아키텍처, SDK 노트, UI 스펙, 미션 콘텐츠, 시험 계획, 시연 대본, 제출 체크리스트, 서비스 플로우, 제출 전략
