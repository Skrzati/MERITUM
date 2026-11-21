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
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | 127.0.0.1:5432 (tylko lokalnie) |

ai-gateway i Gotenberg nie mają portów hosta — są dostępne wyłącznie dla backend-core w sieci Dockera
(AI tylko przez audytowane `/api/core-ai/*`).

Logowanie demo: frontend loguje automatycznie (JWT z `POST /api/auth/gov-login`); w Swaggerze/curl — token z
`gov-login` jako `Authorization: Bearer <token>` albo nagłówek `X-Demo-User-Id: <id>` (wyłączany `DEMO_HEADER_ENABLED=false`).

`make down` · `make reset` (kasuje dane) · `make logs` · `make ps`
