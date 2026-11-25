ALTER TABLE public.notice ADD COLUMN body_json jsonb;

CREATE TABLE public.notice_image (
    entity_id uuid PRIMARY KEY,
    notice_id uuid NOT NULL REFERENCES public.notice(entity_id),
    storage_key varchar(200) NOT NULL UNIQUE,
    content_type varchar(32) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    size_bytes bigint NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    created_at timestamptz NOT NULL,
    deleted_at timestamptz
);
CREATE INDEX ix_notice_image_notice ON public.notice_image(notice_id);
ALTER TABLE public.notice_image ENABLE ROW LEVEL SECURITY;
DO $$
DECLARE client_role text;
BEGIN
    FOR client_role IN SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated')
    LOOP
        EXECUTE format('REVOKE ALL ON public.notice_image FROM %I', client_role);
    END LOOP;
END
$$;
