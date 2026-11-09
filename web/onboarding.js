// 치일까봐 — 온보딩(처음 온 사람에게 "이게 뭔지" 보여 주는 화면)
//
// 왜 필요한가:
//   이 웹판은 차량 화면 + 가상 콘솔이라는 낯선 구조다. 설명 없이 열면
//   "왼쪽은 뭐고 오른쪽은 뭔지" 몰라서 아무것도 안 하고 닫는다.
//   그래서 제품의 흐름(주차 중 → 주행 중 → 주차 후)을 **그대로** 따라가며 보여 준다.
//   글로 읽히는 설명이 아니라 '움직이는 그림'이라, 한 장면이 한 가지만 말한다.
//
// 규칙:
//   - 움직임은 transform·opacity만 쓴다(합성만으로 끝나 CPU를 안 먹는다).
//   - prefers-reduced-motion이면 마지막 상태를 바로 보여 준다(움직임 없음).
//   - 글자 대비는 라이트·다크 모두 AA를 넘겨야 한다 → 색은 토큰만 쓴다.

export const STEPS = [
  {
    key: 'what',
    tag: '무엇인가요',
    title: '면허는 있는데,<br>운전석이 아직 낯선 사람에게',
    body: '도로 연수는 비싸고, 가족에게 배우면 다투기 쉬워요. 그 사이를 차가 채웁니다. <b>확인은 차가, 판단은 사람이.</b>',
    note: '앱은 차를 조종하지 않아요. 차량 신호를 <b>읽기만</b> 합니다.',
  },
  {
    key: 'park',
    tag: '연습 전 5분 · 주차 중',
    title: '조작하면<br>차가 확인해 줘요',
    body: '안전벨트를 매면 차가 "맸다"고 알려 줘요. 브레이크를 밟았다 떼면 "했다"고 확인해 줘요. 맞는지 틀린지 물어볼 사람이 없어도 돼요.',
    note: '기어 P + 주차 브레이크 + 주행 제한 없음, 셋 다 맞을 때만 열려요.',
  },
  {
    key: 'drive',
    tag: '주행 중 · 0터치',
    title: '화면은 쉬고,<br>목소리만 남아요',
    body: '달릴 땐 화면이 잠겨요. 대신 <b>꼭 필요할 때만 한마디</b> 합니다. “방금 출발이 조금 급했어요.”',
    note: '주행당 최대 5회 · 최소 간격 45초. 내비·통화·경고음이 나오면 바로 양보해요.',
  },
  {
    key: 'review',
    tag: '연습 후 3분 · 다시 주차',
    title: '오늘 있었던 일을<br>같이 돌아봐요',
    body: '언제 무슨 일이 있었는지 시간순으로 보여 줘요. 혼자 봐도 되고, 옆에 앉았던 사람과 같이 봐도 돼요.',
    note: '점수가 아니라 그 순간의 사실이에요. 기록은 기기 안에만 남아요.',
  },
  {
    key: 'howto',
    tag: '이 체험판 쓰는 법',
    title: '왼쪽은 차량 화면,<br>오른쪽은 가상 콘솔',
    body: '오른쪽(휴대폰은 아래 버튼) 콘솔에서 기어·브레이크·벨트를 움직이면, 왼쪽 앱이 그 신호를 읽어서 반응해요. 콘솔 맨 위 <b>‘다음 조작’</b>만 따라가면 돼요.',
    note: '맨 위에서 <b>연습 · 평가 · 강습</b> 모드를 고를 수 있어요. 평가·강습은 <b>가상 시뮬레이션</b>이에요.',
  },
];

/** 단계마다 다른 그림. 모두 같은 120×88 좌표계를 쓴다. */
function art(key) {
  const car = (cls = '') => `
    <g class="ob-car ${cls}">
      <rect x="50" y="30" width="20" height="34" rx="6" fill="var(--primary)"/>
      <rect x="53.5" y="36" width="13" height="8" rx="2" fill="var(--car-glass)"/>
      <rect x="53.5" y="52" width="13" height="6" rx="2" fill="var(--car-glass)"/>
    </g>`;
  if (key === 'what') {
    return `<svg viewBox="0 0 120 88" role="img" aria-label="주차된 차 안에 앉은 사람">
      <rect x="8" y="10" width="104" height="68" rx="14" fill="var(--panel)" stroke="var(--line)" stroke-width="1.5"/>
      ${car()}
      <g class="ob-ring"><circle cx="60" cy="47" r="26" fill="none" stroke="var(--primary)" stroke-width="2" opacity=".55"/></g>
      <g class="ob-ring ob-ring-2"><circle cx="60" cy="47" r="26" fill="none" stroke="var(--primary)" stroke-width="2" opacity=".35"/></g>
    </svg>`;
  }
  if (key === 'park') {
    const row = (y, i) => `
      <g class="ob-check" style="--i:${i}">
        <rect x="20" y="${y}" width="80" height="14" rx="7" fill="color-mix(in srgb, var(--success) 16%, transparent)"/>
        <path d="M27 ${y + 7}l3.4 3.4L37 ${y + 3.6}" fill="none" stroke="var(--success)" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
        <rect x="43" y="${y + 4.5}" width="${34 - i * 6}" height="5" rx="2.5" fill="var(--success)" opacity=".5"/>
      </g>`;
    return `<svg viewBox="0 0 120 88" role="img" aria-label="확인 항목 세 개가 차례로 확인으로 바뀐다">
      <rect x="8" y="10" width="104" height="68" rx="14" fill="var(--panel)" stroke="var(--line)" stroke-width="1.5"/>
      ${row(22, 0)}${row(40, 1)}${row(58, 2)}
    </svg>`;
  }
  if (key === 'drive') {
    return `<svg viewBox="0 0 120 88" role="img" aria-label="화면은 잠기고 말풍선이 한 번 떠올랐다 사라진다">
      <rect x="8" y="10" width="104" height="68" rx="14" fill="var(--panel)" stroke="var(--line)" stroke-width="1.5"/>
      <g class="ob-road">
        <rect x="8" y="10" width="104" height="68" rx="14" fill="var(--road-asphalt)" opacity=".5"/>
        <g stroke="var(--road-line)" stroke-width="2.4" stroke-linecap="round" opacity=".55">
          <path class="ob-dash" d="M60 4v14M60 26v14M60 48v14M60 70v14"/>
        </g>
      </g>
      ${car('ob-car-drive')}
      <g class="ob-bubble">
        <rect x="18" y="14" width="84" height="20" rx="10" fill="var(--surface)" stroke="var(--primary)" stroke-width="1.5"/>
        <circle cx="28" cy="24" r="3.4" fill="var(--primary)"/>
        <rect x="36" y="21" width="56" height="6" rx="3" fill="var(--sub)" opacity=".6"/>
      </g>
    </svg>`;
  }
  if (key === 'review') {
    const line = (y, i, w) => `
      <g class="ob-row" style="--i:${i}">
        <circle cx="26" cy="${y + 6}" r="4" fill="var(--primary)"/>
        <rect x="36" y="${y + 1}" width="${w}" height="5" rx="2.5" fill="var(--text)" opacity=".75"/>
        <rect x="36" y="${y + 9}" width="${w - 14}" height="4" rx="2" fill="var(--muted)" opacity=".6"/>
      </g>`;
    return `<svg viewBox="0 0 120 88" role="img" aria-label="주행 기록이 시간순으로 쌓인다">
      <rect x="8" y="10" width="104" height="68" rx="14" fill="var(--panel)" stroke="var(--line)" stroke-width="1.5"/>
      <path d="M26 20v48" stroke="var(--line-2)" stroke-width="1.5"/>
      ${line(18, 0, 56)}${line(38, 1, 48)}${line(58, 2, 62)}
    </svg>`;
  }
  // howto
  return `<svg viewBox="0 0 120 88" role="img" aria-label="왼쪽 차량 화면과 오른쪽 가상 콘솔">
    <rect x="6" y="12" width="62" height="64" rx="10" fill="var(--panel)" stroke="var(--line)" stroke-width="1.5"/>
    <rect x="13" y="20" width="26" height="5" rx="2.5" fill="var(--muted)" opacity=".7"/>
    <rect x="13" y="32" width="48" height="26" rx="6" fill="var(--surface2)"/>
    <rect x="13" y="64" width="34" height="5" rx="2.5" fill="var(--muted)" opacity=".5"/>
    <rect x="74" y="12" width="40" height="64" rx="10" fill="var(--surface)" stroke="var(--line)" stroke-width="1.5"/>
    <g class="ob-knob">
      <rect x="81" y="22" width="26" height="9" rx="4.5" fill="var(--surface3)"/>
      <circle cx="86" cy="26.5" r="3.2" fill="var(--primary)"/>
    </g>
    <rect x="81" y="38" width="26" height="5" rx="2.5" fill="var(--muted)" opacity=".5"/>
    <rect x="81" y="48" width="18" height="5" rx="2.5" fill="var(--muted)" opacity=".5"/>
    <g class="ob-arrow"><path d="M72 46h-6m0 0l3-3m-3 3l3 3" stroke="var(--primary)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></g>
  </svg>`;
}

/**
 * 온보딩 전체 HTML. 모달 안에 넣는다.
 * @param {number} i 현재 단계
 */
export function html(i = 0) {
  const s = STEPS[i];
  const last = i === STEPS.length - 1;
  return `<div class="ob" data-step="${s.key}">
    <div class="ob-art">${art(s.key)}</div>
    <div class="ob-text">
      <span class="ob-tag">${s.tag}</span>
      <h2 class="ob-title" tabindex="-1">${s.title}</h2>
      <p class="ob-body">${s.body}</p>
      <p class="ob-note">${s.note}</p>
    </div>
    <div class="ob-foot">
      <ol class="ob-dots" aria-label="${STEPS.length}단계 중 ${i + 1}단계">
        ${STEPS.map((x, k) => `<li><button type="button" class="ob-dot${k === i ? ' on' : ''}" data-go="${k}" aria-label="${k + 1}. ${x.tag}"${k === i ? ' aria-current="step"' : ''}></button></li>`).join('')}
      </ol>
      <div class="ob-btns">
        ${i > 0 ? '<button type="button" class="btn secondary" data-ob="prev">이전</button>' : '<button type="button" class="btn ghost-btn" data-ob="skip">건너뛰기</button>'}
        ${last
          ? '<button type="button" class="btn secondary" data-ob="demo">자동 시연 보기</button><button type="button" class="btn primary" data-ob="done">직접 해 보기</button>'
          : '<button type="button" class="btn primary" data-ob="next">다음</button>'}
      </div>
    </div>
  </div>`;
}
