# 치일까봐 (Chiilkkabwa)

**주차된 내 차가 운전 연습을 확인해 주는 Pleos Connect 강습모드 앱** — 연습 전 5분, 주행 중 0터치, 연습 후 3분.

> 이름의 뜻: 길을 건널 때는 ‘치일까 봐’, 운전석에 앉으면 ‘칠까 봐’. 누구나 처음엔 겁이 나는 그 마음.

- **연습 전 5분(주차 중)**: 안전벨트·브레이크·방향지시등 같은 조작을 하면 **차량 신호가 확인**해 줍니다.
- **주행 중 0터치**: 화면은 잠기고 터치를 요구하지 않습니다. 혼자 연습이면 **필요할 때만 음성으로 한마디**(주행당 최대 5회·최소 간격 45초, 내비·통화·경고음에 즉시 양보).
- **연습 후 3분(다시 주차)**: 주행 중 있었던 일을 **시간순 리플레이**로 보고, 다음 목표 하나를 정합니다.

원칙: **확인은 차가, 판단은 사람이.** 앱은 차량을 제어하지 않고(읽기 전용), 운전 실력을 판정하지 않습니다.

### 심사용 빠른 안내

| 보고 싶은 것 | 어디서 |
|---|---|
| 설치 없이 전체 흐름(모의 신호) | 웹 체험판 https://chiilkkabwa-web.vercel.app — 머리말 **자동 시연**, **소개** |
| Android 앱 전체 흐름 | `mock` release APK(§3) — 화면 안 조작 패널로 신호를 바꿈 |
| 실제로 확인한 범위와 증거 | §5 표, [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md), [docs/VERIFICATION_LOG.md](docs/VERIFICATION_LOG.md) |

---

## 개발 브랜치 안내 (2026-11-13)

- **기능 개발:** 최신 `dev`에서 `feature/<scope>` 등 독립 브랜치를 만든다. 3명이 `dev`·`main`에서 직접 동시에 개발하지 않는다.
- **기능 통합:** 각 기능 담당자가 자체 테스트·규정 검증 후 **다른 팀원 컨펌 없이 `dev`에 직접 병합**한다. PR/리뷰는 선택이며, 병합 후 CI 실패는 본인이 복구한다.
- **최종 제출/릴리스:** `main`은 안정 버전으로 보존하며, 팀의 명시적 승인 후 `dev → main` 릴리스 PR로만 갱신한다. AI가 임의로 `main`에 병합하지 않는다.
- 새 clone의 기본 브랜치가 `main`이어도 개발 시작 전 `dev`로 전환한다. 상세 규칙: [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md), [AGENTS.md](AGENTS.md). **팀원 배정 전 기능 분업안:** [docs/THREE_PERSON_FEATURE_PLAN_V3.md](docs/THREE_PERSON_FEATURE_PLAN_V3.md).

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

| 구분 | 빌드 명령 | 산출물 | 무엇을 볼 수 있나 |
|---|---|---|---|
| **전 흐름 시연용** (모의 신호) | `assembleMockRelease` | `app/build/outputs/apk/mock/release/app-mock-release.apk` | 화면 안 조작 패널로 신호를 바꿔 가며 **주차 확인 → 워밍업 → 주행 잠금 → 복기** 전체 흐름. 화면에 '모의' 표시 |
| **차량 신호용** | `assembleVehicleRelease` | `app/build/outputs/apk/vehicle/release/app-vehicle-release.apk` | AAOS 공개 속성(기어·주차 브레이크·주행 제한)만 읽음. **Pleos 신호·속도·내비 연동은 release에서 꺼져 있음**(아래 §4) |

제출용 APK는 공식 규칙대로 저장소 **`apk/` 폴더**에 `팀명_앱이름_release.apk` 이름으로 올립니다(그 밖의 APK는 `.gitignore`). 절차: [docs/SUBMISSION_RUNBOOK.md](docs/SUBMISSION_RUNBOOK.md).

| 배포 | 링크 |
|---|---|
| release APK 다운로드 | *(GitHub Releases — 제출 시 기재)* |

**서명**: 기본값은 디버그 키입니다(설치·실행 확인용). 자기 키로 서명하려면 저장소 루트에 `keystore.properties`를 만드세요 — `storeFile` / `storePassword` / `keyAlias` / `keyPassword`. 이 파일은 `.gitignore`에 있습니다.

## 4. 사용 SDK / API

| 구분 | 사용 내용 | release 빌드 |
|---|---|---|
| AAOS Car API | `CarUxRestrictionsManager`(주행 제한), `CarPropertyManager` — `GEAR_SELECTION`, `PARKING_BRAKE_ON`, 페달 압력(지원 시 — 공개 SDK stub에 상수가 없어 실행 중 조회, 없으면 해당 미션 생략) | 켜짐 |
| AAOS 속도 | `PERF_VEHICLE_SPEED`(권한 `CAR_SPEED`) → 주차 후 속도 시간표·급가속/급감속 사건 | **꺼짐** — debug에서 `-PenableAaosSpeed=true`로만 시험 |
| Pleos Vehicle SDK 2.3.2 (조회만) | Seat(안전벨트·착석), Brake(브레이크 페달), TurnSignal, Wiper, Light, DrivingTime·DistanceDriven(트립) | **꺼짐** — debug + 에뮬레이터에서 `-PenablePleosSignals=true`로만 시험 |
| Pleos NaviHelper 3.0.5 | 정차 중 연습 코스 경로 요청 | **꺼짐** — debug에서 `-PenablePleosNavi=true`로만 시험 |
| 음성 | Android `TextToSpeech` + `AudioFocusRequest` — 정차 중 미리듣기(mock debug), **mock 앱 주행 잠금 중 모의 음성 길잡이**(급가속·급감속, 주행당 최대 5회·45초 간격, 포커스 잃으면 버림) | vehicle 앱은 주행 중 말하지 않음 |

**왜 release에서 꺼 두었나**: 에뮬레이터에서 일부 신호 수신은 확인했지만(§5), 전체 미션 완료·주차 게이트 해제·실차는 아직 확인하지 못했습니다. 팀 안전 원칙([docs/SAFETY_RULES.md](docs/SAFETY_RULES.md) S03·G5)에 따라 **확인되지 않은 실차 경로는 기본으로 잠급니다.** 그래서 vehicle release는 신호가 모자라면 연습을 열지 않고 이유를 보여 줍니다.

**주행 중 음성에 대해**: 공식 가이드는 주행 중 **영상**을 금지하고 오디오는 오디오 포커스 정책 준수를 요구합니다. 다만 Distraction Optimization을 선언하지 않은 앱이 주행 중에 계속 소리를 낼 수 있는지는 **Pleos 에뮬레이터에서 아직 확인하지 못했습니다**([S05](docs/SAFETY_RULES.md)). 그래서 차량 신호용(vehicle) 앱은 주행 중에 말하지 않고, 주행 중 음성 길잡이는 **모의 신호로만**(웹 체험판, Android mock 앱) 보여 줍니다.

모든 신호는 사용 전에 지원 여부(capability)를 확인하고, 미지원·권한 거부·끊김은 각각 다른 문구로 안내하며 **성공으로 세지 않습니다**. 조회 권한만 선언하고 제어 권한(`CONTROL_CAR_*`)·`distractionOptimized`·`INTERNET`은 선언하지 않습니다.

## 5. 지금 상태 (2026-11-15, `dev` 52e551f 기준)

| 항목 | 상태 | 근거 |
|---|---|---|
| Android 단위 테스트 | ✅ 241개 통과(mock 기준) | [검증 기록](docs/VERIFICATION_LOG.md) C01 절 |
| Android 빌드 | ✅ GitHub CI에서 mock·vehicle debug 빌드 성공 | GitHub Actions `Team CI` |
| 웹 단위 테스트 | ✅ 126개 통과 | 같음 |
| Pleos Connect Emulator v2.1.0(API 34) | 🟡 **일부 확인** — 벨트(초기값)·브레이크·방향지시등 수신, 속도 증가/감소 사건, NaviHelper SDK 연결·재연결(경로 요청은 미확인) | [구현 상태](docs/IMPLEMENTATION_STATUS.md), [docs/evidence](docs/evidence) |
| 전체 미션 완료·주차 게이트 해제(에뮬레이터) | ❌ 미확인 — 콘솔로 주차 브레이크 ON·벨트 체결을 만들 수 없음 | 구현 상태 A01 절 |
| 주행 중 음성(Android) | 🟡 mock 앱에만 연결(모의 신호, CI 테스트 통과). 에뮬레이터 실제 소리·실제 주행 허용은 미확인(S05) | 위 §4 |
| 실차 | ❌ 미시험 | — |

> 「빌드된다」, 「에뮬레이터에서 신호가 들어온다」, 「차에서 동작한다」는 각각 다릅니다. 위 표는 실제로 확인한 범위만 적었습니다.

## 6. 데모

| 자료 | 링크 |
|---|---|
| 웹 체험판(모의 신호, 설치 불필요) | https://chiilkkabwa-web.vercel.app |
| 시연 영상 | *(제출 시 기재)* |

---

> 개발 문서: [`CLAUDE.md`](CLAUDE.md), [`docs/`](docs/) — PRD, 아키텍처, SDK 노트, UI 스펙, 미션 콘텐츠, 시험 계획, 시연 대본, 제출 체크리스트, 서비스 플로우, 제출 전략
