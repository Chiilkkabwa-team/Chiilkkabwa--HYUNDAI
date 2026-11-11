# 기존 Markdown 팩트 감사

작성: 2026-11-11. 기준 SHA: de612636cc2c. **전수 감사가 완료되었다는 문서는 아니다.** 정정이 필요한 항목과 진행 상태를 관리한다.

| 문서 | 확인한 사실 또는 불일치 | 처리 |
|---|---|---|
| docs/00_BUILD_START_HERE.md | 이미 clone하는 저장소인데 git init을 지시; 과거 테스트 16개 및 미컴파일 설명 | 이번 세팅에서 온보딩 가이드로 교체 |
| docs/08_SUBMISSION_CHECKLIST.md | 조직 저장소인데 개인 저장소라고 서술; Private 심사계정 초대가 필수라는 과거 규정 | 이번 세팅에서 공식 제출 가이드 기준으로 교체 |
| docs/09_BACKLOG.md | 빌드·테스트 성공 이력과 미완료 체크가 혼재 | 후속 정리 필요 |
| docs/06_TEST_PLAN.md | node --test만으로 Playwright/axe까지 자동 실행되는 것처럼 읽힘 | 후속: 각 검증 방법/명령 분리 |
| docs/07_DEMO_SCRIPT.md | Android에 없는 음성/시계열 리플레이가 실신호 데모의 필수 장면처럼 표현됨 | 후속: Mock/실신호 시연 구분 |
| docs/02_ARCHITECTURE.md | SharedPreferences JSON(현재 코드)과 최종 DOCX의 암호화 저장 계획(목표) 분리 필요 | 후속 |
| docs/03_PLEOS_SDK_NOTES.md | SDK 문서 가용성과 실제 승인/에뮬레이터 수신/주행 오디오 정책을 분리해야 함 | 후속 |
| docs/04_UI_SPEC.md, docs/05_MISSIONS_CONTENT.md | 벨트 재체결을 권하는 기존 문구와 최종 DOCX의 '이미 체결 시 재체결 강요 금지' 원칙 충돌 | 후속 |
| web/README.md | 주행 중 무음이라고 서술하지만 web/voice.js에 모의 음성 기능이 있음 | 후속 |
| README.md, CLAUDE.md | 제품 계획이 Android에 구현된 사실처럼 읽힐 여지 및 오래된 문서 경로 | 후속 |

자료 우선순위:
1. 공식 공모전·Pleos 규정: URL과 확인 날짜
2. 최종 접수 DOCX: 제품 요구사항
3. 현재 GitHub 실제 소스: 구현 사실(파일·SHA)
4. 테스트 로그: 검증 사실(환경·입력·실제 결과)
5. 이전 회의/기획 문서: 당시 결정과 맥락(현재 사실과 분리)

기존 회의록 자체는 훼손하지 않는다. 정정이 필요한 현재 작업 문서부터 갱신하고, 완료 증거가 없는 과거 주장을 반복하지 않는다.
