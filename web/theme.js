// 치일까봐 — 테마(다크/라이트)
//
// 왜 둘 다 있나:
//   Pleos Connect는 "눈부심을 줄이는 **다크 테마를 기본**으로 **라이트 테마로 변경도 가능**"하다
//   (현대자동차그룹 공식 소개글, 슬림 디스플레이 설명). 실제 차가 그렇게 동작하므로
//   프로토타입도 같은 선택지를 준다. 기본은 다크다.
//
// 색은 styles.css 의 :root / :root[data-theme="light"] 한 곳에만 있다.
// 캔버스(가상 주차·가상 도로)도 리터럴을 쓰지 않고 여기서 토큰 값을 읽어 간다.
// 그래야 테마를 바꿀 때 화면과 그림이 따로 놀지 않는다.

const KEY = 'chiilkkabwa.theme';
export const THEMES = ['dark', 'light'];

let cache = null;            // 토큰 값 캐시(테마가 바뀌면 비운다)
const listeners = new Set();

/** 저장된 선택 → 없으면 다크(Pleos 기본값) */
export function saved() {
  try {
    const v = localStorage.getItem(KEY);
    return THEMES.includes(v) ? v : null;
  } catch { return null; }
}

export function current() {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
}

/** 테마 적용. 저장은 persist=true 일 때만(자동 적용과 사용자 선택을 구분) */
export function apply(theme, persist = true) {
  const t = THEMES.includes(theme) ? theme : 'dark';
  document.documentElement.dataset.theme = t;
  cache = null;
  if (persist) { try { localStorage.setItem(KEY, t); } catch { /* 저장소 없음 */ } }
  listeners.forEach((fn) => { try { fn(t); } catch { /* 구독자 오류는 무시 */ } });
  return t;
}

export function toggle() { return apply(current() === 'light' ? 'dark' : 'light'); }

/** 테마가 바뀔 때 다시 그려야 하는 쪽에서 구독한다 */
export function onChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }

/**
 * CSS 토큰 값을 읽는다. 캔버스에서 쓴다.
 * @param {string} name 예: '--road-bg'
 */
export function c(name) {
  if (!cache) cache = new Map();
  if (cache.has(name)) return cache.get(name);
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888';
  cache.set(name, v);
  return v;
}

/** 캔버스에서 자주 쓰는 '토큰 + 투명도' — color-mix 대신 직접 계산한다(캔버스는 CSS 함수를 모른다) */
export function alpha(name, a) {
  const v = c(name);
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(v);
  if (m) {
    let h = m[1];
    if (h.length === 3) h = h.split('').map((x) => x + x).join('');
    const n = parseInt(h, 16);
    return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
  }
  const rgb = /rgba?\(([^)]+)\)/.exec(v);
  if (rgb) {
    const p = rgb[1].split(/[ ,/]+/).filter(Boolean).slice(0, 3);
    return `rgba(${p.join(', ')}, ${a})`;
  }
  return v;
}

/** 첫 로드에 저장값을 적용한다(저장값이 없으면 다크 그대로 둔다) */
export function init() {
  const v = saved();
  if (v) apply(v, false);
  return current();
}
