COMPOSE := docker compose -f infra/docker-compose.yml

.PHONY: up down logs ps reset build test test-ai build-fe

infra/.env:
	cp infra/.env.example infra/.env

up: infra/.env ## Buduje i uruchamia wszystkie kontenery
	$(COMPOSE) up -d --build --wait

down: ## Zatrzymuje kontenery
	$(COMPOSE) down

reset: ## Zatrzymuje kontenery i usuwa dane bazy
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

build:
	cd backend-core && mvn -B clean verify

test: ## Testy backend-core
	cd backend-core && mvn -B test

test-ai: ## Testy ai-gateway (wymaga Python 3.9+)
	cd ai-gateway && python3 -m venv .venv && .venv/bin/pip install -q -r requirements-dev.txt && .venv/bin/python -m pytest -q

build-fe: ## Build frontendu (TypeScript + Next.js)
	cd frontend && npm ci && npm run build
