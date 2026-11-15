// 치일까봐 — 정차 중 3D 자동차(A06). Pleos 소개의 '정차 중 차량 3D 그래픽'을 웹에서.
// - three.js(MIT, web/vendor에 포함 — 외부 서버·CDN 없음)로 차를 코드로 만든다. 외부 3D 모델·이미지·폰트 없음(S11).
// - 차량 신호를 그대로 보여 준다: 기어 P, 주차 브레이크(뒷바퀴 빨강), 브레이크등, 방향지시등(켜진 쪽 노랑, 깜빡이지 않음),
//   전조등, 와이퍼, 핸들 각도 → 앞바퀴 각도, 안전벨트.
// - 정차 중 화면(안전 확인·워밍업)에서만 쓴다. 주행 잠금 화면에는 넣지 않는다(S04).
// - 스스로 움직이지 않는다: 신호가 바뀌거나 끌어서 돌릴 때만 한 번 그린다(CPU·배터리 거의 0).
// - WebGL이 없거나 불러오기 실패하면 예전 CSS 상자 모형(또는 넘겨준 그림)을 그대로 보여 준다.

const ok = (sig) => sig?.status === 'OK' && sig.value != null;

/** 차량 스냅샷 → 3D 차 상태(쓸 수 없는 신호는 '꺼짐/모름'으로) */
export function carState(s) {
  return {
    gearP: ok(s.gear) && s.gear.value === 'P',
    parkingBrake: ok(s.parkingBrakeOn) && s.parkingBrakeOn.value === true,
    brake: ok(s.brakePressed) && s.brakePressed.value === true,
    turn: ok(s.turnSignal) ? s.turnSignal.value : 'NONE',
    lowBeam: ok(s.lowBeamOn) && s.lowBeamOn.value === true,
    wiper: ok(s.wiperOn) && s.wiperOn.value === true,
    belt: ok(s.driverBeltBuckled) ? s.driverBeltBuckled.value === true : null,
    steeringDeg: ok(s.steeringDeg) ? s.steeringDeg.value : 0,
  };
}

/** 핸들 각도 → 앞바퀴 각도(조향비 15:1, 최대 35°). core.ParkingSim과 같은 값 */
export const wheelAngleDeg = (steeringDeg) => Math.max(-35, Math.min(35, (steeringDeg || 0) / 15));

/** 화면 읽기 프로그램용 설명(그림을 못 보는 사람도 같은 내용을 듣는다) */
export function describe(st) {
  const turn = { LEFT: '왼쪽 방향지시등 켜짐', RIGHT: '오른쪽 방향지시등 켜짐' }[st.turn];
  const w = Math.round(wheelAngleDeg(st.steeringDeg));
  return ['내 차 3D 모형.', st.gearP ? '기어 P.' : '기어 P 아님.', st.parkingBrake ? '주차 브레이크 걸림.' : '',
    st.brake ? '브레이크등 켜짐.' : '', turn ? `${turn}.` : '', st.lowBeam ? '전조등 켜짐.' : '', st.wiper ? '와이퍼 켜짐.' : '',
    st.belt === true ? '운전석 안전벨트 맴.' : st.belt === false ? '운전석 안전벨트 안 맴.' : '',
    w ? `앞바퀴 ${Math.abs(w)}도 ${w > 0 ? '오른쪽' : '왼쪽'}.` : '앞바퀴 똑바로.', '끌거나 왼쪽·오른쪽 화살표로 돌려 봐요.']
    .filter(Boolean).join(' ');
}

/** 카메라 자리: az(옆으로 돈 각, 0 = 앞), el(높이 각), d(거리), ty(바라보는 높이), glass(유리 불투명도) */
export const VIEWS = {
  gate: { az: -0.72, el: 0.34, d: 7.4, ty: 0.55, tx: 0, glass: 0.82 },
  rear: { az: Math.PI + 0.55, el: 0.3, d: 8.6, ty: 0.55, tx: -0.2, glass: 0.82 },
  front: { az: 0.55, el: 0.3, d: 8.8, ty: 0.55, tx: 0.3, glass: 0.82 },
  turn: { az: 0.0, el: 0.32, d: 8.6, ty: 0.55, tx: 0.4, glass: 0.82 },
  wiper: { az: 0.25, el: 0.72, d: 7.2, ty: 0.9, tx: 0.5, glass: 0.6 },
  steer: { az: 0.5, el: 0.14, d: 8.2, ty: 0.45, tx: 0.8, glass: 0.82 },
  cabin: { az: -Math.PI / 2 + 0.12, el: 0.3, d: 7.8, ty: 1.0, tx: -0.3, glass: 0.1 },
};
/** 워밍업 미션 → 그 조작이 보이는 쪽으로 카메라 */
export const MISSION_VIEW = { SEAT_BELT: 'cabin', BRAKE_PRESS_RELEASE: 'rear', TURN_SIGNAL: 'turn', WIPER: 'wiper', LOW_BEAM: 'front', STEERING_CENTER: 'steer' };

// ── CSS 상자 모형(WebGL이 없을 때) ──────────────────────────────
const L = 176, W = 80, H = 30;            // 차체 길이·폭·높이(px)
const CL = 92, CW = 68, CH = 26;          // 지붕(유리 칸)
function box(cls, w, d, h, x = 0, y = 0) {
  const f = (name, fw, fh, t) => `<i class="f ${name}" style="width:${fw}px;height:${fh}px;margin:${-fh / 2}px 0 0 ${-fw / 2}px;transform:${t}"></i>`;
  return `<div class="c3-box ${cls}" style="transform:translate3d(${x}px,${y}px,0)">
    ${f('fr', d, h, `rotateY(90deg) translateZ(${w / 2}px)`)}${f('bk', d, h, `rotateY(-90deg) translateZ(${w / 2}px)`)}
    ${f('sd', w, h, `translateZ(${d / 2}px)`)}${f('sd', w, h, `rotateY(180deg) translateZ(${d / 2}px)`)}
    ${f('tp', w, d, `rotateX(90deg) translateZ(${h / 2}px)`)}${f('bt', w, d, `rotateX(-90deg) translateZ(${h / 2}px)`)}
  </div>`;
}
const cssWheel = (x, z, rear) => `<i class="c3-wheel${rear ? ' rear' : ''}" style="transform:translate3d(${x}px,${H / 2 + 2}px,${z}px)"></i>`;
function cssCar() {
  const wx = L / 2 - 34, wz = W / 2 + 1;
  return `<div class="c3-car">
      <i class="c3-shadow"></i>
      ${box('body', L, W, H)}
      ${box('cab', CL, CW, CH, -10, -(H / 2 + CH / 2))}
      ${cssWheel(wx, wz, false)}${cssWheel(wx, -wz, false)}${cssWheel(-wx, wz, true)}${cssWheel(-wx, -wz, true)}
      <b class="c3-p" style="transform:translate3d(-10px,${-(H / 2 + CH) - 1}px,0) rotateX(90deg)">P</b>
    </div>`;
}

/**
 * 3D 차 자리. view: VIEWS 키. fallback: WebGL이 없을 때 대신 보일 HTML(없으면 CSS 상자 모형).
 * mini: 워밍업 그림 칸처럼 작은 자리(이름표 없음)
 */
export function html({ view = 'gate', fallback = null, mini = false } = {}) {
  return `<div class="c3${mini ? ' mini' : ''}" data-view="${view}" tabindex="0" role="img" aria-label="내 차 3D 모형. 끌거나 왼쪽·오른쪽 화살표로 돌려 봐요.">
    <div class="c3-fb">${fallback ?? cssCar()}</div>
  </div>`;
}

// ── WebGL 엔진(한 개만 만들어 화면을 옮겨 다닌다 — WebGL 컨텍스트가 쌓이지 않게) ──
let enginePromise = null;
let engine = null;
let last = null;            // 마지막 차 상태
let cssRy = -32;            // CSS 모형 돌린 각

function webglAvailable() {
  try { const c = document.createElement('canvas'); return !!(c.getContext('webgl2') || c.getContext('webgl')); } catch { return false; }
}

function ensureEngine() {
  if (!enginePromise) {
    enginePromise = (typeof document !== 'undefined' && webglAvailable()
      ? import('./vendor/three.module.min.js').then(buildEngine) : Promise.resolve(null))
      .catch(() => null)
      .then((e) => (engine = e));
  }
  return enginePromise;
}

function buildEngine(T) {
  const renderer = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
  renderer.setClearColor(0x000000, 0);
  const canvas = renderer.domElement;
  canvas.setAttribute('aria-hidden', 'true');
  const scene = new T.Scene();
  const camera = new T.PerspectiveCamera(30, 2, 0.1, 60);

  scene.add(new T.HemisphereLight(0xdfe9ff, 0x2a3240, 1.5));
  const sun = new T.DirectionalLight(0xffffff, 2.2); sun.position.set(3, 6, 4); scene.add(sun);
  const fill = new T.DirectionalLight(0x9fc3ff, 0.6); fill.position.set(-4, 2, -3); scene.add(fill);

  const mat = (color, o = {}) => new T.MeshStandardMaterial({ color, roughness: 0.45, metalness: 0.25, ...o });
  const M = {
    paint: mat(0x2f80ed, { roughness: 0.32, metalness: 0.35 }),
    dark: mat(0x1b2430, { roughness: 0.7 }),
    trim: mat(0x2a3544, { roughness: 0.6 }),
    tire: mat(0x15191f, { roughness: 0.9, metalness: 0 }),
    rim: mat(0xc9d3df, { roughness: 0.3, metalness: 0.8 }),
    rimPb: mat(0xd9483f, { roughness: 0.4, emissive: 0x7a1410, emissiveIntensity: 0.6 }),
    glass: new T.MeshStandardMaterial({ color: 0x223246, roughness: 0.08, metalness: 0.4, transparent: true, opacity: 0.82 }),
    head: mat(0x8a96a6, { roughness: 0.2, emissive: 0x000000 }),
    headOn: mat(0xffffff, { emissive: 0xfff4c8, emissiveIntensity: 2.2 }),
    tail: mat(0x5a1414, { emissive: 0x3a0808, emissiveIntensity: 0.6 }),
    tailOn: mat(0xff3b30, { emissive: 0xff2a20, emissiveIntensity: 2.4 }),
    amber: mat(0x6b4a12, { emissive: 0x241605, emissiveIntensity: 0.5 }),
    amberOn: mat(0xffb020, { emissive: 0xffa000, emissiveIntensity: 2.6 }),
    seat: mat(0x3b4656, { roughness: 0.8 }),
    beltOn: mat(0x27ae60, { emissive: 0x0f5a2c, emissiveIntensity: 0.8 }),
    beltOff: mat(0xd9483f, { emissive: 0x5a1410, emissiveIntensity: 0.6 }),
    wiper: mat(0x0f141b, { roughness: 0.6 }),
  };

  const car = new T.Group(); scene.add(car);
  const extrude = (pts, depth, bevel = 0.05) => {
    const sh = new T.Shape(); pts.forEach(([x, y], i) => (i ? sh.lineTo(x, y) : sh.moveTo(x, y)));
    const g = new T.ExtrudeGeometry(sh, { depth, bevelEnabled: bevel > 0, bevelThickness: bevel, bevelSize: bevel, bevelSegments: 3, curveSegments: 18 });
    g.translate(0, 0, -depth / 2); return g;
  };

  // 차체(옆모습을 폭만큼 밀어 낸 것). 앞 = +x, 위 = +y, 오른쪽 = +z. 바퀴 자리는 반원으로 파 냈다
  const R = 0.36, WX = 1.36;
  const bodyShape = new T.Shape();
  bodyShape.moveTo(-2.12, 0.3); bodyShape.lineTo(-WX - 0.47, 0.3);
  bodyShape.absarc(-WX, 0.34, 0.47, Math.PI, 0, true);
  bodyShape.lineTo(WX - 0.47, 0.3);
  bodyShape.absarc(WX, 0.34, 0.47, Math.PI, 0, true);
  bodyShape.lineTo(2.12, 0.3); bodyShape.lineTo(2.24, 0.46); bodyShape.lineTo(2.22, 0.74);
  bodyShape.quadraticCurveTo(1.7, 0.9, 0.96, 0.96); bodyShape.lineTo(-1.45, 0.98);
  bodyShape.quadraticCurveTo(-2.0, 0.97, -2.2, 0.82); bodyShape.lineTo(-2.24, 0.52); bodyShape.lineTo(-2.12, 0.3);
  const bodyGeo = new T.ExtrudeGeometry(bodyShape, { depth: 1.7, bevelEnabled: true, bevelThickness: 0.06, bevelSize: 0.06, bevelSegments: 3, curveSegments: 20 });
  bodyGeo.translate(0, 0, -0.85);
  car.add(new T.Mesh(bodyGeo, M.paint));
  // 아래 검은 띠(범퍼·사이드 스커트)
  const skirt = new T.Mesh(new T.BoxGeometry(2.0, 0.08, 1.84), M.trim); skirt.position.set(0, 0.3, 0); car.add(skirt);
  // 유리 칸(창문) + 지붕
  const cabin = new T.Mesh(extrude([[0.98, 0.95], [0.28, 1.4], [-0.95, 1.43], [-1.6, 0.98]], 1.46, 0.04), M.glass); car.add(cabin);
  const roof = new T.Mesh(extrude([[0.3, 1.39], [0.18, 1.47], [-0.9, 1.49], [-1.02, 1.41]], 1.4, 0.03), M.paint); car.add(roof);
  // 앞·뒤 기둥(유리를 몇 칸으로 나눠 차처럼 보이게)
  for (const z of [-0.74, 0.74]) {
    const bp = new T.Mesh(new T.BoxGeometry(0.1, 0.48, 0.04), M.dark); bp.position.set(-0.28, 1.18, z); car.add(bp);
  }
  // 운전석(왼쪽 = -z)과 안전벨트(유리를 통해 보인다)
  const seat = new T.Group(); seat.position.set(-0.35, 0.95, -0.38); car.add(seat);
  const cushion = new T.Mesh(new T.BoxGeometry(0.5, 0.1, 0.46), M.seat); seat.add(cushion);
  const back = new T.Mesh(new T.BoxGeometry(0.1, 0.5, 0.46), M.seat); back.position.set(-0.24, 0.25, 0); back.rotation.z = 0.15; seat.add(back);
  const belt = new T.Mesh(new T.BoxGeometry(0.04, 0.6, 0.06), M.beltOff); belt.position.set(-0.17, 0.22, 0); belt.rotation.x = 0.75; seat.add(belt);
  // 사이드 미러(방향지시등 보조등 포함)
  const mirrors = {};
  for (const [side, z] of [['LEFT', -0.95], ['RIGHT', 0.95]]) {
    const m = new T.Group(); m.position.set(0.82, 1.0, z); car.add(m);
    const shell = new T.Mesh(new T.BoxGeometry(0.16, 0.12, 0.18), M.paint); m.add(shell);
    const rep = new T.Mesh(new T.BoxGeometry(0.05, 0.03, 0.14), M.amber); rep.position.set(0.06, -0.05, 0); m.add(rep);
    mirrors[side] = rep;
  }
  // 등: 전조등·후미등(브레이크등)·방향지시등(앞·뒤 모서리)
  const lamp = (w, h, d, x, y, z, m) => { const l = new T.Mesh(new T.BoxGeometry(w, h, d), m); l.position.set(x, y, z); car.add(l); return l; };
  const heads = [-0.58, 0.58].map((z) => lamp(0.1, 0.16, 0.46, 2.22, 0.67, z, M.head));
  const tails = [-0.56, 0.56].map((z) => lamp(0.1, 0.17, 0.52, -2.24, 0.74, z, M.tail));
  const ambers = { LEFT: [lamp(0.12, 0.1, 0.22, 2.2, 0.53, -0.78, M.amber), lamp(0.12, 0.1, 0.2, -2.22, 0.58, -0.8, M.amber)],
    RIGHT: [lamp(0.12, 0.1, 0.22, 2.2, 0.53, 0.78, M.amber), lamp(0.12, 0.1, 0.2, -2.22, 0.58, 0.8, M.amber)] };
  ambers.LEFT.push(mirrors.LEFT); ambers.RIGHT.push(mirrors.RIGHT);
  lamp(0.06, 0.16, 0.9, 2.26, 0.47, 0, M.dark);   // 앞 그릴
  // 우리 엠블럼(치일까봐 마스코트 콩 모양) — 실제 자동차 회사 로고는 상표라 쓰지 않는다(O04·S11)
  const emblemTex = (() => {
    const c = document.createElement('canvas'); c.width = 128; c.height = 128; const x = c.getContext('2d');
    x.fillStyle = '#c9d3df'; x.beginPath(); x.arc(64, 64, 62, 0, Math.PI * 2); x.fill();
    x.fillStyle = '#1b2430'; x.beginPath(); x.arc(64, 64, 52, 0, Math.PI * 2); x.fill();
    x.fillStyle = '#ffd66b'; x.beginPath(); x.ellipse(64, 68, 26, 32, 0, 0, Math.PI * 2); x.fill();
    x.fillStyle = '#3e7b4f'; x.beginPath(); x.ellipse(74, 34, 9, 5, -0.5, 0, Math.PI * 2); x.fill();
    x.fillStyle = '#1b2430'; x.beginPath(); x.arc(55, 64, 4, 0, Math.PI * 2); x.arc(73, 64, 4, 0, Math.PI * 2); x.fill();
    const t = new T.CanvasTexture(c); t.colorSpace = T.SRGBColorSpace; return t;
  })();
  for (const [x, ry] of [[2.3, Math.PI / 2], [-2.31, -Math.PI / 2]]) {
    const e = new T.Mesh(new T.CircleGeometry(0.11, 32), new T.MeshStandardMaterial({ map: emblemTex, roughness: 0.3, metalness: 0.3 }));
    e.position.set(x, x > 0 ? 0.47 : 0.62, 0); e.rotation.y = ry; car.add(e);
  }
  // 전조등 빛(켜졌을 때만 보이는 반투명 원뿔)
  const beamMat = new T.MeshBasicMaterial({ color: 0xfff3c4, transparent: true, opacity: 0.16, depthWrite: false });
  const beams = [-0.6, 0.6].map((z) => {
    const g = new T.ConeGeometry(0.55, 2.2, 24, 1, true); g.rotateZ(Math.PI / 2); g.translate(-1.1, 0, 0);
    const b = new T.Mesh(g, beamMat); b.position.set(2.3 + 2.2, 0.62, z); b.rotation.z = 0.08; b.visible = false; car.add(b); return b;
  });
  // 와이퍼(앞 유리 아래, 켜면 유리를 따라 세운 모습)
  const wipers = [-0.05, 0.6].map((z) => {
    const pivot = new T.Group(); pivot.position.set(0.93, 0.99, z); pivot.rotation.z = 0.57; car.add(pivot);
    const blade = new T.Mesh(new T.BoxGeometry(0.03, 0.03, 0.62), M.wiper); blade.position.set(0, 0.02, -0.3); pivot.add(blade);
    return pivot;
  });
  // 바퀴: 앞바퀴는 핸들에 따라 돈다, 주차 브레이크면 뒷바퀴 휠이 빨강
  const tireGeo = new T.CylinderGeometry(R, R, 0.26, 32); tireGeo.rotateX(Math.PI / 2);
  const rimGeo = new T.CylinderGeometry(0.22, 0.22, 0.27, 20); rimGeo.rotateX(Math.PI / 2);
  const hubGeo = new T.CylinderGeometry(0.06, 0.06, 0.29, 12); hubGeo.rotateX(Math.PI / 2);
  const wheel = (x, z, front) => {
    const g = new T.Group(); g.position.set(x, R, z); car.add(g);
    g.add(new T.Mesh(tireGeo, M.tire));
    const rim = new T.Mesh(rimGeo, M.rim); g.add(rim);
    g.add(new T.Mesh(hubGeo, M.dark));
    return { g, rim, front };
  };
  const wheels = [wheel(WX, -0.8, true), wheel(WX, 0.8, true), wheel(-WX, -0.8, false), wheel(-WX, 0.8, false)];

  // 바닥: 부드러운 그림자 + 주차 칸 선
  const tex = (() => {
    const c = document.createElement('canvas'); c.width = 512; c.height = 512; const x = c.getContext('2d');
    const g = x.createRadialGradient(256, 256, 30, 256, 256, 250);
    g.addColorStop(0, 'rgba(0,0,0,.55)'); g.addColorStop(0.45, 'rgba(0,0,0,.22)'); g.addColorStop(1, 'rgba(0,0,0,0)');
    x.fillStyle = g; x.fillRect(0, 0, 512, 512);
    x.strokeStyle = 'rgba(255,255,255,.55)'; x.lineWidth = 7;
    x.beginPath(); x.moveTo(40, 256 - 118); x.lineTo(472, 256 - 118); x.moveTo(40, 256 + 118); x.lineTo(472, 256 + 118); x.stroke();
    const t = new T.CanvasTexture(c); t.colorSpace = T.SRGBColorSpace; return t;
  })();
  const ground = new T.Mesh(new T.PlaneGeometry(6.4, 6.4), new T.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }));
  ground.rotation.x = -Math.PI / 2; ground.position.y = 0.005; scene.add(ground);
  // 지붕 위 'P' 표시(기어 P일 때 파랑)
  const pTex = (on) => {
    const c = document.createElement('canvas'); c.width = 128; c.height = 128; const x = c.getContext('2d');
    x.fillStyle = on ? '#2f80ed' : 'rgba(42,53,68,.9)'; x.beginPath(); x.roundRect(8, 8, 112, 112, 26); x.fill();
    x.fillStyle = on ? '#ffffff' : '#93a1b3'; x.font = 'bold 84px system-ui, sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('P', 64, 70);
    const t = new T.CanvasTexture(c); t.colorSpace = T.SRGBColorSpace; return t;
  };
  const pOn = pTex(true), pOff = pTex(false);
  const pSprite = new T.Sprite(new T.SpriteMaterial({ map: pOff, depthTest: false })); pSprite.scale.set(0.42, 0.42, 1); pSprite.position.set(-0.35, 1.9, 0); scene.add(pSprite);

  let view = VIEWS.gate, az = view.az, w = 0, h = 0;
  const placeCamera = () => {
    const { el, d, ty, tx } = view;
    camera.position.set(tx + d * Math.cos(el) * Math.cos(az), ty + d * Math.sin(el), d * Math.cos(el) * Math.sin(az));
    camera.lookAt(tx, ty, 0);
  };
  const api = {
    canvas,
    setView(name) { view = VIEWS[name] || VIEWS.gate; az = view.az; M.glass.opacity = view.glass; pSprite.visible = name === 'gate'; placeCamera(); },
    rotate(dAz) { az += dAz; placeCamera(); api.render(); },
    resize(el) {
      const r = el.getBoundingClientRect(); const nw = Math.max(1, Math.round(r.width)), nh = Math.max(1, Math.round(r.height));
      if (nw === w && nh === h) return; w = nw; h = nh;
      renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); api.render();
    },
    apply(st) {
      for (const l of heads) l.material = st.lowBeam ? M.headOn : M.head;
      for (const b of beams) b.visible = st.lowBeam;
      for (const l of tails) l.material = st.brake ? M.tailOn : M.tail;
      for (const side of ['LEFT', 'RIGHT']) for (const l of ambers[side]) l.material = st.turn === side ? M.amberOn : M.amber;
      const a = -wheelAngleDeg(st.steeringDeg) * Math.PI / 180;
      for (const wh of wheels) { if (wh.front) wh.g.rotation.y = a; else wh.rim.material = st.parkingBrake ? M.rimPb : M.rim; }
      wipers.forEach((p, i) => { p.rotation.x = st.wiper ? (i ? 1.1 : 1.0) : 0; });
      belt.material = st.belt ? M.beltOn : M.beltOff; belt.visible = st.belt !== null;
      pSprite.material.map = st.gearP ? pOn : pOff; pSprite.material.needsUpdate = true;
    },
    render() { if (w && h) renderer.render(scene, camera); },
  };
  api.setView('gate');
  return api;
}

let ro = null;
/** 화면에 붙인다: 끌기·키 돌리기, 그리고 WebGL이 되면 CSS 모형 대신 3D로 바꾼다 */
export function bind(root) {
  const el = root.querySelector('.c3'); if (!el) return;
  const view = el.dataset.view || 'gate';
  const car = el.querySelector('.c3-car');
  const applyCss = () => car?.style.setProperty('--ry', `${cssRy}deg`);
  applyCss();
  const turn = (dx) => { if (engine && el.classList.contains('gl')) engine.rotate(dx * 0.012); else { cssRy += dx * 0.6; applyCss(); } };
  let x0 = null;
  el.addEventListener('pointerdown', (e) => { x0 = e.clientX; el.setPointerCapture?.(e.pointerId); });
  el.addEventListener('pointermove', (e) => { if (x0 == null) return; turn(e.clientX - x0); x0 = e.clientX; });
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((t) => el.addEventListener(t, () => { x0 = null; }));
  el.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    e.preventDefault(); e.stopPropagation(); turn(e.key === 'ArrowLeft' ? -25 : 25);
  });
  ensureEngine().then((eng) => {
    if (!eng || !el.isConnected) return;
    el.classList.add('gl');
    el.closest('.m-ill')?.classList.add('has3d');
    el.appendChild(eng.canvas);
    eng.setView(view);
    if (last) eng.apply(last);
    ro?.disconnect();
    ro = new ResizeObserver(() => { if (eng.canvas.isConnected) eng.resize(el); });
    ro.observe(el);
    eng.resize(el); eng.render();
  });
}

let lastKey = '';
/** 차량 신호를 모형에 반영(바뀐 것이 있을 때만 다시 그린다) */
export function show(root, st) {
  const el = root.querySelector('.c3'); if (!el) return;
  el.dataset.pb = st.parkingBrake ? 'on' : 'off';
  el.dataset.p = st.gearP ? 'on' : 'off';
  const label = describe(st);
  if (el.getAttribute('aria-label') !== label) el.setAttribute('aria-label', label);
  const key = JSON.stringify(st);
  last = st;
  if (key === lastKey && el.dataset.drawn === '1') return;
  lastKey = key;
  if (engine && engine.canvas.parentElement === el) { engine.apply(st); engine.render(); el.dataset.drawn = '1'; }
}
