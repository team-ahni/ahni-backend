CREATE TABLE public.notice (
    entity_id uuid PRIMARY KEY,
    title varchar(150) NOT NULL CHECK (length(btrim(title)) > 0),
    body varchar(10000) NOT NULL CHECK (length(btrim(body)) > 0),
    updated_at timestamptz NOT NULL,
    published_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);

ALTER TABLE public.notice ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    client_role text;
BEGIN
    FOR client_role IN SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated')
    LOOP
        EXECUTE format('REVOKE ALL ON public.notice FROM %I', client_role);
    END LOOP;
END
$$;
