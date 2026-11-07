# 학생 계정 재연결 API

같은 학교 이메일의 Supabase 계정이 다시 만들어지면 새 계정 ID로는 기존 학생 프로필을 찾을 수 없습니다. 이때 프로필 등록(`POST /api/v1/students/me`)은 `409 STUDENT_EMAIL_ALREADY_REGISTERED`로 막힙니다. 학교 이메일은 재발급될 수 있으므로 이메일만으로 자동 재연결하지 않습니다. 대신 학생이 요청하고 관리자가 본인임을 확인한 뒤 승인합니다.

## 흐름

1. 학생: `POST /api/v1/students/me/relink-requests` → `202`, `PENDING`
   - 새 계정 ID(`sub`)와 이메일(`email` 클레임)은 JWT에서 읽고 요청 본문은 없습니다.
   - 같은 계정의 대기 요청이 있으면 그 요청을 그대로 돌려줍니다.
   - 오류: 이 계정에 이미 프로필이 있으면 `409 STUDENT_ALREADY_REGISTERED`, 같은 이메일의 삭제되지 않은 프로필이 없으면 `404 STUDENT_NOT_FOUND`.
2. 관리자: `GET /api/v1/admin/student-relink-requests`
   - 처리된 요청을 포함해 최신순으로 반환합니다. 각 요청에 기존 프로필(닉네임·전공·입학년도·재학 상태)과 프로필 등록 시각이 들어 있습니다.
3. 관리자가 앱 밖에서 본인임을 확인합니다. 예: 학생에게 기존 프로필의 닉네임·전공·입학년도와 등록한 성적 일부를 묻습니다. 이메일을 받을 수 있다는 것만으로는 확인으로 보지 않습니다.
4. 관리자: `POST /api/v1/admin/student-relink-requests/{requestEntityId}/approve` 또는 `/reject`
   - 승인하면 기존 프로필의 `auth_user_id`를 새 계정으로 바꿉니다. 성적·시간표·문의는 프로필에 붙어 있어 그대로 이어집니다.
   - 이전 계정 ID, 처리 관리자, 처리 시각을 요청에 기록합니다. 같은 프로필의 다른 대기 요청은 함께 거절합니다.
   - 오류: 요청이 없으면 `404 STUDENT_RELINK_REQUEST_NOT_FOUND`. 다음 경우는 `409 STUDENT_RELINK_CONFLICT`입니다.
     - 이미 처리된 요청
     - 새 계정에 이미 다른 프로필이 연결됨
     - 요청 이후 기존 프로필이 삭제되었거나 이메일이 바뀜

관리자 API는 활성 관리자만 사용할 수 있고, 그 밖의 사용자는 `403 ADMIN_ACCESS_DENIED`를 받습니다. 승인한 뒤에는 이전 계정으로 로그인해도 프로필이 보이지 않습니다.
