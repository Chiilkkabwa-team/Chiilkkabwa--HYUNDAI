# A03 runtime 최소 증거 — 2026-11-15 KST

코드 기준: 01eff12a2ecdbd4ae3e0fdb78f6031504aa13540 (PR #32 후 최신 dev).
코드 수정 없이 기존 Probe를 실행했다. 이 디렉터리의 instrumentation 출력은 원본이며,
logcat은 A03_PROBE의 고정 SDK_TRACE 행만 추렸다. APK/SDK/전체 시스템 로그/POI/좌표/계정은 포함하지 않는다.

- 실제 장비 emulator-5562, 현재 Android 사용자 10. 다른 serial은 명령 대상으로 사용하지 않았다.
- Pleos 2.1.0 / Android14 API34 / x86_64, NaviHelper3.0.5, NaviService3.0.4(55).
- 시작 전 adb devices 및 emulator/qemu 프로세스가 없음을 확인했다.
- 기존 A01 AVD를 `-port 5562 -read-only -no-window -no-audio -no-snapshot -gpu host`로 실행.
  호스트 PID7704, 자식11932. 원본 AVD 데이터/스냅샷을 저장하지 않는 임시 인스턴스이며 종료 완료.
- 작업 후 별도 A02 Lifecycle 기기 emulator-5558이 관측됐으나 조작하지 않았다.
- Host 2026-11-15T00:41:07+09:00과 device 2026-11-14T07:41:07-0800은 같은 UTC 시각이다.
  instrumentation atWallMs는 기기 epoch ms, logcat은 기기 현지 시각이다. 날짜 차이는 시간대 차이다.
- opt-in 설치 APK SHA256: 48226dab3864fb8a0d509c822cec3e147b633f13352442352b3f643e1124e32c
  설치된 base.apk를 adb shell sha256sum으로 조회했다.
- 실제 opt-in 설치 경로:
  `/data/app/~~fuyj4JEpqYYKwUDxLse_Eg==/com.chiilkkabwa.app-JiMS1LGifOfmf7MeQT6xZA==/base.apk`
  재설치마다 달라지는 경로이며 재현 명령은 pm path로 다시 조회해야 한다.

| 파일 | 의미 |
|---|---|
| inspect.txt | 자동 초기화/요청 없음, 실제 P/UX/PB 관측, 종료 RELEASED |
| initialize.txt | 명시 시작 후 CONNECTED, 초기화만 성공; 경로 호출 없음 |
| lifecycle.txt | 첫 Activity RELEASED → 새 Activity IDLE/자동초기화 없음 → 명시 재연결 → RELEASED |
| sdk-traces.txt | 초기화 총3회, 각 SDK_OBJECT_CREATED→INITIALIZE_CALLED→CONNECTED_CALLBACK→LISTENER_REMOVED→RELEASE_RETURNED |
| default-off.txt | opt-in 없는 APK 재설치 후 runner BLOCKED; trace 누계15→15로 증가 없음 |

PROBE_COMPLETED/INSTRUMENTATION_CODE -1은 절차 종료 코드다. SDK/search/guidance 성공 코드가 아니다.
이 증거에 FAILED_CALLBACK/TIMEOUT/실제 지연 callback 유발 증거는 없다.
PB=false이며 사용자 선택 POI도 없으므로 mode=request 및 경로 API 호출은 실행하지 않았다.
실제 검색/안내는 NOT_VERIFIED, 요청 단계는 BLOCKED. 자세한 판정은 ../../A03_RUNTIME_RESULTS.md 참조.
