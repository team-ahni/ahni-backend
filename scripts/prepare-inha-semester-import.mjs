import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const days = '월화수목금토일'
const quote = value => value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`
const time = minute => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`

export function parseSchedule(raw) {
  const schedules = []
  for (const part of raw.split(/\s*\/\s*/)) {
    if (/^셀\d+\(웹강의\)$/.test(part)) continue
    const opening = part.indexOf('(')
    assert.ok(opening < 0 || part.endsWith(')'), `Trailing classroom text: ${raw}`)
    const clock = opening < 0 ? part : part.slice(0, opening)
    const classroom = opening < 0 ? null : part.slice(opening + 1, part.endsWith(')') ? -1 : undefined)
    const groups = [...clock.matchAll(/([월화수목금토일])((?:\d+,?)+)/g)]
    assert.equal(groups.map(group => group[0]).join(''), clock, `Unparsed time: ${raw}`)
    assert.ok(groups.length, `Missing day: ${raw}`)
    for (const group of groups) {
      const periods = group[2].replace(/,$/, '').split(',').map(Number)
      assert.ok(periods.every(p => Number.isInteger(p) && p >= 1 && p <= 26), `Invalid period: ${raw}`)
      assert.equal(new Set(periods).size, periods.length, `Duplicate period: ${raw}`)
      periods.sort((a, b) => a - b)
      for (let start = 0; start < periods.length;) {
        let end = start
        while (periods[end + 1] === periods[end] + 1) end++
        schedules.push({ dayOfWeek: days.indexOf(group[1]) + 1, startTime: time(540 + (periods[start] - 1) * 30), endTime: time(540 + periods[end] * 30), classroom })
        start = end + 1
      }
    }
  }
  const unique = [...new Map(schedules.map(s => [JSON.stringify(s), s])).values()]
  unique.sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime))
  for (let i = 1; i < unique.length; i++) {
    const a = unique[i - 1], b = unique[i]
    assert.ok(a.dayOfWeek !== b.dayOfWeek || a.endTime <= b.startTime, `Overlapping source times: ${raw}`)
  }
  return unique
}

export function prepare(source) {
  assert.equal(source.year, 2026)
  assert.equal(source.semester, 2)
  const courses = new Map(), offerings = new Map()
  for (const row of source.courses) {
    assert.equal(row[0], source.year)
    assert.equal(row[1], source.semester)
    assert.ok(['전공필수', '전공선택', '전공기초', '교양필수', '교양선택'].includes(row[8]), `Unknown course division: ${row[8]}`)
    const [code, section, name, credit, category, professorName] = [row[3], row[4], row[5], row[7], row[8].startsWith('전공') ? 'MAJOR' : 'GENERAL_EDUCATION', row[10].trim()]
    assert.match(code, /^[A-Z0-9]{1,30}$/)
    assert.match(section, /^\d{3}$/)
    assert.equal(row[2], `${code}-${section}`)
    assert.ok(name && name.length <= 200)
    assert.ok(Number.isFinite(credit) && credit >= 0 && credit <= 30 && Number.isInteger(credit * 10))
    assert.ok(professorName && professorName.length <= 500)
    const department = category === 'MAJOR' && !row[14].includes(',') ? row[14] : null
    const course = { code, name, credit, category, department }
    if (courses.has(code)) {
      const previous = courses.get(code)
      assert.deepEqual({ ...previous, department: null }, { ...course, department: null }, `Conflicting course: ${code}`)
      if (previous.department !== course.department) course.department = null
    }
    courses.set(code, course)
    assert.ok(!offerings.has(row[2]), `Duplicate offering: ${row[2]}`)
    offerings.set(row[2], { code, section, professorName, rawTime: row[9], schedules: parseSchedule(row[9]) })
  }
  assert.equal(courses.size, 232)
  assert.equal(offerings.size, 312)
  return { courses: [...courses.values()], offerings: [...offerings.values()] }
}

export function renderSql(plan, checksum) {
  const courseValues = plan.courses.map(c => `(${[c.code, c.name, c.credit, c.category, c.department].map(quote).join(',')})`)
  const lectureValues = plan.offerings.map(l => `(${[l.code, l.section, l.professorName].map(quote).join(',')})`)
  const scheduleValues = plan.offerings.flatMap(l => l.schedules.map(s => `(${[l.code, l.section, s.dayOfWeek, s.startTime, s.endTime, s.classroom].map(quote).join(',')})`))
  return `-- Inha 2026 SECOND snapshot SHA-256: ${checksum}
-- Run inside one transaction after Flyway V26. Existing catalog records are never updated.
LOCK TABLE public.course, public.lecture IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE _inha_courses(code text PRIMARY KEY, name text, credit numeric, category text, department text) ON COMMIT DROP;
INSERT INTO _inha_courses VALUES ${courseValues.join(',\n')};
CREATE TEMP TABLE _inha_offerings(code text, section text, professor_name text, PRIMARY KEY(code,section)) ON COMMIT DROP;
INSERT INTO _inha_offerings VALUES ${lectureValues.join(',\n')};
CREATE TEMP TABLE _inha_schedules(code text, section text, day_of_week integer, start_time time, end_time time, classroom text) ON COMMIT DROP;
INSERT INTO _inha_schedules VALUES ${scheduleValues.join(',\n')};
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM _inha_courses i JOIN public.course c USING(code)
    WHERE c.name IS DISTINCT FROM i.name OR c.credit IS DISTINCT FROM i.credit OR c.category IS DISTINCT FROM i.category OR NOT c.is_active)
  THEN RAISE EXCEPTION 'Existing course metadata differs; abort instead of overwriting'; END IF;
  IF EXISTS (SELECT 1 FROM _inha_courses i LEFT JOIN public.course c USING(code)
    WHERE c.id IS NULL AND i.department IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.department d WHERE d.name=i.department AND d.deleted_at IS NULL))
  THEN RAISE EXCEPTION 'Missing source department'; END IF;
END $$;
INSERT INTO public.course(code,name,credit,category,department_id)
SELECT i.code,i.name,i.credit,i.category,d.id FROM _inha_courses i
LEFT JOIN public.department d ON d.name=i.department AND d.deleted_at IS NULL
WHERE NOT EXISTS(SELECT 1 FROM public.course c WHERE c.code=i.code);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM _inha_offerings i JOIN public.course c USING(code)
    JOIN public.lecture l ON l.course_id=c.id AND l.academic_year=2026 AND l.term='SECOND' AND l.section=i.section
    WHERE l.professor_name IS DISTINCT FROM i.professor_name)
  THEN RAISE EXCEPTION 'Existing lecture instructor differs'; END IF;
END $$;
CREATE TEMP TABLE _inha_new_lectures ON COMMIT DROP AS
WITH inserted AS (
  INSERT INTO public.lecture(course_id,professor_name,academic_year,term,section)
  SELECT c.id,i.professor_name,2026,'SECOND',i.section FROM _inha_offerings i JOIN public.course c USING(code)
  WHERE NOT EXISTS(SELECT 1 FROM public.lecture l WHERE l.course_id=c.id AND l.academic_year=2026 AND l.term='SECOND' AND l.section=i.section)
  RETURNING id,course_id,section
) SELECT * FROM inserted;
INSERT INTO public.lecture_schedule(lecture_id,day_of_week,start_time,end_time,classroom)
SELECT l.id,s.day_of_week,s.start_time,s.end_time,s.classroom FROM _inha_schedules s
JOIN public.course c USING(code) JOIN _inha_new_lectures l ON l.course_id=c.id AND l.section=s.section;
DO $$ BEGIN
  IF (SELECT count(*) FROM _inha_offerings i JOIN public.course c USING(code) JOIN public.lecture l
    ON l.course_id=c.id AND l.academic_year=2026 AND l.term='SECOND' AND l.section=i.section) <> ${plan.offerings.length}
  THEN RAISE EXCEPTION 'Offering coverage mismatch'; END IF;
  IF EXISTS (
    (SELECT s.code,s.section,s.day_of_week,s.start_time,s.end_time,s.classroom FROM _inha_schedules s
     EXCEPT ALL
     SELECT i.code,i.section,s.day_of_week,s.start_time,s.end_time,s.classroom FROM _inha_offerings i JOIN public.course c USING(code)
     JOIN public.lecture l ON l.course_id=c.id AND l.academic_year=2026 AND l.term='SECOND' AND l.section=i.section
     JOIN public.lecture_schedule s ON s.lecture_id=l.id)
    UNION ALL
    (SELECT i.code,i.section,s.day_of_week,s.start_time,s.end_time,s.classroom FROM _inha_offerings i JOIN public.course c USING(code)
     JOIN public.lecture l ON l.course_id=c.id AND l.academic_year=2026 AND l.term='SECOND' AND l.section=i.section
     JOIN public.lecture_schedule s ON s.lecture_id=l.id
     EXCEPT ALL
     SELECT s.code,s.section,s.day_of_week,s.start_time,s.end_time,s.classroom FROM _inha_schedules s)
  ) THEN RAISE EXCEPTION 'Schedule coverage mismatch; no existing lecture schedules will be overwritten'; END IF;
END $$;
`
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const [sourcePath, output] = process.argv.slice(2)
  assert.ok(sourcePath && output, 'Usage: node scripts/prepare-inha-semester-import.mjs SOURCE.json OUTPUT_DIRECTORY')
  const bytes = fs.readFileSync(sourcePath)
  const checksum = createHash('sha256').update(bytes).digest('hex')
  const plan = prepare(JSON.parse(bytes))
  fs.mkdirSync(output, { recursive: true })
  fs.writeFileSync(path.join(output, 'import.sql'), renderSql(plan, checksum))
  fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify({ checksum, sourcePath, ...plan }, null, 2))
  console.log(JSON.stringify({ courses: plan.courses.length, lectures: plan.offerings.length, schedules: plan.offerings.reduce((n, l) => n + l.schedules.length, 0), withoutFixedTime: plan.offerings.filter(l => !l.schedules.length).length, checksum }))
}
