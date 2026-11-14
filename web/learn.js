// 치일까봐 — 정차 중 학습 도우미(B04, 11/12 회의): 미션별 도움말(O/X) + 운전 노트(위키처럼 펼쳐 보기)
//
// 원칙:
//   - **주차 중에만** 열린다(오버레이는 주행 잠금이 걸리면 닫힌다 — app.js renderOverlay). S04
//   - 필요한 사람만 펼쳐 본다. 메인 흐름을 막지 않는다(미션 화면 오른쪽 위 (i) 단추).
//   - 확인 안 된 숫자·차종별로 다른 기능을 단정하지 않는다. 차마다 다른 건 "설명서 확인"으로 둔다. S07
//   - 이미 맨 벨트를 풀라고 하지 않는다. S08
//   - 그림은 기본 도형 SVG(외부 이미지·영상 없음). S11

import { evaluateGate } from './core.js';

// 학습 오버레이에는 콘솔의 주차 브레이크 미지원 우회 옵션을 적용하지 않는다.
export const learningAvailable = (snapshot) => evaluateGate(snapshot).ready;

/** O/X 그림 — 맞는 예/아닌 예를 한 줄씩. 그림은 글자를 읽지 않아도 구분되게 기호와 색 둘 다 쓴다 */
const ox = (ok) => `<svg class="ox ${ok ? 'o' : 'x'}" viewBox="0 0 24 24" aria-hidden="true">${ok
  ? '<circle cx="12" cy="12" r="8.5" fill="none" stroke="currentColor" stroke-width="2.6"/>'
  : '<path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2.8" stroke-linecap="round"/>'}</svg>`;

/** 미션 id → 단계·O/X·이어 볼 노트 */
export const HELP = {
  SEAT_BELT: {
    steps: ['어깨띠가 목이 아니라 쇄골 가운데를 지나게 당겨요.', '허리띠는 배가 아니라 골반 뼈 위로.', '‘철컥’ 소리가 나면 한 번 당겨서 잠겼는지 확인해요.'],
    o: '어깨띠가 쇄골 가운데, 허리띠가 골반 위', x: '어깨띠를 겨드랑이 밑으로 빼서 매기',
    note: '이미 맸다면 그대로 유지해 주세요. 체결 신호를 읽을 수 없는 차에서는 벨트를 확인한 뒤 미션을 건너뛸 수 있어요.', wiki: 'before',
  },
  BRAKE_PRESS_RELEASE: {
    steps: ['오른발 뒤꿈치를 브레이크 앞 바닥에 붙여요.', '발끝으로 끝까지 꾹 — 페달이 더 안 들어갈 때까지.', '뗄 땐 2~3초에 걸쳐 천천히.'],
    o: '뒤꿈치는 바닥에, 발끝으로 밟기', x: '발 전체를 들어서 위에서 내리꽂기',
    wiki: 'stop',
  },
  TURN_SIGNAL: {
    steps: ['핸들에서 손을 떼지 않고 손가락으로 레버를 올리고 내려요.', '왼쪽은 아래로, 오른쪽은 위로 미는 차가 많아요(차마다 달라요).', '계기판 화살표가 깜빡이는지 봐요.'],
    o: '차로를 바꾸기 전에 먼저 켜기', x: '옮기면서, 또는 옮긴 다음에 켜기',
    wiki: 'lane',
  },
  WIPER: {
    steps: ['와이퍼 레버 위치는 차마다 달라요. 보통 핸들 오른쪽 레버예요.', '비가 약하면 한 번·느리게, 세지면 빠르게.', '와이퍼를 켤 땐 전조등도 같이 켜면 다른 차가 나를 잘 봐요.'],
    o: '와이퍼 켤 때 전조등도 같이', x: '비 오는데 전조등 끄고 달리기',
    wiki: 'before',
  },
  LOW_BEAM: {
    steps: ['전조등 스위치는 보통 핸들 왼쪽 레버 끝을 돌려요(차마다 달라요).', '해 질 녘·터널·비 오는 날엔 켜요.', '마주 오는 차가 있으면 상향등은 꺼요.'],
    o: '터널 들어가기 전에 켜기', x: '앞차 바로 뒤에서 상향등 켜기',
    wiki: 'before',
  },
  STEERING_CENTER: {
    steps: ['핸들 가운데 로고가 똑바로 서 있는지 봐요.', '바퀴가 곧게 서 있어야 출발할 때 옆으로 튀지 않아요.', '두 손은 9시·3시 방향.'],
    o: '9시·3시 방향으로 두 손', x: '한 손으로 핸들 아래쪽만 잡기',
    wiki: 'before',
  },
  PEDAL_FEEL: {
    steps: ['초록 칸은 ‘살살’ 밟은 정도예요.', '발목만 움직여서 조금씩 더하고 빼요.', '2초 버티면 내 발이 그 깊이를 기억해요.'],
    o: '발목으로 조금씩 더하기', x: '한 번에 끝까지 밟았다 떼기',
    wiki: 'stop',
  },
};

/** 운전 노트 — 챕터를 넘겨 가며 본다(위키하우 느낌: 짧은 단계 + O/X) */
export const WIKI = [
  {
    key: 'before', title: '출발 전 1분',
    steps: ['좌석: 브레이크를 끝까지 밟았을 때 무릎이 살짝 굽는 거리.', '미러: 옆 미러에 내 차 옆면이 조금 보이게.', '벨트 → 브레이크 밟고 시동 → 기어 D → 주차 브레이크 해제.'],
    o: '미러·좌석을 출발 전에 맞추기', x: '달리면서 미러 맞추기',
  },
  {
    key: 'stop', title: '멈출 때 · 언덕에서',
    steps: ['멈출 곳을 미리 보고 일찍, 두 번에 나눠 밟아요.', '언덕에서 브레이크 → 가속으로 발을 옮기는 순간 차가 뒤로 밀릴 수 있어요.', '언덕 밀림 방지·오토 홀드처럼 잠깐 잡아 주는 기능은 차마다 이름과 시간이 달라요. 차량 설명서에서 확인해요.'],
    o: '언덕에선 브레이크를 꾹 → 가속으로 빠르게 옮기기', x: '언덕에서 기능만 믿고 발을 오래 떼기',
  },
  {
    key: 'lane', title: '차로 바꾸기',
    steps: ['옆 미러 → 깜빡이 → 고개 돌려 사각지대 → 천천히 이동.', '뒤차 전체가 미러에 보일 만큼 멀어졌을 때가 여유 있어요.', '옮긴 뒤 깜빡이를 꺼요.'],
    o: '깜빡이 먼저, 그다음 이동', x: '깜빡이와 동시에 핸들 꺾기',
  },
  {
    key: 'turn', title: '교차로 돌기',
    steps: ['좌회전은 맨 왼쪽(좌회전) 차로, 우회전은 맨 오른쪽 차로에서.', '우회전은 횡단보도 앞에서 한 번 멈추고, 보행자가 없을 때 천천히.', '좌회전은 마주 오는 차와 건너는 사람을 먼저 봐요.'],
    o: '교차로에 닿기 전에 미리 회전 차로로', x: '교차로 바로 앞에서 차로 바꾸기',
  },
  {
    key: 'park', title: '주차',
    steps: ['후진은 걷는 속도보다 느리게.', '멈출 땐 브레이크를 밟은 채 기어 P → 주차 브레이크.', '내리기 전 뒤에서 오는 차·자전거를 미러로 확인.'],
    o: '기어 P + 주차 브레이크 둘 다', x: '기어 P만 넣고 내리기',
  },
];

const oxRow = (o, x) => `<div class="ox-row"><p class="ox-o">${ox(true)}<span><b>이렇게</b>${o}</span></p><p class="ox-x">${ox(false)}<span><b>이건 아니에요</b>${x}</span></p></div>`;

/** 미션 도움말(오버레이 본문) */
export function helpHtml(missionId, title) {
  const h = HELP[missionId];
  if (!h) return `<h2>${title}</h2><p class="sub">이 미션은 따로 도움말이 없어요.</p>`;
  return `<div class="learn"><span class="tag soft">정차 중 도움말</span><h2>${title}</h2>
    <ol class="learn-steps">${h.steps.map((s) => `<li>${s}</li>`).join('')}</ol>
    ${oxRow(h.o, h.x)}
    ${h.note ? `<p class="learn-note">${h.note}</p>` : ''}
    <div class="learn-more"><button type="button" class="btn secondary" data-wiki="${h.wiki}">더 자세히 — 운전 노트</button></div></div>`;
}

/** 운전 노트(오버레이 본문). page = 챕터 번호 */
export function wikiHtml(page = 0) {
  const c = WIKI[page] || WIKI[0];
  return `<div class="learn wiki"><span class="tag soft">운전 노트 · 정차 중에만</span><h2>${c.title}</h2>
    <nav class="wiki-tabs" aria-label="챕터">${WIKI.map((w, i) => `<button type="button" class="chip-btn" data-page="${i}" aria-current="${i === page ? 'page' : 'false'}">${w.title}</button>`).join('')}</nav>
    <ol class="learn-steps">${c.steps.map((s) => `<li>${s}</li>`).join('')}</ol>
    ${oxRow(c.o, c.x)}
    <p class="caption">일반적인 운전 요령이에요. 차마다 조작 위치·기능 이름이 다르니 내 차 설명서를 먼저 따르세요.</p>
    <div class="wiki-nav">${page > 0 ? `<button type="button" class="btn ghost" data-page="${page - 1}">← ${WIKI[page - 1].title}</button>` : '<span></span>'}
      ${page < WIKI.length - 1 ? `<button type="button" class="btn secondary" data-page="${page + 1}">${WIKI[page + 1].title} →</button>` : ''}</div></div>`;
}

export const wikiIndex = (key) => Math.max(0, WIKI.findIndex((w) => w.key === key));
