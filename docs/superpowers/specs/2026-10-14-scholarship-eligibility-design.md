# Scholarship Eligibility Design

**Status:** Approved implementation slice

**Date:** 2026-10-14

## Source Alignment

- `O2_mini_spec_v1.0.pdf` 2.6: list the scholarship types applicable to the
  student's admission year and department, compare each type's minimum GPA,
  completed credits, and warning-count criteria with the current record, report
  met/unmet per type, and state the shortfall (for example "GPA +0.3"). Missing
  criteria data is reported with guidance to contact an administrator.
- `O2_usecase_spec_v1.0.pdf` UC-S03: scholarship criteria differ by admission
  year and department; scholarship status is refreshed after grade changes.
- ERD `SCHOLARSHIP_CRITERIA`: `dept_id`, `name`, `entry_year`, `min_gpa`,
  `min_credits`, `max_warnings`.
- Inha University academic regulation: a regular semester GPA below 1.70 is an
  academic warning. Dismissal, counselling, and credit-limit consequences are
  outside this slice.

The specifications contain no administrator workflow for scholarship criteria.
`docs/product.md` assigns criteria management to administrators, so this slice
adds an administrator API that mirrors graduation-requirement management.

## Data Model

`scholarship_criteria` is policy data, not a calculated result:

- owning department and admission year (`entry_year` is stored as an integer
  `admission_year`, matching `graduation_requirement`);
- scholarship name, unique per department and admission year;
- `min_gpa` (0.00–4.50), `min_credits` (0.0–999.9), `max_warnings` (≥ 0);
- immutable external identity and audit timestamps.

Department and admission year are immutable after creation. Criteria have no
dependent rows, so administrator deletion is a hard delete.

## Calculation

Eligibility is derived on every request and never stored.

- Department: the student's active `PRIMARY` major. Double-major and minor
  departments are not evaluated.
- GPA and completed credits: the existing cumulative `GradeSummaryCalculator`
  result, including retake replacement and P/NP/RPL/F rules.
- Warning count: number of regular terms (`FIRST`, `SECOND`) whose term GPA is
  below 1.70. The term GPA uses the same grade rules but keeps attempts that a
  later retake replaced, because an issued warning is not withdrawn
  retroactively. Summer and winter terms and terms without GPA credits never
  produce a warning. The term GPA is rounded to two decimals before comparison.
- Shortfalls: `gpaShortfall = max(0, minGpa - gpa)`,
  `creditShortfall = max(0, minCredits - completedCredits)`,
  `excessWarnings = max(0, warningCount - maxWarnings)`. A type is met when all
  three are zero.

The 1.70 threshold is a backend constant while Inha is the only institution.

## API

| Method | Path | Auth |
| --- | --- | --- |
| GET | `/api/v1/scholarship-eligibility` | student JWT |
| GET | `/api/v1/admin/scholarship-criteria` | active administrator |
| POST | `/api/v1/admin/scholarship-criteria` | active administrator |
| PUT | `/api/v1/admin/scholarship-criteria/{criteriaEntityId}` | active administrator |
| DELETE | `/api/v1/admin/scholarship-criteria/{criteriaEntityId}` | active administrator |

The student response contains the current GPA, completed credits, warning
count, and one result per scholarship ordered by name. An empty `scholarships`
array means no criteria are registered; clients show the "기준 데이터 없음"
guidance instead of an error.

Stable errors: `STUDENT_NOT_FOUND`, `ADMIN_ACCESS_DENIED`,
`DEPARTMENT_NOT_FOUND`, `SCHOLARSHIP_CRITERIA_NOT_FOUND`,
`SCHOLARSHIP_CRITERIA_ALREADY_EXISTS`, `INVALID_REQUEST`.

Results are guidance, not an official scholarship decision.

## Out of Scope

- Previous-semester-only scholarship rules and per-scholarship term windows.
- Persisting warning history or importing official warning records.
- Double-major or minor department scholarships.
- Impact counts before criteria edits.
