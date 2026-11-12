// node --test web/test — B04 정차 중 도움말·운전 노트, C05 소개(11/12 회의)
import test from 'node:test';
import assert from 'node:assert/strict';
import { HELP, WIKI, helpHtml, wikiHtml, wikiIndex } from '../learn.js';
import { aboutHtml, STATUS } from '../about.js';
import * as C from '../core.js';

test('help_covers_every_warmup_mission', () => {
  for (const id of Object.keys(C.MISSION_META)) assert.ok(HELP[id], `${id} 도움말 없음`);
  for (const h of Object.values(HELP)) { assert.ok(h.o && h.x, 'O/X 둘 다'); assert.ok(WIKI[wikiIndex(h.wiki)].key === h.wiki, `노트 ${h.wiki} 없음`); }
});

test('belt_help_never_asks_to_unbuckle', () => {
  // S08: 이미 맨 벨트를 풀었다 다시 매라고 하지 않는다
  const t = helpHtml('SEAT_BELT', '안전벨트 매기');
  assert.ok(/그대로 두세요/.test(t));
  assert.ok(!/풀었다 다시 매세요|풀고 다시/.test(t));
});

test('wiki_pages_render_and_link_both_ways', () => {
  WIKI.forEach((_, i) => { const h = wikiHtml(i); assert.ok(h.includes(WIKI[i].title)); });
  assert.ok(!wikiHtml(0).includes(`data-page="-1"`));
  assert.ok(!wikiHtml(WIKI.length - 1).includes(`data-page="${WIKI.length}"`));
});

test('about_separates_mock_from_verified', () => {
  const h = aboutHtml();
  assert.ok(h.includes('모의'));
  // 실제 차·에뮬레이터 열에는 '확인 전' 또는 '해당 없음'만 — 확인 안 한 것을 됐다고 쓰지 않는다
  for (const r of STATUS) assert.ok(/확인 전|해당 없음/.test(r[3]), r[0]);
});
