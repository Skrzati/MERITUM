COMPOSE := docker compose -f infra/docker-compose.yml

.PHONY: up down logs ps reset build

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
