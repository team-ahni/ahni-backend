ALTER TABLE public.notice
    ADD COLUMN created_at timestamptz,
    ADD COLUMN created_by_name varchar(100);

CREATE TABLE public.notice_attachment (
    entity_id uuid PRIMARY KEY,
    notice_id uuid NOT NULL REFERENCES public.notice(entity_id) ON DELETE CASCADE,
    filename varchar(255) NOT NULL,
    content_type varchar(32) NOT NULL,
    content bytea NOT NULL,
    CONSTRAINT ck_notice_attachment_size CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);

CREATE INDEX ix_notice_attachment_notice ON public.notice_attachment(notice_id);
ALTER TABLE public.notice_attachment ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    client_role text;
BEGIN
    FOR client_role IN SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated')
    LOOP
        EXECUTE format('REVOKE ALL ON public.notice_attachment FROM %I', client_role);
    END LOOP;
END
$$;
