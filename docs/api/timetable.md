# 시간표 API

## 범위

학기당 하나의 시간표이며 `timetable` 한 행은 학생이 선택한 개설 강의 한 건입니다.
과목은 기존 `course`를 참조합니다. 교과과정은 과목 인정 기준이고,
`lecture`는 실제 개설 정보이므로 공개 교과과정 목록에서 개설 여부를 추론하지 않습니다.

- `professor`: 선택적 학과 소속, 이름, 연구실. 동명이인을 허용합니다.
- `lecture`: 과목, 교수, `academic_year`, `term`, 분반.
- `lecture_schedule`: 월요일 1부터 일요일 7, 분 단위 시작/종료 시간, 강의실.
- `timetable`: 학생과 강의 연결. 삭제는 연결의 hard delete이며 강의는 보존합니다.

시간은 학교 현지 벽시계 시간이며 날짜/UTC 시각이 아닙니다. 자정을 넘는 수업은
이번 범위에서 지원하지 않습니다. 커스텀 개인 시간, 알림, 복수 시간표와
관리자 강의 수정/삭제 화면은 후속 작업입니다.

## 엔드포인트

모든 API는 Supabase JWT를 요구합니다. 관리자 쓰기는 활성 관리자 프로필을 확인합니다.
학생 시간표 소유자는 JWT subject로 결정하며 요청에서 학생 ID를 받지 않습니다.

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| GET | /api/v1/lectures?academicYear=2026&term=SECOND | 학기 개설 강의 |
| GET | /api/v1/timetable?academicYear=2026&term=SECOND | 내 시간표 |
| POST | /api/v1/timetable | 강의 추가 |
| PUT | /api/v1/timetable/{entityId} | 같은 학기의 강의로 교체 |
| DELETE | /api/v1/timetable/{entityId} | 본인의 연결 삭제 |
| GET/POST | /api/v1/admin/professors | 교수 조회/등록 |
| POST | /api/v1/admin/lectures | 개설 강의와 수업 시간 등록 |
| POST | /api/v1/admin/lectures/import | 같은 학기 1~200강의 전체 등록 또는 전체 취소 |

학생 등록·변경 본문:

```json
{"lectureEntityId":"00000000-0000-0000-0000-000000000901"}
```

관리자 강의 등록 본문 (과목과 교수 UUID는 실제 등록된 값 사용):

```json
{
  "courseEntityId":"00000000-0000-0000-0000-000000000101",
  "professorEntityId":"00000000-0000-0000-0000-000000000801",
  "academicYear":2026,
  "term":"SECOND",
  "section":"001",
  "schedules":[
    {"dayOfWeek":1,"startTime":"09:30","endTime":"11:00","classroom":"101호"},
    {"dayOfWeek":3,"startTime":"13:00","endTime":"14:30","classroom":"102호"}
  ]
}
```

## 규칙과 실패

- 학기는 FIRST, SUMMER, SECOND, WINTER입니다.
- 시작 시간은 종료 시간보다 빨라야 하며 같은 강의 내부 시간도 중복할 수 없습니다.
- 같은 과목·연도·학기·분반은 유일합니다.
- 같은 학생의 등록/변경/삭제를 학생 행 잠금으로 직렬화합니다.
- 같은 학기·요일의 시간 구간이 겹치면 등록/변경 전체를 거부합니다.
  09:00~10:00과 10:00~11:00은 허용합니다.
- 400 INVALID_REQUEST: 잘못된 연도, 학기, 시간, 필수값.
- 401: 인증 실패. 403 ADMIN_ACCESS_DENIED: 관리자 자격 없음.
- 404 STUDENT_NOT_FOUND, COURSE_NOT_FOUND, DEPARTMENT_NOT_FOUND,
  LECTURE_NOT_FOUND, TIMETABLE_NOT_FOUND: 참조 또는 본인 데이터 없음.
- 409 TIMETABLE_CONFLICT: 중복 분반, 중복 등록, 수업 시간 충돌.

## 적용과 확인

관리자 엑셀 등록은 브라우저에서 템플릿을 읽고 검수한 강의를
`{"lectures":[강의 등록 본문, ...]}` 형태로 import API에 전달합니다.
미등록 과목/교수와 동명이인, 파일 내부 중복 수업 시간, 기존 분반 중복은
검수에서 표시합니다. 서버는 참조·권한·입력·분반 중복을 다시 검사하며
한 건이라도 실패하면 앞서 저장한 강의까지 모두 롤백합니다. 한 요청에
다른 연도/학기를 섞을 수 없습니다. 엑셀 원본은 서버에 저장하지 않습니다.

V23/V24는 기존 데이터를 수정하지 않는 추가 마이그레이션입니다.
테스트 DB에서 검증 후 운영 백엔드 재시작에 의해 Flyway가 적용됩니다.
실제 학교 강의 데이터나 샘플은 자동으로 운영 DB에 넣지 않습니다.

관리자 JWT로 Swagger에서 교수 등록 → 기존 과목과 교수에 연결된 강의 등록을
진행합니다. 이후 학생 앱의 시간표 탭에서 같은 연도/학기를 선택하고
강의 추가 → 겹치는 강의 거절 → 분반 변경 → 삭제를 확인합니다.
