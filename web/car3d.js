// 치일까봐 — 정차 중 3D 차 모형(A06). Pleos 소개의 '정차 중 차량 3D 그래픽'을 웹에서 가볍게.
// - CSS 3D 상자 몇 개뿐: 외부 3D 모델·라이브러리·이미지 없음(S11 권리 문제 없음), 스스로 움직이지 않아 CPU를 쓰지 않는다.
// - 정차 중 화면(안전 확인)에서만 쓴다. 주행 잠금 화면에는 넣지 않는다(S04).
// - 끌거나 ←→ 키로 돌려 본다. 주차 브레이크·기어 P 상태를 차에 표시한다.

const L = 176, W = 80, H = 30;            // 차체 길이·폭·높이(px)
const CL = 92, CW = 68, CH = 26;          // 지붕(유리 칸)

/** 가로(x)=길이, 깊이(z)=폭, 세로(y)=높이인 상자 하나 */
function box(cls, w, d, h, x = 0, y = 0) {
  const f = (name, fw, fh, t) => `<i class="f ${name}" style="width:${fw}px;height:${fh}px;margin:${-fh / 2}px 0 0 ${-fw / 2}px;transform:${t}"></i>`;
  return `<div class="c3-box ${cls}" style="transform:translate3d(${x}px,${y}px,0)">
    ${f('fr', d, h, `rotateY(90deg) translateZ(${w / 2}px)`)}${f('bk', d, h, `rotateY(-90deg) translateZ(${w / 2}px)`)}
    ${f('sd', w, h, `translateZ(${d / 2}px)`)}${f('sd', w, h, `rotateY(180deg) translateZ(${d / 2}px)`)}
    ${f('tp', w, d, `rotateX(90deg) translateZ(${h / 2}px)`)}${f('bt', w, d, `rotateX(-90deg) translateZ(${h / 2}px)`)}
  </div>`;
}

const wheel = (x, z, rear) => `<i class="c3-wheel${rear ? ' rear' : ''}" style="transform:translate3d(${x}px,${H / 2 + 2}px,${z}px)"></i>`;

export function html() {
  const wx = L / 2 - 34, wz = W / 2 + 1;
  return `<div class="c3" tabindex="0" role="img" aria-label="내 차 3D 모형. 끌거나 왼쪽·오른쪽 화살표로 돌려 봐요.">
    <div class="c3-car">
      <i class="c3-shadow"></i>
      ${box('body', L, W, H)}
      ${box('cab', CL, CW, CH, -10, -(H / 2 + CH / 2))}
      ${wheel(wx, wz, false)}${wheel(wx, -wz, false)}${wheel(-wx, wz, true)}${wheel(-wx, -wz, true)}
      <b class="c3-p" style="transform:translate3d(-10px,${-(H / 2 + CH) - 1}px,0) rotateX(90deg)">P</b>
    </div>
  </div>`;
}

/** 끌어서·키로 돌리기. 돌린 각도는 화면이 다시 그려져도 유지한다. */
let ry = -32;
export function bind(root) {
  const el = root.querySelector('.c3'); if (!el) return;
  const car = el.querySelector('.c3-car');
  const apply = () => car.style.setProperty('--ry', `${ry}deg`);
  apply();
  let x0 = null;
  el.addEventListener('pointerdown', (e) => { x0 = e.clientX; el.setPointerCapture?.(e.pointerId); });
  el.addEventListener('pointermove', (e) => { if (x0 == null) return; ry += (e.clientX - x0) * 0.6; x0 = e.clientX; apply(); });
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((t) => el.addEventListener(t, () => { x0 = null; }));
  el.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    e.preventDefault(); e.stopPropagation(); ry += e.key === 'ArrowLeft' ? -15 : 15; apply();
  });
}

/** 차량 신호를 모형에 표시: 주차 브레이크 → 뒷바퀴, 기어 P → 지붕의 P */
export function show(root, { parkingBrake, gearP }) {
  const el = root.querySelector('.c3'); if (!el) return;
  el.dataset.pb = parkingBrake ? 'on' : 'off';
  el.dataset.p = gearP ? 'on' : 'off';
}
