# A01 입력 제약 후속 증거 (2026-11-14 KST)

- initial-signals.log: 코드50bddf1 opt-in vehicleDebug, 새 COLD launch PID31744의 adb logcat -d -v epoch --pid=31744 -s PleosVehicleSource:D A01SignalTrace:D AaosVehicleSource:D '*:S'. 신호 태그만 보존. 초기 구독 콜백/앱 소비 증거이며 새로운 입력 전환 시험이 아님.
- console-observation.json: A01 전용 콘솔18088의 공개 UI DOM 카드48개·초기/종료 상태, 설치 버전, 공식 이미지 메타데이터. 실제 카드 제목을 UI에서 추출하고 기록했다. 비공개 API 호출 없음. 벨트/주차브레이크 경고등을 다시 토글하지 않음.
- test-summary.json: 최종 Gradle XML 합계·신규 케이스·RED→GREEN·웹126. Mock JVM은 실제 MockVehicleSource/Repository/MissionRunner 사용, B/C 화면 실행시험 아님. UI 소유자의 기존 pause/resume 계약을 테스트에서 재현했다.
- 화면은 git 제외 build/a01-inputs/app-gate.png. Awake/display ON/Activity resumed 및 잠금 화면을 실제 확인. 검은 화면 미재현이며 이전 원인 해결 증거 아님.
- SDK/APK/AVD·프로필·전체 장치로그는 저장소에 넣지 않았다. 임시 APK/public AAR hash만 기록.

벨트체결·주차브레이크ON·전체 Emulator 워밍업은 BLOCKED/NOT_VERIFIED. 경고등 대조는 [기존 증거](../a01-2026-11-13-reliability/README.md) 참조. [보고서](../../A01_INPUT_BLOCKERS.md).
