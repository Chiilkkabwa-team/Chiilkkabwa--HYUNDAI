# A01 후속 신뢰성 검증 증거

2026-11-13 KST23:20~23:33, 코드 dev1140c74, Pleos Emulator2.1.0/Vehicle Service2.3.22/SDK2.3.2. 실차가 아닌 합성 에뮬레이터 입력이다. [결과·한계](../../A01_RELIABILITY_FOLLOWUP.md).

- `signal-host.log`: adb logcat -v threadtime로 선택 태그를 직접 연속 수집한 원본. SDK callback과 ViewModel 소비 A01SignalTrace, AAOS 지원 속성, OpenGLRenderer가 포함된다. 시작 시 버퍼에 있던 이번 부팅의 초기 플랫폼 로그도 포함한다. guest PST는 KST보다17시간 느리며 host/guest 시계는 수초 차이가 있다.
- `actions.txt`: 호스트 시험 단계 기록. 06:22:39 PID24255는 Seat Belt telltale On,06:23:16 PID30692는 Parking Brake telltale On,06:24:47 이후 PID17157은 최신 APK에서 Electronic Parking Brake telltale On의 초기 읽기와 중단/재개 시험이다. 초기 비교 PID들은 PR18 설치 APK, PID17157은 최신 dev opt-in APK이며 A01 코드 변경은 없다.
- `display-states.txt`: 실제 dumpsys 결과의 선택 구간을 그대로 발췌. 다른 플랫폼 Activity까지 출력되는 APP_ACTIVITY 절은 제외했다. focus/ViewRoot/전원·display 값과325.96초의 호스트 경과 시간을 보존했다.
- `emulator-graphics-excerpt.log`: 호스트 emulator stdout에서 ColorBuffer 오류·부팅 시간만 발췌. 이 오류 이후에도 정상 화면이 관측됐으므로 원인 확정 근거가 아니다.
- `console-belt-search.png`, `console-parking-search.png`: 전체 도메인에서 전용 카드 검색 결과 No matching controls. 검색은 Telltales 선택 메뉴 항목까지 검색하는 것이 아니다. 전체48개 카드와 공식 카드 문서를 함께 확인했다.
- `console-seat-belt-telltale-on.png`: 실제 화면은 Seat Belt 경고등 On이다. 캡처 시 콘솔의 직전 프레임이 남았으므로 이후 Parking Brake 조작 화면으로 쓰지 않는다.
- `console-epb-on.png`: Electronic Parking Brake **경고등** On의 콘솔 화면. 실제 PARKING_BRAKE_ON=true 증거가 아니다.
- `host-resume-five-minutes.png`: Settings에서325.96초 중단 후 동일 PID 앱 게이트가 정상 표시된 실제 캡처. 성공한 전체 미션 화면이 아니다.
- `host-sleep-stable.png`: Asleep/Display OFF일 때 실제 검은 캡처.
- `host-wake-stable.png`: Wake 후 동일 앱 프로세스의 게이트 복구 화면.
- `png-metrics.json`: System.Drawing으로 원본 PNG를 RGB24로 읽어 크기·전체 RGB 최소/최대·0이 아닌 바이트 수 및 원본 SHA256을 계산했다. 기존 PR18 검은 캡처와 이번 Sleep 캡처는 모두 RGB=0이나 파일 SHA는 다르다. 이미지 파일을 편집하지 않았다.

핵심 로그:06:25:06.967 전체 DISCONNECTED/null →06:30:34.489 BRAKE=true callback 및06:30:34.483 앱 brake=OK/true →06:30:47.718 BRAKE=false 및 앱 false. 중단 중 입력은 기존 값을 유지하는 방식으로 미션 성공 처리되지 않았다. Sleep/Wake 이후06:32:00에 세 신호가 새로 수신됐다.

재현 명령: `adb -s emulator-5554 shell am start --user 10 -W -a android.settings.SETTINGS` → 경과 측정 → `adb ... shell am start --user 10 -W -n com.chiilkkabwa.app/.MainActivity`. 화면 전원 대조는 `adb ... shell input keyevent KEYCODE_SLEEP`/`KEYCODE_WAKEUP`. 각 단계에서 `dumpsys power`, `dumpsys display`, `dumpsys window`, `dumpsys gfxinfo com.chiilkkabwa.app`, `screencap -p` 및 앱 trace를 함께 수집한다. 전원 시험은 테스트 AVD 화면이며 차량 제어 API가 아니다.

벨트true·실제 주차브레이크ON·전체 워밍업은 BLOCKED/NOT_VERIFIED. 기존 장시간 검은 화면의 원인·수정 완료도 NOT_VERIFIED. 이번 정상 재개나 Sleep 대조를 원래 사건의 원인 확정으로 사용하지 않는다. SDK/APK/AVD·전체 장치 로그·정밀 위치·계정·비밀정보는 포함하지 않았다. 시험 앱·우리 로그 수집·AVD는 종료했고 기존 AVD 데이터는 보존했다.
