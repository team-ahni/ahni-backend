# 공지 본문 서식과 사진

Tiptap JSON의 doc, paragraph, heading(H2/H3), bulletList, orderedList, listItem, text, hardBreak, image와 bold/italic만 받습니다. 서버가 허용 속성으로 다시 구성하고 평문 body를 생성합니다. 본문은 10,000자, 문서는 100,000자·깊이 16·노드 2,000개 이하입니다. 기존 body만 있는 공지도 읽고 수정할 수 있습니다. 구버전 클라이언트의 공개 상태 변경은 body가 동일하면 기존 JSON을 유지합니다.

관리자 POST/PUT 공지는 기존 application/json을 유지합니다. 사진이 추가되면 multipart/form-data의 notice(JSON), imageIds(JSON UUID 배열), images(같은 순서의 파일)를 사용합니다. JPEG/PNG만, 공지당 사진 최대 10장, 요청당 새 파일 합계 5MiB입니다. 파일명·MIME·시그니처를 검증합니다. image의 attrs는 imageId와 alt만 저장하고 URL은 버립니다. 다른 공지의 imageId를 연결할 수 없습니다.

사진 저장 실패·DB 충돌·커밋 실패 시 트랜잭션을 되돌리고 새 Storage 객체를 정리합니다. 정리에 실패하면 imageId만 로그에 남깁니다. 본문에서 뺀 사진은 deleted_at을 기록하고 파일은 보존합니다. 공지 삭제도 soft delete이며 사진 다운로드 시 공지 상태와 사진 상태를 모두 검사합니다. 학생은 공개 중인 미삭제 공지의 사진만, 관리자는 미삭제 공지의 사진만 인증 API로 읽습니다. 응답은 no-store, inline, nosniff입니다.

## 환경별 적용 순서

새 환경에서는 아래 순서로 적용합니다. 실제 DB 변경은 사용자가 승인한 환경에서만 수행합니다.

1. 대상 DB를 확인하고 V34를 적용합니다. notice.body_json(jsonb)을 추가하고 notice_image 테이블·인덱스·RLS를 만듭니다. 기존 공지 데이터는 유지합니다.
2. 동일 Supabase 프로젝트에 notice-images **비공개** 버킷을 준비합니다. 허용 MIME은 image/jpeg, image/png, 객체 크기 한도는 5MiB입니다. anon/authenticated 직접 Storage 접근 정책을 만들지 않습니다.
3. 백엔드의 기존 AHNI_SUPABASE_SERVICE_ROLE_KEY를 사용합니다. 키를 관리자 웹이나 앱에 제공하지 않습니다. ahni.storage.notice-image-bucket 기본값은 notice-images입니다.
4. 마이그레이션과 버킷 준비 후 백엔드, 관리자 웹, 앱 순서로 배포합니다. 사진 없는 기존 앱은 body 평문으로 계속 표시합니다.

## 실패 예시

- 400 INVALID_NOTICE_CONTENT: {"code":"INVALID_NOTICE_CONTENT","message":"공지 내용과 사진 형식을 확인해 주세요."}
- 404 NOTICE_IMAGE_NOT_FOUND: {"code":"NOTICE_IMAGE_NOT_FOUND","message":"공지 사진을 찾을 수 없습니다."}
- 503 NOTICE_IMAGE_STORAGE_UNAVAILABLE: {"code":"NOTICE_IMAGE_STORAGE_UNAVAILABLE","message":"사진 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."}

관련 검증은 NoticeDocumentTest, NoticeRichContentIntegrationTest, DirectClientAccessMigrationIntegrationTest입니다. 실제 데이터 대신 격리 DB와 모의 Storage를 사용합니다.

## 개발 환경 적용 기록 (2026-11-19)

사용자 승인 후 `.env.dev`의 로컬 개발 DB `localhost:5433/ahni_supabase_copy`에 V34 SQL을 단일 트랜잭션으로 적용했습니다. `notice.body_json`의 jsonb 형식과 `notice_image`의 RLS 활성화를 확인했습니다. 개발 프로필은 Flyway를 사용하지 않으므로 Flyway 이력을 수동으로 추가하지 않았습니다.

같은 Auth 프로젝트의 Supabase Storage에 `notice-images` 비공개 버킷을 생성했습니다. 객체 크기 한도 5MiB, 허용 MIME `image/jpeg`, `image/png`이며 클라이언트 직접 접근 정책은 추가하지 않았습니다. Supabase 운영 애플리케이션 DB는 V26 상태로 확인되어 이번 작업에서 미적용 V27 이후의 기능까지 함께 변경하지 않았습니다.

2026-11-19 적용 당시 `.env.dev`에 `AHNI_SUPABASE_SERVICE_ROLE_KEY`가 없어 실제 사진 업로드·다운로드 확인은 보류했습니다. 키를 채팅이나 저장소에 남기지 말고 로컬 환경 파일에만 추가한 뒤 백엔드를 다시 시작합니다.

## 저장소 연결 확인 (2026-11-25)

사용자가 `.env.dev`에 서버 키를 설정한 후 키를 출력하지 않고 검증했습니다. 로컬 DB의 `body_json`, `notice_image`와 RLS 적용 상태를 재확인했습니다. 비공개 `notice-images` 버킷에 임시 PNG를 업로드하고 같은 바이트를 인증 다운로드한 뒤 삭제했으며 모두 HTTP 200으로 완료했습니다. 새 환경 설정으로 개발 백엔드를 시작하고 `/actuator/health`의 UP 응답을 확인했습니다.

남은 수동 확인은 관리자 공지 작성 → 사진 삽입 → 초안 저장 → 다시 열어 사진 유지 확인 → 공개 → 학생 앱 목록 썸네일과 상세 본문 확인입니다. Supabase 운영 애플리케이션 DB 마이그레이션은 이번 연결 확인에서 실행하지 않았습니다.

## 운영 Flyway 적용 (2026-11-25)

사용자 승인 후 프로젝트와 같은 Flyway 12.4.0으로 운영 DB의 V1~V26 이력을 검증하고, 대기 중이던 V27~V34 총 8개를 순서대로 적용했습니다. 최종 버전은 V34이며 전체 34개 마이그레이션 검증을 통과했습니다. 기존 public 테이블 17개의 행 수가 변경 전후 동일함을 확인했습니다. Flyway repair나 수동 이력 삽입은 하지 않았습니다.

공지의 jsonb 본문과 사진 메타데이터·인덱스·RLS가 운영에도 준비됐습니다. 로컬에서 작성한 테스트 공지와 사진 메타데이터는 운영 DB로 복사하지 않았습니다. 사용자가 로컬 관리자 화면에서 이미지 저장과 표시를 확인했습니다. 서버 키는 추적하지 않는 백엔드 환경 파일에만 설정했습니다.

운영 public 스키마 전체를 로컬 파일로 내보내는 백업은 자동 승인 검토에서 데이터 반출 범위에 대한 별도 승인이 없다는 이유로 거절되어 수행하지 않았습니다. 실제 데이터 파일은 생성하지 않았으며, Flyway 트랜잭션과 기존 행 수 비교로 적용을 검증했습니다.

세 저장소의 전체 검증을 통과한 기능 브랜치를 함께 배포합니다. 운영 백엔드에도 AHNI_SUPABASE_SERVICE_ROLE_KEY를 별도 서버 환경변수로 설정해야 합니다. Supabase 연결은 세션 풀러 사용자명 postgres.PROJECT_REF와 sslmode=require를 사용하며 중복 reference 연결 옵션은 사용하지 않습니다.
