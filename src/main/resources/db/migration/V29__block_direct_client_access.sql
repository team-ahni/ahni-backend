-- Supabase Data API는 anon/authenticated 역할로 public 표에 직접 접근한다.
-- 클라이언트는 모두 Spring API(BYPASSRLS JDBC 역할)를 거치므로 직접 접근은 전부 막는다.
-- 표 이름을 나열하지 않고 public 전체를 돌아, 이 파일보다 먼저 생긴 표도 빠짐없이 막는다. 재실행해도 안전하다.
DO $$
DECLARE
    target record;
    client_roles text;
BEGIN
    FOR target IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          -- Flyway가 실행 중 다른 연결로 잠그고 있어 ALTER하면 멈춘다. 아래 REVOKE로 막는다.
          AND c.relname <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', target.relname);
    END LOOP;

    SELECT string_agg(quote_ident(rolname), ', ')
    INTO client_roles
    FROM pg_roles
    WHERE rolname IN ('anon', 'authenticated');

    -- 로컬·테스트 DB에는 Supabase 역할이 없다.
    IF client_roles IS NOT NULL THEN
        EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %s', client_roles);
        EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %s', client_roles);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %s', client_roles);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %s', client_roles);
    END IF;
END
$$;
