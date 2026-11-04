# 09. 백로그 — 우선순위 순

> P0 = 1차 시연 영상 전 / P1 = 2차 PT 전 / P2 = 여력 / ★ = 심사에서 '와' 할 만한 것. 끝나면 [x].

## P0
- [ ] B01 mock 플레이버 컴파일 오류 수정, 디버그 패널로 전 흐름 1회
- [ ] B02 단위 테스트를 Gradle에서 실행(16개) — CI(GitHub Actions)로 자동화하면 가산점에 도움
- [ ] B03 에뮬레이터 설치, vehicle 플레이버 설치, AAOS 지원 속성 목록 로그 → docs/03 갱신
- [ ] B04 게이트 실신호(CarUxRestrictions·GEAR·PARKING_BRAKE) — T02
- [ ] B05 PleosVehicleSource: Seat 벨트, Brake 페달 상태 실연결 — T04·T05
- [ ] B06 출처 라벨 실제 값 반영(에뮬레이터 = '에뮬레이터 신호')
- [ ] B07 시연 영상 45~60초(docs/07)

## P1
- [ ] B08 TurnSignal·SteeringWheel·Odometer·CarInfo 연결
- [ ] B09 ★ 가상 주차 실제 핸들 연동 + 차체 실제 치수
- [ ] B10 Lifecycle: onStop에서 repo.stop(), onStart에서 게이트부터(T12)
- [ ] B11 STALE 판정(연속값 3초 무응답) — T08
- [ ] B12 PracticeStore 계측 테스트(프로필 분리) — T10
- [ ] B13 정차 중 음성 칭찬(Android TextToSpeech, 오디오 포커스, 주행 중 금지)
- [ ] B14 성능 측정·기록(docs/06 §4)
- [ ] B15 README(제출용) + release APK

## P2 (창의 영역 — docs/10 참고)
- [ ] B16 ★ 가로 2창 멘토 카드(조수석 쪽), Connect-W 와이드 대응
- [ ] B17 ★ 수료 카드 이미지 내보내기(공유는 사용자 직접)
- [ ] B18 평행 주차·전면 주차 시나리오
- [ ] B19 ★ ‘내 운전 점검’ 경력자 모드(정차 페달·핸들 테스트) — 멘토가 먼저 해 보는 장면
- [ ] B20 Gleo AI 음성 진입(App Schema) — 연동 신청 가능 시
- [ ] B21 ★ 로드맵 3단계 ‘강습모드 차량 프로파일’ 개념 데모 화면(‘제조사 협업 가정 · 가상’ 라벨 필수, 실제 제어 호출 없음)
- [ ] B22 접근성: 글자 크기 2단계, 색 외 표시 점검
