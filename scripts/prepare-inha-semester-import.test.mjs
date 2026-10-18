import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseSchedule } from './prepare-inha-semester-import.mjs'

test('maps late evening periods without truncation', () => {
  assert.deepEqual(parseSchedule('수21,22,23,24,25,26(M-206)'), [
    {dayOfWeek:3,startTime:'19:00',endTime:'22:00',classroom:'M-206'},
  ])
})
test('applies shared classrooms to both days', () => {
  const result = parseSchedule('화21,22,23,토1,2,3(M-306)')
  assert.deepEqual(result.map(s=>[s.dayOfWeek,s.startTime,s.endTime,s.classroom]), [[2,'19:00','20:30','M-306'],[6,'09:00','10:30','M-306']])
})
test('preserves asynchronous and blended instruction', () => {
  assert.deepEqual(parseSchedule('셀13(웹강의)'), [])
  assert.deepEqual(parseSchedule('셀0(웹강의) /월13,14,15,16(60주년-511)'), [{dayOfWeek:1,startTime:'15:00',endTime:'17:00',classroom:'60주년-511'}])
})
test('splits gaps and preserves missing or nested classroom text', () => {
  assert.equal(parseSchedule('토1,2,4,5').length, 2)
  assert.equal(parseSchedule('토23,24')[0].classroom, null)
  assert.equal(parseSchedule('화4,5,6,7,8,9(5남125(5남)')[0].classroom, '5남125(5남')
})
test('rejects unknown, duplicate and invalid periods instead of losing source text', () => {
  for (const raw of ['월0', '화27', '수1,1', '월1(강의실) extra', '알수없음', '']) assert.throws(()=>parseSchedule(raw))
})
