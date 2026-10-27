# API Authoring Guide

학생 시간표와 관리자 개설 강의 등록은 [시간표 API](timetable.md)를 참고하세요.

학기별 수강 과목 추천 규칙은 [수강 추천 API](course-recommendation.md)를 참고하세요.

The backend OpenAPI document is the single API contract for the mobile and administrator clients. Clients consume a pinned contract version and must not access Supabase PostgreSQL directly.

The read-only [grade simulation API](grade-simulation.md) compares current and projected GPA without persisting expected grades.

[연도별 교과과정 가이드](../development/yearly-curriculum.md)는 관리자 초안·공개 API와 학생 연도별 조회, 성적 저장 검증 및 단계별 배포를 설명합니다. 신규 조회는 추가 계약이지만 성적 등록 검증은 동작 변경이므로 모바일·관리자와 함께 배포를 준비해야 합니다.

공통전공 지원은 JSON 필드 변경 없이 동작을 확장합니다. 관리자 과목 생성은 `MAJOR`도 `departmentEntityId=null`을 허용하고, 교과과정은 다른 관리 학과의 전공과목을 연결할 수 있습니다. 과목 분류 일치·중복 방지·관리자 권한·version 검증은 유지합니다. 졸업 전공학점은 관리 소속이 아닌 공개된 학과·수강연도 연결을 사용하므로 누락/초안 연결은 전공학점에 포함되지 않습니다. 기존 총학점·성적 ID·스냅샷·GPA는 보존합니다. V20을 먼저 적용하고 새 백엔드·어드민을 함께 반영한 뒤 공식 자료를 검토·공개해 주세요.

## API completion checklist

`GET /api/v1/admin/me` returns the authenticated user's active administrator profile. A valid Supabase session without an active `admin` mapping receives `403 ADMIN_ACCESS_DENIED`. `GET /api/v1/admin/admins` lists active administrators by name for selecting an inquiry assignee with `PUT /api/v1/admin/inquiries/{inquiryEntityId}/assignee`.

An API change is incomplete unless the same change includes all of the following:

1. Endpoint implementation.
2. Relevant unit and integration tests.
3. OpenAPI request, response, and example updates.
4. Authentication and authorization requirements.
5. Stable error codes and failure examples.

Document successful and failure responses in OpenAPI, including the shared error shape in [`../reliability/errors.md`](../reliability/errors.md). Pull requests state whether the API change is backward compatible and which clients are affected.

## Contract workflow

1. Implement the endpoint and its authorization boundary.
2. Add the relevant unit and integration tests.
3. Update the OpenAPI request, response, and examples, including authentication requirements and stable failure codes.
4. Run `./gradlew integrationTest --tests '*OpenApiContractTest'`; if the contract intentionally changed, review `build/openapi/openapi.json` and update `docs/api/openapi.json`, then rerun the test.
5. Run `./scripts/verify` before requesting review.

`config.OpenApiContractTest` compares the generated runtime contract with the checked-in JSON semantically and fails on drift. Controllers use DTOs rather than exposing JPA entities.
