# 09. 백로그 — 우선순위 순

> P0 = 1차 시연 영상 전 / P1 = 2차 PT 전 / P2 = 여력 / ★ = 심사에서 '와' 할 만한 것. 끝나면 [x].

## P0
- [x] B00 웹 프로토타입(`web/`) — core 로직 JS 포팅 + 화면 8개 + 가상 차량 콘솔, Vercel 배포. 모의 신호 전용(2026-11-04)
- [x] B00b 웹판 개선: 목표별 단계 순서, 선택 미션 한 번에 넘기기, 자동 시연(약 1분) — docs/11 §5
- [ ] B00c 목표별 미션 추천 확장, 첫 연습 '새 관측' 설명 1줄, 가상 주차 속도 2단·탭 이동, 복기 '오늘 한 것' 요약 — docs/11 §5
- [x] B00d 웹판 평가 모드·강습 모드(가상 시뮬레이션): 가상 센서 기반 점수·상황별 조언, 강습자 정지 요청·최고 속도·주행보조 프리셋, 왼쪽 가상 도로, 자동 시연 — docs/11 §7. **CLAUDE.md §5와 충돌 → 제출 문장 반영 여부 팀 결정 필요**
- [ ] B00a `SessionReducer` PREPARING에서 Ready 복귀 시 lockReason 미해제 버그 — Kotlin 수정함, Gradle 테스트로 확인 필요(웹판 테스트 `preparing_pause_resumes_when_brake_reapplied`)
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
- [~] B21 ★ (웹판에서 강습 모드로 시연 가능 — docs/11 §7)  로드맵 3단계 ‘강습모드 차량 프로파일’ 개념 데모 화면(‘제조사 협업 가정 · 가상’ 라벨 필수, 실제 제어 호출 없음)
- [ ] B22 접근성: 글자 크기 2단계, 색 외 표시 점검
