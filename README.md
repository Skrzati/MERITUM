# MERITUM — Cyfrowy Asystent Ocen Okresowych (KPRM)

## Start (jedna komenda)
```bash
make up
```
Tworzy `infra/.env` z `infra/.env.example` (jeśli brak), buduje i uruchamia wszystkie kontenery.
Bez `make`: `cp infra/.env.example infra/.env && docker compose -f infra/docker-compose.yml up -d --build --wait`

| Serwis | URL |
|---|---|
| **Frontend (demo)** | **http://localhost:3000** |
| backend-core API | http://localhost:8080/api/health |
| ai-gateway (docs) | http://localhost:8000/docs |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Gotenberg | http://localhost:3001 |
| PostgreSQL | localhost:5432 |

Logowanie demo: `GET /api/auth/demo-users`, potem nagłówek `X-Demo-User-Id: <id>`.

`make down` · `make reset` (kasuje dane) · `make logs` · `make ps`
