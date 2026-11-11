# 00. 신규 팀원 Android Studio 시작

기준: 2026-11-11. 공통 개발 규칙은 [TEAM_GUIDE.md](TEAM_GUIDE.md), 구현 상태는 [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).

## 1. 공통 환경

- Windows 10/11, Android Studio(지원 안정판)
- Gradle JDK: 17 권장(이전 팀 기록은 JDK 21에서 성공했다고 기재)
- 프로젝트 설정: Gradle Wrapper 8.10.2 / Android Gradle Plugin 8.8.2 / Kotlin 2.0.0
- Android SDK: API 35, Build-Tools 35.0.0, Platform-Tools
- 프로젝트 설정: compileSdk 35 / targetSdk 34 / minSdk 28
- 실차 신호 실험: Pleos Connect Emulator v2.1.0(API 34) + Virtual Console (실수신은 별도 검증 필요)
- 키스토어, 계정, 비밀 토큰, local.properties는 커밋 금지

## 2. Git 클론

PowerShell에서 영문 경로의 원하는 작업 폴더를 선택한다.

~~~powershell
mkdir C:\dev -ErrorAction SilentlyContinue
cd C:\dev
git clone https://github.com/Chiilkkabwa-team/Chiilkkabwa--HYUNDAI.git
cd .\Chiilkkabwa--HYUNDAI
git status
git branch -a
~~~

**이미 clone했다면 새로 clone하지 않는다.** 기존의 안쪽 Chiilkkabwa--HYUNDAI 폴더에서 실행한다. 이미 존재하는 저장소에 git init을 다시 하면 안 된다.

## 3. Android Studio 설치 확인

1. Android Studio > Open에서 **Git 저장소의 루트 폴더**를 연다.
2. Gradle Sync 결과를 확인한다. 필요하면 Android SDK 35와 JDK 17을 설정한다.
3. Build Variants에서 mockDebug를 먼저 선택한다.
4. 저장소 루트 PowerShell에서 실행한다.

~~~powershell
.\gradlew.bat :app:testMockDebugUnitTest
.\gradlew.bat :app:assembleMockDebug
.\gradlew.bat :app:assembleVehicleDebug
~~~

- mock: 앱 안의 **모의 차량 신호**를 이용한 시연용.
- vehicle: AAOS 일부 코드가 존재하나 **PleosVehicleSource는 미연동**. 컴파일 성공만으로 실제 신호 수신을 입증하지 못한다.
- Pleos Maven 의존성이 app/build.gradle.kts의 공통 dependencies에 있어 mock 빌드도 해당 리포지토리 접근이 필요할 수 있다.
- 실행 결과/막힌 원인을 [VERIFICATION_LOG.md](VERIFICATION_LOG.md) 양식으로 기록한다.

## 4. Pleos Emulator 별도 확인

공식 설치 안내: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator

~~~powershell
adb devices
adb forward tcp:8088 tcp:8088
adb logcat -s AaosVehicleSource PleosVehicleSource
~~~

Virtual Console 입력 항목과 **앱에 실제로 수신된 신호**는 별개다. 기어·주차 브레이크·벨트 등 각 신호를 개별 검증하고 [03_PLEOS_SDK_NOTES.md](03_PLEOS_SDK_NOTES.md)에 기록한다.

## 5. 브랜치 생성

~~~powershell
git fetch origin --prune
git switch main
git pull --ff-only origin main
git switch -c feature/my-task
~~~

담당 경계, 통합 규칙, PR 제목 형식은 [TEAM_GUIDE.md](TEAM_GUIDE.md) 참조.

## 6. 완료 기준

- 기능별 책임 범위와 변경 파일을 파악했다.
- 컴파일/테스트를 실제 실행하고 결과를 기록했다.
- 모의 신호와 실제 수신을 구분했다.
- 기능 브랜치만 푸시했으며, 통합 담당자가 검증 후 main에 병합한다.
