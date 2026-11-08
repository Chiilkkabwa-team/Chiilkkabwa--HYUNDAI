# 치일까봐 (Chiilkkabwa)

**주차된 내 차가 운전 연습을 확인해 주는 Pleos Connect 강습모드 앱** — 연습 전 5분, 주행 중 0터치, 연습 후 3분.

> 이름의 뜻: 길을 건널 때는 ‘치일까 봐’, 운전석에 앉으면 ‘칠까 봐’. 누구나 처음엔 겁이 나는 그 마음.

- **연습 전 5분(주차 중)**: 안전벨트·브레이크·방향지시등 같은 조작을 하면 **차량 신호가 확인**해 줍니다.
- **주행 중 0터치**: 화면은 잠기고 터치를 요구하지 않습니다. 혼자 연습이면 **필요할 때만 음성으로 한마디**(주행당 최대 5회·최소 간격 45초, 내비·통화·경고음에 즉시 양보).
- **연습 후 3분(다시 주차)**: 주행 중 있었던 일을 **시간순 리플레이**로 보고, 다음 목표 하나를 정합니다.

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
| 개발 환경 | Android Studio (최신 안정판), JDK 17 이상 (검증: JDK 21) |
| 빌드 | Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0 |
| 필요한 SDK 패키지 | `platforms;android-35`, `build-tools;35.0.0`, `platform-tools` |
| 로컬 설정 | 저장소 루트 `local.properties`에 `sdk.dir=<Android SDK 경로>` |
| 네트워크 | `dl.google.com`(Google Maven), `repo.maven.apache.org`, `nexus-playground.pleos.ai`(Pleos SDK) |
| SDK | compileSdk 35 / targetSdk 34 / **minSdk 28**(Pleos 요구: Android API 28 이상) |
| 실행 대상 | **Pleos Connect Emulator v2.1.0 (Automotive, API 34)** 또는 Pleos Connect 탑재 차량 |
| 화면 | 가로 기준, 세로 1창 / 가로 2창 모두 동작 |

**에뮬레이터 설치** (공식 문서 *Pleos Connect Emulator 설치 및 실행* 기준)

> 이전 버전의 Pleos Connect Emulator가 설치돼 있으면 **먼저 삭제**한 뒤 진행합니다.

1. Android Studio → Tools → **SDK Manager** → **SDK Update Sites** 탭 → 좌측 상단 `+`
   - Name: `Pleos Connect System Image`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - macOS: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. **SDK Platforms** 탭 → 하단 **Show Package Details** 체크 → Android API Level 34 아래 **Pleos Connect system Image** 체크 → OK → 설치 후 **Android Studio 재시작**
3. Tools → **Device Manager** → **Create device** → Form Factor **Automotive** → **Pleos Connect v2.1.0** → Next
4. **Advanced Settings** 탭 → Startup > Default boot = **Cold** / Emulated Performance > **RAM 4 GB**
   - 구버전 Android Studio: Graphics = Hardware, Boot option = Cold boot
5. 다시 **Device** 탭 → Select system image > API **34** → 하단 표의 **Pleos Connect v2.1.0 APIs** 선택 → Finish

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

| 구분 | 위치 | 크기 |
|---|---|---|
| 제출용 release APK (실제 신호) | `app/build/outputs/apk/vehicle/release/app-vehicle-release.apk` | 약 21 MB |
| 전 흐름 시연용 (모의 신호 + 디버그 패널) | `app/build/outputs/apk/mock/release/app-mock-release.apk` | 약 21 MB |
| 로컬 빌드 산출물 | `app/build/outputs/apk/<flavor>/<buildType>/` | |

APK 파일은 용량 때문에 저장소에 커밋하지 않습니다(`apk/*.apk`는 `.gitignore`). **GitHub Releases**에 올리고 아래에 링크합니다.

| 배포 | 링크 |
|---|---|
| release APK 다운로드 | *(GitHub Releases — 제출 시 기재)* |

**서명**: 기본값은 디버그 키입니다(설치·실행 확인용). 자기 키로 서명하려면 저장소 루트에 `keystore.properties`를 만드세요 — `storeFile` / `storePassword` / `keyAlias` / `keyPassword`. 이 파일은 `.gitignore`에 있습니다.

## 4. 사용 SDK / API

| 구분 | 사용 내용 |
|---|---|
| AAOS Car API | `CarUxRestrictionsManager`(주행 제한), `CarPropertyManager` — `GEAR_SELECTION`, `PARKING_BRAKE_ON`, 페달 압력(지원 시 — 공개 SDK stub에 상수가 없어 실행 중 조회, 없으면 해당 미션 생략) |
| Pleos Vehicle SDK (조회) | Seat(안전벨트·착석), Brake(브레이크 페달·주차 브레이크), TurnSignal, Wiper, Light, SteeringWheel(핸들 각도), Odometer(트립 거리·시간), CarInfo(차체 크기) |
| 음성 | Pleos TextToSpeech(ON_DEVICE 모드) → 미지원 시 Android 기본 TextToSpeech. 재생 전 `AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)` 요청, 포커스를 잃으면 **그 발화를 버립니다** |

**주행 중 음성에 대해**: 공식 가이드 「주행 안전을 고려한 설계」 원문은 *"주행 규제 상태에서 **영상 재생**을 시작하거나 재개하지 않도록 처리하고, **오디오 재생은 오디오 포커스 개발 가이드에 정의된 정책을 준수**합니다"*입니다. 금지는 영상이고 오디오는 정책 준수 시 허용되며, 음성 연계 인터랙션은 권장 사항입니다. 이 앱은 `distractionOptimized`를 선언하지 않아 주행 중 **화면**을 쓰지 않고, 음성만 위 정책에 맞춰 사용합니다.

모든 신호는 사용 전에 지원 여부(capability)를 확인하고, 미지원·권한 거부·끊김은 각각 다른 문구로 안내하며 **성공으로 세지 않습니다**.

## 5. 지금 상태 (2026-11-08)

| 항목 | 상태 |
|---|---|
| **APK 빌드** | ✅ `assembleMockDebug` · `assembleVehicleDebug` · `assembleRelease` 모두 BUILD SUCCESSFUL |
| **단위 테스트** | ✅ `:app:testMockDebugUnitTest` **17/17 통과** (Gradle 실행) |
| `core/` 순수 로직(안전 게이트·미션 판정 7종·세션 상태·요약·배지·가상 주차) | 위 테스트로 검증 |
| `data/` 차량 신호 계층 | 컴파일 통과. Pleos SDK 실연결은 `TODO(sdk)` |
| `ui/` Compose 화면 9개 (+주행 리플레이) | 컴파일 통과 |
| `web/` 웹 프로토타입 | 단위 테스트 **46개 통과**(core 22 + 주행 음성·리플레이 24), 접근성 검사(axe-core WCAG 2 A/AA) 위반 0건 |
| 매니페스트 점검 | `CONTROL_CAR_*` 권한 없음 · `distractionOptimized` 미선언 · `INTERNET` 권한 없음 (release APK에서 확인) |
| **에뮬레이터 실제 신호 수신** | ❌ **미시험** — 설치·신호 확인은 Pleos Connect Emulator에서 할 일입니다 |

> 「빌드된다」와 「차에서 동작한다」는 다릅니다. 위 표는 빌드·테스트까지만 확인된 사실입니다.

## 6. 데모

| 자료 | 링크 |
|---|---|
| 웹 프로토타입(모의 신호, 설치 불필요) | *(제출 시 기재)* |
| 시연 영상 | *(준비 중)* |

---

> 개발 문서: [`CLAUDE.md`](CLAUDE.md), [`docs/`](docs/) — PRD, 아키텍처, SDK 노트, UI 스펙, 미션 콘텐츠, 시험 계획, 시연 대본, 제출 체크리스트, 서비스 플로우, 제출 전략
