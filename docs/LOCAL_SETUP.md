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
.\scripts\start-backend.ps1 -Dev
# In another terminal:
cd frontend
npm ci
npm start
# In another terminal, from the root:
.\scripts\start-ngrok.ps1
```

Open .env locally for the generated owner and demo passwords. Do not print its contents in logs.

## Package and run without Maven

```powershell
. .\scripts\import-env.ps1
.\backend\mvnw.cmd -f backend\pom.xml verify
$env:SPRING_PROFILES_ACTIVE = 'dev'
java -jar backend/target/my-society-1.0.0.jar
```

For real data, remove the dev profile, use a fresh real database and configure the real owner identity before the first startup.

## Dev OpenAPI

Development enables /v3/api-docs and /swagger-ui/index.html. Production-like mode disables both. Use an access token from login for protected APIs. API authorization still applies regardless of which client sends the request.

## Test database protection

Backend integration tests intentionally clean and recreate the schema of the dedicated mysociety_test database. Their migration strategy refuses any other database name. Never point that test URL at a database containing valuable data.

## Process lifecycle

The database, backend, frontend dev server and ngrok are separate processes. Closing a terminal or sleeping the laptop can interrupt them. Stop only processes started for this project. Do not kill a service because it happens to use the preferred port.

No scheduled startup or recurring automation has been installed.
