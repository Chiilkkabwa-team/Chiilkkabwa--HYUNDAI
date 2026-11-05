// 치일까봐 웹 프로토타입 — 그림(독자 디자인 SVG). 외부 캐릭터·로고 사용 없음.

/** 마스코트 '콩콩이': 노란 콩 모양, 작은 핸들을 쥐고 있다. mood: calm | happy | worried | rest */
export function mascot(mood = 'calm', size = 96) {
  const eyes = {
    calm: `<ellipse cx="47" cy="56" rx="4.6" ry="6" fill="#1B2430"/><ellipse cx="73" cy="56" rx="4.6" ry="6" fill="#1B2430"/>
           <circle cx="48.6" cy="53.6" r="1.6" fill="#fff"/><circle cx="74.6" cy="53.6" r="1.6" fill="#fff"/>`,
    happy: `<path d="M41 57q6-8 12 0M67 57q6-8 12 0" stroke="#1B2430" stroke-width="4" fill="none" stroke-linecap="round"/>`,
    worried: `<ellipse cx="47" cy="58" rx="4.4" ry="5.6" fill="#1B2430"/><ellipse cx="73" cy="58" rx="4.4" ry="5.6" fill="#1B2430"/>
              <circle cx="48.4" cy="55.8" r="1.5" fill="#fff"/><circle cx="74.4" cy="55.8" r="1.5" fill="#fff"/>
              <path d="M39 47l12 3M81 47l-12 3" stroke="#1B2430" stroke-width="3" stroke-linecap="round"/>`,
    rest: `<path d="M41 58h12M67 58h12" stroke="#1B2430" stroke-width="4" stroke-linecap="round"/>`,
  }[mood];
  const mouth = {
    calm: `<path d="M55 68q5 4 10 0" stroke="#1B2430" stroke-width="3" fill="none" stroke-linecap="round"/>`,
    happy: `<path d="M53 66q7 10 14 0z" fill="#1B2430"/><path d="M56 70q4 3 8 0" fill="#F2857B"/>`,
    worried: `<path d="M55 71q5-4 10 0" stroke="#1B2430" stroke-width="3" fill="none" stroke-linecap="round"/>`,
    rest: `<path d="M56 69h8" stroke="#1B2430" stroke-width="3" stroke-linecap="round"/>`,
  }[mood];
  const sweat = mood === 'worried' ? `<path d="M88 40q4 7 0 10q-4-3 0-10z" fill="#9FD3FF"/>` : '';
  const spark = mood === 'happy' ? `<path d="M100 22l2 6 6 2-6 2-2 6-2-6-6-2 6-2z" fill="#FFD66B"/><path d="M18 34l1.5 4 4 1.5-4 1.5-1.5 4-1.5-4-4-1.5 4-1.5z" fill="#7EE2A8"/>` : '';
  return `<svg class="mascot mascot-${mood}" width="${size}" height="${size}" viewBox="0 0 120 120" role="img" aria-label="마스코트 콩콩이">
    ${spark}
    <path d="M60 14c24 0 38 18 38 44 0 30-17 48-38 48S22 88 22 58c0-26 14-44 38-44z" fill="#FFD66B"/>
    <path d="M60 14c24 0 38 18 38 44 0 3-.2 6-.6 9C92 44 80 26 60 24 42 24 30 36 25 52c2-22 15-38 35-38z" fill="#FFE59A"/>
    <path d="M52 16q8-10 18-6" stroke="#3E7B4F" stroke-width="4" fill="none" stroke-linecap="round"/>
    <path d="M70 10q8-2 10 6q-8 2-10-6z" fill="#7EE2A8"/>
    <ellipse cx="38" cy="68" rx="6" ry="3.6" fill="#F7A69B" opacity=".75"/><ellipse cx="82" cy="68" rx="6" ry="3.6" fill="#F7A69B" opacity=".75"/>
    ${eyes}${mouth}${sweat}
    <g transform="translate(60 94)">
      <circle r="15" fill="none" stroke="#2F80ED" stroke-width="5"/>
      <circle r="4" fill="#2F80ED"/><path d="M-13 3h26M0 4v11" stroke="#2F80ED" stroke-width="4"/>
    </g>
    <ellipse cx="42" cy="92" rx="7" ry="6" fill="#FFD66B"/><ellipse cx="78" cy="92" rx="7" ry="6" fill="#FFD66B"/>
  </svg>`;
}

const P = {
  belt: `<path d="M12 6l22 30" /><rect x="24" y="26" width="16" height="12" rx="3"/><path d="M28 32h8"/><path d="M8 38h16"/>`,
  brake: `<path d="M14 10h20l-3 22H17z"/><path d="M18 16h12M18 21h12M18 26h12"/><path d="M24 36v6m-4-4 4 4 4-4"/>`,
  turn: `<path d="M18 14 8 24l10 10"/><path d="M8 24h12"/><path d="M30 14l10 10-10 10"/><path d="M40 24H28"/>`,
  wiper: `<path d="M6 34a18 18 0 0 1 36 0"/><path d="M24 34 12 16"/><circle cx="24" cy="34" r="2.5"/>`,
  beam: `<path d="M22 12c-7 0-12 5-12 12s5 12 12 12z"/><path d="M28 14l12-2M28 20l12-1M28 26l12 1M28 32l12 2"/>`,
  wheel: `<circle cx="24" cy="24" r="17"/><circle cx="24" cy="24" r="4.5"/><path d="M8 22h11.5M28.5 22H40M24 28.5V41"/>`,
  seat: `<path d="M16 6h10a3 3 0 0 1 3 3l-2 18H15l-2-18a3 3 0 0 1 3-3z"/><path d="M12 28h22l2 8H10z"/><path d="M14 36v6M32 36v6"/>`,
  pedal: `<path d="M8 34a16 16 0 0 1 32 0"/><path d="M24 34l8-11"/><path d="M14 22l3 3M24 18v4M34 22l-3 3"/>`,
  parking: `<rect x="8" y="8" width="32" height="32" rx="8"/><path d="M19 34V14h7a6 6 0 0 1 0 12h-7"/>`,
  pact: `<path d="M6 12h22v14H16l-6 5v-5H6z"/><path d="M32 18h10v14h-4v5l-6-5h-8v-4"/>`,
  note: `<rect x="10" y="6" width="28" height="36" rx="4"/><path d="M16 15h16M16 22h16M16 29h10"/><path d="M30 36l4-4 4 4"/>`,
  three: `<circle cx="12" cy="30" r="5"/><circle cx="24" cy="22" r="5"/><circle cx="36" cy="14" r="5"/><path d="M15.5 26.5l5-1.5M27.5 18.5l5-1.5"/>`,
  flag: `<path d="M12 42V6"/><path d="M12 8h22l-5 7 5 7H12"/>`,
  mentor: `<circle cx="16" cy="15" r="5"/><circle cx="33" cy="18" r="4"/><path d="M7 38c0-7 4-12 9-12s9 5 9 12"/><path d="M26 38c0-6 3-10 7-10s7 4 7 10"/>`,
  lock: `<rect x="11" y="21" width="26" height="20" rx="4"/><path d="M16 21v-6a8 8 0 0 1 16 0v6"/><circle cx="24" cy="31" r="2.5"/>`,
  check: `<path d="M10 25l9 9 19-20"/>`,
  wait: `<circle cx="24" cy="24" r="16"/><path d="M24 15v10l6 4"/>`,
  gear: `<rect x="10" y="6" width="28" height="36" rx="6"/><path d="M19 32V16h5a5 5 0 0 1 0 10h-5"/>`,
  pbrake: `<circle cx="24" cy="24" r="12"/><path d="M8 12a20 20 0 0 0 0 24M40 12a20 20 0 0 1 0 24"/><path d="M21 30V18h4a3.5 3.5 0 0 1 0 7h-4"/>`,
  road: `<path d="M18 42 22 6M30 42 26 6"/><path d="M24 36v-4M24 26v-4M24 16v-4"/>`,
  star: `<path d="M24 6l5 11 12 1-9 8 3 12-11-6-11 6 3-12-9-8 12-1z"/>`,
  user: `<circle cx="24" cy="17" r="7"/><path d="M10 41c0-8 6-14 14-14s14 6 14 14"/>`,
  close: `<path d="M12 12l24 24M36 12 12 36"/>`,
  sound: `<path d="M8 19h7l9-7v24l-9-7H8z"/><path d="M31 18a8 8 0 0 1 0 12M35 13a14 14 0 0 1 0 22"/>`,
  text: `<path d="M8 38 17 10h2l9 28M11 29h14"/><path d="M30 38l5-14h1l5 14M32 33h8"/>`,
  trash: `<path d="M10 13h28M19 13V8h10v5M14 13l2 27h16l2-27"/>`,
  console: `<rect x="6" y="10" width="36" height="28" rx="5"/><circle cx="17" cy="24" r="5"/><path d="M28 19h8M28 24h8M28 29h5"/>`,
  arrowUp: `<path d="M24 38V10M12 22l12-12 12 12"/>`,
  arrowDown: `<path d="M24 10v28M12 26l12 12 12-12"/>`,
  reset: `<path d="M10 24a14 14 0 1 0 4-10"/><path d="M10 8v8h8"/>`,
  info: `<circle cx="24" cy="24" r="17"/><path d="M24 22v11M24 15v1"/>`,
  home: `<path d="M7 22 24 8l17 14"/><path d="M12 20v20h24V20"/><path d="M20 40V28h8v12"/>`,
};

export function icon(name, size = 24, cls = '') {
  return `<svg class="ico ${cls}" width="${size}" height="${size}" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[name] || ''}</svg>`;
}

/** 배지 엠블럼: 얻음 = 채움 + 리본, 못 얻음 = 점선 테두리 */
export function badgeArt(b, earned, size = 72) {
  const h = b.hue;
  const id = 'g' + Math.random().toString(36).slice(2, 8);
  if (!earned) {
    return `<svg width="${size}" height="${size}" viewBox="0 0 72 72" aria-hidden="true">
      <circle cx="36" cy="34" r="27" fill="#18202A" stroke="#3B4656" stroke-width="2.5" stroke-dasharray="5 5"/>
      <g transform="translate(22 20) scale(.58)" stroke="#5A6779" fill="none" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round">${P[b.icon]}</g>
    </svg>`;
  }
  return `<svg width="${size}" height="${size}" viewBox="0 0 72 72" aria-hidden="true">
    <defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="hsl(${h} 75% 62%)"/><stop offset="1" stop-color="hsl(${h + 25} 65% 42%)"/></linearGradient></defs>
    <path d="M24 56l-6 14 9-3 5 6 4-13M48 56l6 14-9-3-5 6-4-13" fill="hsl(${h} 45% 35%)"/>
    <circle cx="36" cy="34" r="27" fill="url(#${id})"/>
    <circle cx="36" cy="34" r="22" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="2"/>
    <g transform="translate(22 20) scale(.58)" stroke="#fff" fill="none" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round">${P[b.icon]}</g>
  </svg>`;
}

/** 시스템 Driving view 자리(앱 영역 아님) — 지도 느낌의 정적 그림 */
export function drivingViewArt() {
  return `<svg class="dv-map" viewBox="0 0 300 420" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
    <rect width="300" height="420" fill="#121A23"/>
    <g stroke="#1E2935" stroke-width="18" fill="none" stroke-linecap="round">
      <path d="M-20 90h360M-20 300h360M70 -20v460M230 -20v460"/>
    </g>
    <g stroke="#26374A" stroke-width="30" fill="none" stroke-linecap="round"><path d="M150 460V250q0-40 40-60l140-70"/></g>
    <path d="M150 460V250q0-40 40-60l140-70" stroke="#2F80ED" stroke-width="8" fill="none" stroke-linecap="round" opacity=".9"/>
    <g fill="#18222E"><rect x="88" y="110" width="50" height="60" rx="6"/><rect x="250" y="210" width="40" height="70" rx="6"/><rect x="10" y="320" width="45" height="60" rx="6"/><rect x="170" y="20" width="45" height="50" rx="6"/></g>
    <path d="M150 352l-14 30 14-8 14 8z" fill="#E9EEF5"/>
  </svg>`;
}
