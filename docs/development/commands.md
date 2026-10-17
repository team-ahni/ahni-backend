# Development Commands

## Prerequisites

- Java 26
- Docker for PostgreSQL integration tests
- Gradle Wrapper included in the repository

## Commands

```bash
./scripts/setup             # resolve and report Gradle dependencies
./scripts/dev               # run the local API
./scripts/test-unit         # run tests without the integration tag
./scripts/test-integration  # run Spring and PostgreSQL boundary tests
./scripts/lint              # run the Gradle check lifecycle
./scripts/typecheck         # compile production and test sources
./scripts/verify            # clean, check, and run integration tests
```

The default Gradle `test` task and `unitTest` exclude the JUnit `integration` tag. `integrationTest` includes only that tag. `scripts/verify` is the authoritative local and CI entry point and runs integration tests once, after the default verification lifecycle.

The project uses the Gradle Wrapper. Do not require a globally installed Gradle version for development or CI. Docker is a hard prerequisite for the PostgreSQL Testcontainers test; when Docker is unavailable, `integrationTest` and `scripts/verify` fail with a clear prerequisite message.

## Local development run

`scripts/dev` loads the untracked `.env.dev` file from the backend repository root and runs the `dev` profile. It does not load `.env` or fall back to the Supabase database. Use `.env.dev.example` as the template; an existing `.env.dev` is retained.

```bash
./scripts/dev
```

Set `AHNI_DEV_DB_URL`, `AHNI_DEV_DB_USERNAME`, and `AHNI_DEV_DB_PASSWORD` for the local Docker database. Keep `AHNI_SUPABASE_URL` pointing to the existing Auth project: JWT validation still uses Supabase Auth, while application data is stored locally. Inquiry attachments additionally need `AHNI_SUPABASE_SERVICE_ROLE_KEY`; without it the API starts, and attachment calls return `INQUIRY_ATTACHMENT_STORAGE_UNAVAILABLE`.

Start the database before the API:

```bash
docker compose --env-file .env.dev -f compose.dev.yml up -d postgres
./scripts/dev
```

The Compose configuration uses database/user `ahni_dev` and host port `5433`. The password must match the existing database volume; changing the environment file alone does not change an initialized database password.

## Supabase database run

The separate `.env` file remains available for intentional Supabase database access:

```bash
set -a
source .env
set +a
./gradlew bootRun --args='--spring.profiles.active=supabase'
```

The Supabase profile runs Flyway against the configured PostgreSQL database. Never commit `.env` or `.env.dev`, or print their values in logs. Switching profiles does not copy data between databases.

## Local Docker database

The `dev` profile uses the local PostgreSQL connection defined by `AHNI_DEV_DB_URL`, `AHNI_DEV_DB_USERNAME`, and `AHNI_DEV_DB_PASSWORD`. Hibernate uses `ddl-auto=update` to create missing tables and update the schema without recreating existing tables at startup. Flyway remains disabled for this profile; the Supabase profile continues to use migrations.

Restarting the backend preserves local academic records and administrator profiles. The Docker database also uses the named `postgres_data` volume, which survives ordinary container stops and starts. Do not use `docker compose down -v` or remove that volume when you need to keep its data.

Automatic schema updates are for local development only and do not replace reviewed migrations or backups. Previously erased data cannot be recovered by changing this setting. The restart-preservation regression test runs with isolated PostgreSQL, not the developer's database:

```bash
./gradlew integrationTest --tests '*DevelopmentDatabasePersistenceIntegrationTest'
```
