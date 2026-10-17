# Security Baseline

- Supabase Auth owns student and administrator passwords. AHNI must not store, compare, log, or return passwords or password hashes.
- Authenticate every student and administrator endpoint.
- Configure `AHNI_CORS_ALLOWED_ORIGINS` with exact trusted web origins; never use a wildcard. Local `dev` defaults to the two localhost Vite origins.
- Authorize by role and resource ownership, not by client-provided identifiers alone.
- `curriculum` and `curriculum_course` use RLS without direct-client allow policies. `anon` and `authenticated` must not directly read or mutate them; clients use Spring APIs. The configured JDBC role requires verified server access (currently `postgres` with BYPASSRLS). Recheck access before changing that role; do not grant BYPASSRLS to client roles.
- The administrator client checks the authenticated identity through `GET /api/v1/admin/me` before rendering protected routes; every protected endpoint still enforces administrator authorization independently.
- Authorize graduation-policy mutations only when the JWT subject matches an active `admin.auth_user_id`; return `ADMIN_ACCESS_DENIED` otherwise.
- Provision administrators out of band by linking an existing Supabase Auth user to `admin`; do not expose a public administrator-registration endpoint.
- Restrict signup through exact entries in `allowed_signup_email_domain`; do not allow a domain solely because it ends in `.edu` or `.ac.kr`.
- Treat email confirmation as proof of mailbox control, not proof of current enrollment or legal identity.
- Do not relink a student profile to a new Supabase user ID using email alone.
- Validate file type, size, and content before OCR or storage.
- Inquiry attachments live in the private Supabase Storage bucket `inquiry-attachments` without `storage.objects` client policies. Only the backend reads or writes it with `AHNI_SUPABASE_SERVICE_ROLE_KEY`; never return public or signed URLs, and always download as `attachment` with the server-validated content type.
- `inquiry` and `inquiry_attachment` use RLS without direct-client allow policies, like `curriculum`.
- Keep secrets in environment variables or a secret manager, never in Git.
- Treat OCR, map, notification, and other external service responses as untrusted input.
