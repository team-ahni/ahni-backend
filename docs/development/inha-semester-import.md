# Inha 2026 Second Semester Import

This is an explicit operational import, not a Flyway data seed. Running the
backend or its tests does not import school data into a live database.

## Source And Mapping

- Source: https://sugang.inha.ac.kr/sugang/SU_51001/Lec_Time_Search.htm
- Snapshot collected: 2026-10-13; target: 2026, `SECOND`.
- Scope: five Future Convergence College department searches plus all general
  electives. Preserve department-specific general-required sections too.
- Snapshot SHA-256: `6c276db8d3fd165808f1ab8e3fa822d19548b592327f662bbb669ec23c4cb0e1`.
- 232 course codes, 312 offerings, 321 fixed-time meetings.
- Course identity is the course code, never the name. Existing courses are not
  updated. Missing major courses use their unambiguous source department;
  general education and shared majors may have no catalog department.
- Preserve the instructor text, including joint instructors, without identity
  matching or creating professor records.
- Time reference: https://www.inu.ac.kr/bbs/inu/247/376268/download
  (Inha's 2026 exchange guide). Period 1 starts at 09:00 and periods are 30
  minutes. The source's late periods 25 and 26 continue that sequence to 22:00.
- 58 entirely web-based offerings have no fixed schedules. Two blended
  offerings retain their in-person schedules. Web slot numbers are not days or
  clock times. Preserve the original text in the manifest.
- Keep classroom text as collected, even when the school's nested parentheses
  are malformed; do not infer building or room identifiers.

## Preparation And Safety

```bash
node --test scripts/prepare-inha-semester-import.test.mjs
node scripts/prepare-inha-semester-import.mjs /absolute/path/to/inha_2026_2_courses.json /absolute/path/to/output
```

The generator produces `import.sql` and a source-linked `manifest.json`. It
rejects unknown time syntax, invalid periods, overlapping meetings, duplicate
offerings and conflicting course metadata. The SQL requires Flyway V26 and
one transaction. It checks existing metadata and exact offering schedules,
adds only missing courses/offerings, and aborts instead of replacing conflicts.
Reapplying it does not duplicate meetings. Use a transaction-level advisory
lock and a short lock timeout when executing operationally.

Before live execution, obtain explicit approval, save a permission-restricted
database backup outside Git, restore it into an isolated database, and test
Flyway and the import twice. Compare every instructor and meeting against the
manifest and preserve all existing catalog rows. Never run Flyway `clean` or
rewrite migration history. The administrator HTTP import still requires at
least one schedule; asynchronous offerings use this explicit SQL import, not
fabricated schedules to bypass that validation.

## Applied Result

Applied on 2026-10-18 to the database configured by the backend `.env`, after
backup and restoration validation. Flyway advanced from V21 to V26. Added 10
courses (473 to 483), 312 lectures and 321 schedules. Existing 473 catalog rows
were unchanged. Student timetables and curriculum-course assignments were not
created or changed. All imported instructors and schedules were verified by a
read-only query after commit. Backups and result files are under the workspace
`outputs` directory and must not be committed.
