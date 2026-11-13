# A01 실제 동작 신뢰성 후속 검증

2026-11-13 KST / Issue #16 / PR #18 이후. 기반 dev `1140c74`, 브랜치 `fix/a01-signal-reliability`. 새 기능을 추가하지 않고 기존 공개 읽기 경로와 에뮬레이터를 검증한다. 실차 시험이 아니다.

## 환경·제약

- Pleos Connect Emulator2.1.0/API34/x86_64, Vehicle Service2.3.22, 앱 Vehicle SDK2.3.2. 기존 전용 AVD `Chiilkkabwa_Pleos_2_1_0_A01`를 Cold boot했다.
- Windows / Intel Arc140T / WHPX / Emulator37.2.13, RAM4096MB/4코어, `-gpu host`. 기존 이미지·AVD 데이터는 초기화하지 않았다.
- 최신 코드의 opt-in vehicleDebug APK SHA256: `4BCDFF4F362E018AD22273CB96040E1443531EFCDA7D9F1635C32FD7C664FDC5`.
- 기본 비활성화, debug + EMULATOR opt-in, 실차/release 차단과 `P + parkingBrakeOn=true + UX 제한 해제` 게이트를 유지했다. 제어 API·권한·직접 VHAL 주입·가짜 성공 값 추가 없음.
- guest 로그 PST는 KST보다17시간 느리며, 호스트/guest 시계도 수초 차이가 있어 순서와 PID를 함께 확인한다.

## 실제 신호와 전체 워밍업

| 항목 | 수행·근거 | 판정 |
|---|---|---|
| 안전벨트 false→true | DDS ONLINE의 전체48개 카드 확인, `belt` 검색은 No matching controls. Seat Belt telltale On 상태에서 앱 force-stop/start 후 새 SDK BELT=false와 앱 belt=OK/false 수신 | 초기 수신 PASS, 체결 전환 **BLOCKED / NOT_VERIFIED** |
| 실제 주차 브레이크 | Parking Brake 및 Electronic Parking Brake telltale On을 각각 시험. 새 구독에서 parkingBrake=OK/false, gear=OK/PARK, ux=OK/false | 경고등 제어 확인 / 실제 PARKING_BRAKE_ON=true 전환 **BLOCKED / NOT_VERIFIED** |
| 전체 워밍업 | 실제 주차 브레이크가 false여서 `Locked(PARKING_BRAKE_OFF)`, 시작 버튼 비활성. 경고등·좌석 점유·속도0을 대신 사용하지 않음 | **BLOCKED**. 전체 미션 성공·완료 기록 없음 |

공식 [콘솔 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) 및 [전체 카드 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)의 Safety/Telltales는 **경고등**이다. Convenience/Seat Occupancy도 탑승 여부이며 벨트 버클 신호가 아니다. 공개 가이드와 설치된 콘솔에서 전용 벨트 체결·실제 주차브레이크 카드가 확인되지 않았다. 이는 이 버전 콘솔의 입력 경로 제약이며 모든 Pleos 버전/실차의 센서 미지원을 뜻하지 않는다.

## 검은 화면 원인 조사

가설을 앱 생명주기/프로세스, 장치 화면 전원, 호스트 그래픽으로 나누고 동일 APK에서 비교한다. `dumpsys power/display/window/activity/gfxinfo`, 실제 screencap, 앱 소비 trace와 호스트 emulator 로그를 함께 수집한다.

- 기존 검은 캡처만으로는 당시 전원·Surface 상태를 알 수 없어 GPU 문제나 Compose 결함으로 단정할 수 없다.
- 이번 host Cold boot와 여러 앱 재시작에서는 계기판·시스템 UI·앱 게이트 화면이 표시됐다. 호스트 `Failed to find ColorBuffer: 28`/`invalid color buffer` 및 앱 `Unable to match the desired swap behavior` 오류가 있더라도 정상 화면이 관측됐다. 같은 OpenGLRenderer 경고는 시스템 Settings에도 나타났다. 따라서 **오류 문자열만으로 앱 검은 화면의 원인을 확정하지 않는다**.
- HOME 키는 이 Pleos 멀티윈도우에서 앱을 실제 pause하지 않았다. Settings Activity를 열어 앱 ViewRoot visibility=8 및 전체 snapshot DISCONNECTED/null을 확인한 뒤 중단 시간을 측정했다.

### 재개·전원 대조 결과

| 시험 | 직접 관측 | 판정 |
|---|---|---|
| Settings에서325.96초 중단 후 복귀 | 같은 PID17157, HOT launch, Awake/Display ON, 앱 focus 및 ViewRoot visibility=0, 프레임3→6. 앱 게이트 정상 표시. 중단 중 바꾼 Brake100%를 복귀 새 callback=true 및 앱 brake=OK/true로 수신, 콘솔0% 복원 후 false 수신 | 이 한정 시나리오 PASS / 기존 장시간 검은 화면 미재현 |
| 안정된 화면 Sleep | KEYCODE_SLEEP 후 Asleep/Display OFF, ViewRoot visibility=8, STOPPED, 전체 RGB=0인 캡처 | 화면 전원 OFF 시 전체 검은 캡처 재현 |
| 화면 Wake 후 앱 복귀 | KEYCODE_WAKEUP 및 동일 MainActivity 실행, 같은 PID17157, Awake/Display ON, visibility=0, 프레임9→12, 앱 게이트 표시 및 새 세 신호 수신 | 이 한정 시나리오 PASS |
| 기존 캡처와 비교 | 기존 이미지와 Sleep 이미지는 둘 다2560×1440/모든 RGB=0. PNG SHA는 다르며 파일 동일성·당시 전원 상태를 증명하지 않음 | **전원 OFF는 가능한 설명, 기존 사건 원인은 NOT_VERIFIED** |

[보존 증거](evidence/a01-2026-11-13-reliability/README.md)에 실제 진단·로그·화면·픽셀 수치를 남겼다. 신호 trace만으로 화면이 켜져 있다고 판단하지 않는다. 앱 크래시/프로세스 재생성은 이325.96초 시험에서 관측하지 않았으며, 기존 검은 화면 사건에 소급해 적용하지 않는다.

**결론:** 관측된 Sleep 캡처는 화면 전원 OFF로 설명된다. 하지만 기존 '장시간 중단 후 검은 화면'은 당시 power/display/Surface 정보가 없어 원인 확정·수정 완료로 보고할 수 없다. 더 긴 중단, 호스트 절전/복귀, GPU A/B는 **NOT_VERIFIED**다. [Android 공식 그래픽 설정](https://developer.android.com/studio/run/emulator-acceleration)은 host 렌더링 문제 시 software 비교를 권장한다. 이번 시험에서 Awake 검은 화면이 재현되지 않아 GPU 변경을 해결책으로 적용하지 않았고, 앱 렌더러 변경·강제 재생성·안전 게이트 변경도 하지 않았다.

## 회귀 검사

최신 dev에서 `.\gradlew.bat --offline :app:testMockDebugUnitTest :app:testVehicleDebugUnitTest :app:assembleMockDebug :app:assembleVehicleDebug` 실행: **mock78/78, vehicle78/78, 양 debug 빌드 PASS**. `node --test web/test/*.test.mjs`: **79/79 PASS**. 기존 A02 테스트가 포함돼 이전44개보다 증가했다. opt-in APK는 별도 `-PenablePleosSignals=true :app:assembleVehicleDebug`로 생성했다. 빌드·JVM 성공을 실기 전체 미션 성공으로 취급하지 않는다.

## 남은 검증을 재개하는 조건

1. 공식적으로 지원되는 `Seat belt buckled` 및 실제 주차브레이크 입력을 제공하는 콘솔/이미지 또는 공식 안내 확보. 버전·권한·공개 API 확인 후 앱 callback과 소비 snapshot을 다시 대조한다.
2. 유효한 P/주차브레이크ON/UX 해제와 벨트 신호가 수신될 때만 전체 워밍업을 시험한다. 건너뛰기를 성공으로 기록하지 않는다.
3. 검은 화면 재발 즉시 전원·focused/resumed Activity·PID·Surface/renderer·호스트 로그·스크린샷을 같은 시점에 확보한다. 시간만 지난 정상 재개는 원인 해결 증거가 아니다.
