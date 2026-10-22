import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

export const states = ['other_app', 'locked_on', 'locked_off'];
const routes = ['VOICE_COMMAND', 'ASSIST', 'KEY_MAPPER'];
const median = v => { const a = [...v].sort((a,b) => a-b); return (a[Math.floor((a.length-1)/2)] + a[Math.floor(a.length/2)])/2; };
const p95 = v => [...v].sort((a,b) => a-b)[Math.ceil(v.length*.95)-1];
const number = value => value !== '' && value != null && Number.isFinite(Number(value)) && Number(value) >= 0;

export function summarize(events, print = console.log) {
  const attempts = new Map(), cells = new Map();
  for (const e of events) { if (!attempts.has(e.id)) attempts.set(e.id, []); attempts.get(e.id).push(e); }
  for (const rows of attempts.values()) {
    const armed = rows.find(e => e.event === 'armed');
    if (!armed) continue;
    const by = Object.fromEntries(rows.map(e => [e.event, e]));
    const d = by.dispatch_started ?? {}, o = by.observation ?? {};
    let ok = o.outcome === 'success' && Boolean(o.notes?.trim()) && Boolean(by.dispatched)
      && !by.error && !by.dispatch_error && d.observed_state === armed.requested_state
      && number(o.ready_ms) && number(o.uncertainty_ms);
    if (armed.mic) {
      const start = by.release_started?.elapsed_ns, end = by.capture_released_worker_terminated?.elapsed_ns;
      ok = ok && Boolean(by.capture_started) && start >= 0 && end >= start && end <= d.elapsed_ns && end-start < 2e9;
    }
    const key = JSON.stringify([armed.route, armed.requested_state, armed.mic]);
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push({time: armed.elapsed_ns, ok, ready: Number(o.ready_ms), uncertainty: Number(o.uncertainty_ms)});
  }
  const qualified = [];
  for (const route of routes) {
    let passes = true;
    const routeTimes = [];
    for (const state of states) for (const mic of [false, true]) {
      const rows = (cells.get(JSON.stringify([route,state,mic])) ?? []).sort((a,b) => a.time-b.time);
      let streak = 0;
      for (const row of rows) streak = row.ok ? streak+1 : 0;
      passes = passes && streak >= 10;
      const valid = rows.filter(r => r.ok), times = valid.map(r => r.ready);
      routeTimes.push(...times);
      const timing = times.length ? `median=${median(times)}ms p95=${p95(times)}ms max_uncertainty=${Math.max(...valid.map(r=>r.uncertainty))}ms` : 'no valid timings';
      print(`${route}/${state}/mic=${mic}: attempts=${rows.length} failures_or_pending=${rows.length-valid.length} trailing_successes=${streak}; ${timing}`);
    }
    if (passes) qualified.push({route,p95:p95(routeTimes)});
  }
  const direct = qualified.filter(q => q.route !== 'KEY_MAPPER');
  const selected = (direct.length ? direct : qualified).sort((a,b)=>a.p95-b.p95)[0] ?? null;
  print(selected ? `GATE: QUALIFIES ${selected.route} (human evidence must be reviewed)` : 'GATE: PENDING/NOT PASSED — listener delivery blocked');
  return selected;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!process.argv[2]) throw Error('Usage: node tools/gate-report.mjs attempts.jsonl [baseline.csv]');
  summarize(fs.readFileSync(process.argv[2], 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(l=>l.trim()).map(l=>JSON.parse(l)));
  if (process.argv[3]) {
    // Baseline file uses simple numeric fields and recording filenames without commas.
    const rows = fs.readFileSync(process.argv[3], 'utf8').trim().split(/\r?\n/).slice(1).map(l=>l.split(','));
    for (const state of states) {
      const matching = rows.filter(r=>r[0]===state);
      if (matching.some(r=>!number(r[1]) || !number(r[2]) || !r[3])) throw Error('Baseline requires valid timings, uncertainty and recording filename');
      const times = matching.map(r=>Number(r[1]));
      if (times.length) console.log(`Physical-button baseline/${state}: n=${times.length} median=${median(times)}ms p95=${p95(times)}ms max_uncertainty=${Math.max(...matching.map(r=>Number(r[2])))}ms`);
    }
  }
}
