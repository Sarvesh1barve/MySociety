# Local setup and operations

## This workstation

- Workspace: D:\MySociety
- Backend: http://localhost:8081
- Frontend development server: http://localhost:4200
- Project PostgreSQL: 127.0.0.1:5433
- Portable binaries: .tools/postgres/pgsql/bin (ignored)
- Database files: .local/postgres-data (ignored)
- Uploaded files: .local/uploads (ignored, configured by UPLOAD_DIR)
- Generated secrets: .env (ignored)
- Existing listeners on 8080 and 5432 were preserved.

## Start the already initialized database

```powershell
.\.tools\postgres\pgsql\bin\pg_ctl.exe -D D:/MySociety/.local/postgres-data -l D:/MySociety/.local/postgres.log -o "-h 127.0.0.1 -p 5433" -w start
```

If it is already running, leave it running. Do not reinitialize its data directory.

## Fresh Windows setup

Prefer an existing PostgreSQL installation or Docker Compose. The optional initialize-local.ps1 supports portable PostgreSQL binaries at .tools/postgres/pgsql. It checks for an existing .env, generates random local secrets, chooses a free private PostgreSQL port, initializes a new database directory and creates mysociety and mysociety_test.

The portable binaries can be obtained from the official PostgreSQL/EDB Windows distribution. Downloaded tooling is not tracked in Git. The script is for a fresh checkout only and intentionally refuses to overwrite an existing .env.

## Run

```powershell
.\scripts\start-backend.ps1
# In another terminal:
cd frontend
npm ci
npm start
# In another terminal, from the root:
.\scripts\start-ngrok.ps1
```

Open the ignored .local/SUPER_ADMIN_ACCESS.md locally for the owner credentials and recovery codes. Do not print its contents in logs.

## Package and run without Maven

```powershell
. .\scripts\import-env.ps1
.\backend\mvnw.cmd -f backend\pom.xml verify
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
java -jar backend/target/my-society-1.0.0.jar
```

Use the default profile for real data. Normal startup creates only the required owner, role definitions and application settings. Test users exist only in dedicated test databases.

## Dev OpenAPI

Development enables /v3/api-docs and /swagger-ui/index.html. Production-like mode disables both. Use an access token from login for protected APIs. API authorization still applies regardless of which client sends the request.

## Test database protection

Backend integration tests intentionally clean and recreate the schema of the dedicated mysociety_test database. Their migration strategy refuses any other database name. Never point that test URL at a database containing valuable data.

## Process lifecycle

The database, backend, frontend dev server and ngrok are separate processes. Closing a terminal or sleeping the laptop can interrupt them. Stop only processes started for this project. Do not kill a service because it happens to use the preferred port.

No scheduled startup or recurring automation has been installed.

## Isolated browser tests

The mutating browser suites refuse to run against the live API. Keep `npm start` running on 4200, then:

```powershell
. .\scripts\import-env.ps1
.\backend\mvnw.cmd -f backend\pom.xml verify dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt' '-DincludeScope=test'
.\scripts\run-browser-tests.ps1
```

The script creates/reuses only `mysociety_e2e`, starts the test-only fixture server on loopback 8091, supplies separate random credentials, routes browser runtime config to it, and stops only that test process afterwards. PostgreSQL stays private. Fixtures cannot be enabled in the production JAR. `mysociety_test` is separately reset by backend integration tests; never use either test database for real data.
