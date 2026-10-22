import test from 'node:test';
import assert from 'node:assert/strict';
import {summarize, states} from './gate-report.mjs';
const gate = rows => summarize(rows, ()=>{});
function fixture() {
  const events = [];
  for (const state of states) for (const mic of [false,true]) for (let n=0;n<10;n++) {
    const id = `${state}-${mic}-${n}`;
    const rows = [{event:'armed',elapsed_ns:n*1e10,route:'ASSIST',requested_state:state,mic}];
    if (mic) rows.push({event:'capture_started'},{event:'release_started',elapsed_ns:1},{event:'capture_released_worker_terminated',elapsed_ns:2});
    rows.push({event:'dispatch_started',elapsed_ns:3,observed_state:state},{event:'dispatched'},
      {event:'observation',outcome:'success',notes:'recording.mp4; new phrase and reply',ready_ms:'100',uncertainty_ms:'20'});
    events.push(...rows.map(row=>({id,...row})));
  }
  return events;
}
test('empty evidence stays pending',()=>assert.equal(gate([]),null));
test('complete six-cell matrix qualifies',()=>assert.equal(gate(fixture()).route,'ASSIST'));
test('missing observation breaks streak',()=>{const rows=fixture();rows.pop();assert.equal(gate(rows),null);});
test('launch return alone is insufficient',()=>assert.equal(gate(fixture().filter(r=>r.event!=='observation')),null));
test('wrong device state blocks qualification',()=>{const rows=fixture();rows.find(r=>r.event==='dispatch_started').observed_state='wrong';assert.equal(gate(rows),null);});
test('handoff timeout blocks qualification',()=>{const rows=fixture();rows.find(r=>r.event==='capture_released_worker_terminated').elapsed_ns=2e9+1;assert.equal(gate(rows),null);});
test('missing timing cannot qualify',()=>{const rows=fixture();rows.find(r=>r.event==='observation').ready_ms='';assert.equal(gate(rows),null);});
test('release failure blocks qualification',()=>{const rows=fixture();rows.push({id:rows[0].id,event:'error'});assert.equal(gate(rows),null);});
