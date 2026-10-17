# Inquiry Attachment Design

**Status:** Approved implementation slice

**Date:** 2026-10-17

## Source Alignment

- Use-case specifications, inquiry handling: a student may attach evidence
  (for example a screenshot of a grade screen) to an inquiry, and an
  administrator reviews it while answering.
- `docs/security/README.md`: validate file type, size, and content before
  storage; keep secrets in environment variables; authorize by ownership.
- Storage decision (2026-10-17): Supabase Storage private bucket. Every upload
  and download goes through the Spring API; clients never receive storage
  credentials, public URLs, or signed URLs.

## Storage

- Bucket `inquiry-attachments`, `public = false`, 5 MiB file limit, and
  the allowed MIME types below. `V23` creates it when the `storage` schema
  exists; plain PostgreSQL test databases skip that step.
- No `storage.objects` policy is added, so `anon` and `authenticated` cannot
  read or write objects directly. The backend uses the service-role key from
  `AHNI_SUPABASE_SERVICE_ROLE_KEY`, which is never logged or returned.
- Object key: `inquiries/{inquiryEntityId}/{attachmentEntityId}`. The original
  filename is never part of the key.
- A storage failure or a missing key returns
  `INQUIRY_ATTACHMENT_STORAGE_UNAVAILABLE` (503) without provider details.

## Data Model

`inquiry_attachment` belongs to one inquiry and stores the immutable entity
ID, storage key, display filename, validated content type, byte size, and
creation time. RLS is enabled on `inquiry_attachment` and, in the same
migration, on `inquiry`, which `V21` left without RLS.

## Validation

All checks run on the server before storage:

| Rule | Value | Error |
| --- | --- | --- |
| Size | 1 byte – 5 MiB (multipart limit and service check) | `INQUIRY_ATTACHMENT_TOO_LARGE` 413, `INVALID_INQUIRY_ATTACHMENT` 400 for empty |
| Count | at most 3 per inquiry | `INQUIRY_ATTACHMENT_LIMIT_EXCEEDED` 409 |
| Type | extension, declared MIME type, and magic bytes must all agree: `.jpg/.jpeg` `image/jpeg` `FF D8 FF`; `.png` `image/png` PNG signature; `.pdf` `application/pdf` `%PDF-` | `UNSUPPORTED_INQUIRY_ATTACHMENT_TYPE` 415 |
| Filename | directory parts and control characters removed; 1–255 characters remain | `INVALID_INQUIRY_ATTACHMENT` 400 |

SVG, HTML, and office documents are rejected because the administrator web
could render them as active content. The stored content type is the
server-validated one, never the raw client header.

## API

| Method | Path | Auth |
| --- | --- | --- |
| POST (multipart `file`) | `/api/v1/inquiries/{inquiryEntityId}/attachments` | owning student |
| GET | `/api/v1/inquiries/{inquiryEntityId}/attachments` | owning student |
| GET | `/api/v1/inquiries/{inquiryEntityId}/attachments/{attachmentEntityId}` | owning student |
| DELETE | `/api/v1/inquiries/{inquiryEntityId}/attachments/{attachmentEntityId}` | owning student |
| GET | `/api/v1/admin/inquiries/{inquiryEntityId}/attachments` | active administrator |
| GET | `/api/v1/admin/inquiries/{inquiryEntityId}/attachments/{attachmentEntityId}` | active administrator |

- Student access resolves the inquiry through the owner-scoped, non-deleted
  query, then the attachment by both inquiry and attachment ID. Another
  student's inquiry or attachment returns 404 (`INQUIRY_NOT_FOUND` or
  `INQUIRY_ATTACHMENT_NOT_FOUND`), never 403.
- Adding or deleting follows the inquiry edit rule: allowed while `SUBMITTED`
  or `IN_REVIEW`, otherwise `INQUIRY_UPDATE_CONFLICT` (409).
- Administrators can read attachments of soft-deleted inquiries, matching the
  administrator inquiry list. Reading attachments does not change status.
- Downloads respond with the stored content type,
  `Content-Disposition: attachment` (RFC 5987 filename), and Spring Security's
  default `X-Content-Type-Options: nosniff` and no-cache headers.

## Consistency

- Upload stores the object first and then the row; if the row insert fails,
  the object is deleted on a best-effort basis.
- Delete removes the row, flushes, then deletes the object; a storage failure
  rolls the row back.
- Soft-deleting an inquiry keeps its attachments for administrator history.

## Out of Scope

- Malware scanning; the narrow allowlist and forced download reduce exposure.
- Stripping image metadata (EXIF location). The server does not re-encode
  images; the mobile client should remove metadata before upload.
- Retention and hard deletion of attachments of soft-deleted inquiries.
- Request-rate limiting, which the project does not have yet; the count and
  size limits bound per-inquiry usage.
- Attachments on administrator answers.
