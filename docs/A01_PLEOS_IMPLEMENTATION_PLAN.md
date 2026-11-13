# A01 Pleos 읽기 신호 연동 계획 — 2026-11-13

기준: Issue #16, dev b66f28e. 사용자 지정 순서와 안전 조건을 구현 명세로 사용한다.

- [x] 지정 문서, 공식 공개 문서, 캐시 AAR/sources 및 javap로 SDK 2.3.2 조사.
- [x] 실제 SDK 포트와 테스트 가능한 구독 수명 관리 분리. VehicleSnapshot 계약 보존.
- [x] 테스트 먼저: capability/영역 미지원, 권한, null/타입 오류, 연결 오류/시간초과, stop/restart 후 늦은 이벤트, 초기 조회와 새 이벤트 경쟁, 해제 실패 격리.
- [x] Seat 벨트 → Brake → TurnSignal 공개 읽기 API만 연결. 운전석은 한국 LHD의 ROW_1_LEFT를 명시하며 capability에 단독 영역이 없으면 차단.
- [x] 미검증 실차 경로는 기본 차단. debug에서 명시적 opt-in으로 에뮬레이터만 시험 가능. release에서는 항상 차단.
- [x] 변경 관련 단위 테스트, Android 양 플레이버 디버그 빌드 및 웹 회귀 실행.
- [x] SDK 시그니처/해시, 환경, 테스트 결과, 에뮬레이터 부재와 S01~S12/G1~G5 상태 기록.
- [x] 최신 dev와 충돌/테스트/CI 확인 후 안전 기준 충족분만 dev 병합. main 보존. (최종 병합/CI 결과는 검증 로그에 별도 기록)

수정 범위: data/vehicle의 Pleos 소스 및 전용 읽기 어댑터/수명 관리, 해당 JVM 테스트, app/build.gradle.kts의 시험용 기본 꺼짐 플래그, SDK/구현/검증 문서. AAOS 주차 게이트·권한·공유 신호 필드는 변경하지 않는다.

연결 단절은 공개 연결 리스너가 없으므로 읽기 heartbeat와 5초 응답 제한으로 보수적으로 감지한다. SDK 미응답 시 값을 폐기하고 재시작 전까지 복구하지 않는다. 종료 시 진행 중 비동기 조회/capability 응답을 배출한 뒤 release하며 늦은 콜백은 스냅샷에 적용하지 않는다. 내부 서비스·VHAL·숫자 property ID·제어 API는 사용하지 않는다.

리뷰 반영: Activity/VM 생명주기에 필요한 AAOS callback 세션 격리, repository 중지 시 값 무효화와 generation 차단을 함께 보완했다. S03 게이트의 판정 규칙/공유 필드는 그대로다. 무응답 SDK가 남으면 한 source 인스턴스의 추가 client 생성을 차단하고 sink 참조를 버린다. 응답 배출 전 조기 release는 하지 않는다. 실환경 생명주기는 NOT_VERIFIED.

통합 전 CI: PR #17 / Team CI 43391694682의 Web·Android jobs SUCCESS. 기본 off 상태의 안전한 dev 통합 기준 충족, 실제 병합 결과는 PR과 Actions 이력으로 추적한다.
