# A01 실기 검증 증거 — 2026-11-13

대상은 실차가 아닌 Pleos Connect Emulator2.1.0(API34), Vehicle SDK2.3.2 / Vehicle Service2.3.22다. 시험 코드258e4da, 명시적 opt-in vehicleDebug, source=EMULATOR.

- `console-reproduction.log`: adb가 직접 연속 저장한 최종 재현 로그. guest 시간은 America/Los_Angeles(PST), 같은 날 KST는 +17시간. 사용한도 중단 전02:45 기록과 재개 후05:33~05:36 기록이 함께 있다. 최종 재현 구간05:35~05:36은 단일 PID21878에서 brake false→true→false, turn LEFT/OFF/RIGHT/OFF와 앱 소비 상태를 보존한다. SDK callback과 A01SignalTrace를 구분한다.
- `sdk-release-reproduction.log`: 실제 unregister 요청 제거·release·listener 정리 로그. 플랫폼 사각지대 카메라가 앱을 가리면 구독을 중지한다.
- `app-signals.log`: 초기 실기 시험 후 저장한 부분 로그. 링 버퍼 순환으로 일부 앞선 전환이 누락됐으므로 전체 증거는 위 최종 재현 로그를 사용한다.
- `sdk-lifecycle-and-belt.log`: 당시 SDK getter의 운전석 areaId1 belt=false 조회. 파일명과 달리 마지막 구간은 반복 getter 중심이므로 해제 증거는 `sdk-release-reproduction.log`를 사용한다.
- `app-gate-locked.png`: 최초 실기 실행 중 캡처. 계기판 주차 브레이크 경고등은 켜졌지만 앱의 실제 주차 브레이크 확인은 대기이며 시작 버튼은 잠겨 있다. 최종 재현 후 화면이 아니라 최초 실행 화면임을 구분한다.
- `app-display-after-resume-black.png`: 장시간 사용한도 중단 후 최종 재현이 끝난 시점의 실제 검은 캡처. 로그의 신호 수신 PASS와 달리 이 호스트의 렌더링 안정성 PASS 근거로 쓰지 않는다.

전용 belt-buckled 컨트롤은 콘솔에 없었고 Seat Belt telltale은 별개였다. 실제 belt=true 전환, PARKING_BRAKE_ON=true 및 전체 미션 UI 완료는 NOT_VERIFIED/BLOCKED다. 안전 게이트를 우회하지 않았다. SDK 바이너리·APK·AVD·전체 장치 로그는 커밋하지 않았다.
