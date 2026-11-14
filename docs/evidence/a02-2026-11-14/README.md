# A02 실제 AAOS 속도 연결 증거 — 2026-11-14

실차가 아닌 Pleos2.1.0 API34 합성 시험이다. 코드8166fe4, opt-in APK882487C3...86D6. [계약·등급·한계](../../A02_SPEED_BRIDGE.md). guest America/Los_Angeles(PST) 11/13 21시는 KST 11/14 14시다.

로그는 adb logcat -v threadtime의 A02SpeedTrace 태그만 직접 연속 수집한 로컬 원본에서 **아래 연속 시간 구간**을 무편집 추출했다. 전체 선택태그 원본은 로컬 tmp/a02-speed/runtime.log에 보존했다. 전체 장치 로그는 커밋하지 않았다.

- `baseline.log`: guest21:01:20~21초, 초기 연결/지연 표본/첫0m/s Baseline 및 정속 event=null. observation은 원본 AAOS callback의 decode 결과(raw m/s/property ns/receipt ns/상태/출처)이고 consumed는 bridge/기존 엔진 소비 결과다.
- `increase.log`: 21:02:35~36초. 0.8333334→1.1111112m/s와 실제 RAPID_SPEED_INCREASE. 약1km/h 단계와 짧은 간격에서 산출되므로 실제 급출발 정확성 PASS로 쓰지 않는다.
- `decrease.log`: 21:04:15~16초, BRAKE 입력 중 실제 RAPID_SPEED_DECREASE와 이후 ONGOING_EPISODE 억제.
- `camera-restart.log`: 21:04:41~42초와21:05:20~25초. R 카메라에 의한 DISCONNECTED/null, P 복귀 새 sessionId와 새 기준값. 서로 떨어진 두 구간을 한 연속 기록이라고 하지 않는다.
- `permission-denied.log`: 21:06:32초, revoke 뒤 실제 PERMISSION_DENIED/null 및 INVALID_STATUS 거부. 사용자 권한창 전체 허용/거부 흐름은 미검증이다.
- `permission-definition.txt`: 실제 시스템 CAR_SPEED 정의 발췌, dangerous/sourcePackage 확인. `pm list permissions` 단순 검색 누락은 정의 부재 증거가 아니었다.
- `permission-restored.txt`: 종료 전 사용자10의 임시 CAR_SPEED를 다시 철회한 package 상태. 사용자0/10 모두 granted=false.
- `default-flags.txt`, `release-flags.txt`, `debug-flag-bytecode.txt`: opt-in 뒤 기본 debug false 복원 및 release false, debug의 실제 GETSTATIC 읽기. 최초 stale literal false는 실제 callback 성공 증거로 사용하지 않았다. 기본 복원 APK의 새 PID1349 logcat에서 A02 trace0 bytes를 확인했다.
- `console-speed-forward.png`: 실제 DDS ONLINE/D/29km/h 콘솔 화면. 앱 수신 증거는 동시기의 관측·소비 로그이며 이 화면 단독으로 성공 처리하지 않는다.
- `console-speed-braked.png`: 제동 시험 이후 캡처 시점은 D/13km/h/+0.4m/s²다. 정차0 또는 제동 순간 화면으로 오인하지 않는다.
- `console-restored-park.png`: 시험 종료 후 실제 P/0km/h 복원 화면.

1733개의 OK 원본 관측에서 propertyNs<=receiptNs를 스크립트로 확인했다. SDK/API 지원의 전체 인증이 아니라 해당 실행 자료 검사다. 파일에는 합성 속도와 임시 테스트 세션ID·프로세스ID만 있으며 실제 차량/VIN/위치/사용자 계정·APK·SDK 바이너리·AVD는 포함하지 않았다. 이미지 파일을 편집하지 않았다.

재현: `-PenableAaosSpeed=true :app:assembleVehicleDebug`, API34 Pleos AVD에 설치/정의 확인 후 필요한 사용자10 CAR_SPEED grant. `adb forward tcp:8088 tcp:8088`, 공식 Virtual Console의 D/ACCEL 및 BRAKE, 이후P 복원. `adb logcat -v threadtime -s A02SpeedTrace:D '*:S'`에서 observation과 consumed를 대조한다. R 카메라가 앱을 가리면 종료되는 것이 이번 정책이며 이를 우회하지 않는다. 기본/off와 release/실차에서는 이 수집을 열지 않는다.
