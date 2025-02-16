# Track username and login status
LOGIN_STATUS_FILE := .docker_login_status
USERNAME_FILE := .docker_username

# Function to get Docker Hub username
define get_username
	$(shell if [ ! -f $(USERNAME_FILE) ]; then \
		read -p "Docker Hub username: " username; \
		echo "$$username" > $(USERNAME_FILE); \
	fi; \
	cat $(USERNAME_FILE))
endef

# Docker Hub username - read from file or prompt once
DOCKER_HUB_USERNAME := $(call get_username)

# Available services
SERVICES := eureka-discovery-server api-gateway auth-service document-interaction-service document-search-service document-processor-service

# Default target
.PHONY: help
help:
	@echo "Available commands:"
	@echo "  make build-all                    - Build all services"
	@echo "  make push-all                     - Push all services"
	@echo "  make build-push-all               - Build and push all services"
	@echo "  make build SERVICE=<service-name> - Build specific service"
	@echo "  make push SERVICE=<service-name>  - Push specific service"
	@echo "  make build-push SERVICE=<service-name> - Build and push specific service"
	@echo "  make login                        - Login to Docker Hub"
	@echo "  make clean                        - Reset Docker login and username"
	@echo ""
	@echo "Available services:"
	@echo "  $(SERVICES)"

# Check if already logged in
.PHONY: check-login
check-login:
	@if [ ! -f $(LOGIN_STATUS_FILE) ]; then \
		echo "Docker Hub login required..."; \
		if docker login; then \
			touch $(LOGIN_STATUS_FILE); \
		else \
			exit 1; \
		fi \
	fi

# Login to Docker Hub
.PHONY: login
login:
	@rm -f $(LOGIN_STATUS_FILE)
	@make check-login

# Build all services
.PHONY: build-all
build-all:
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml build

# Push all services
.PHONY: push-all
push-all: check-login
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml push

# Build and push all services
.PHONY: build-push-all
build-push-all:
	@make build-all && make push-all

# Build specific service
.PHONY: build
build:
ifndef SERVICE
	@echo "Error: SERVICE is not set. Usage: make build SERVICE=<service-name>"
	@echo "Available services: $(SERVICES)"
	@exit 1
endif
ifeq ($(filter $(SERVICE),$(SERVICES)),)
	@echo "Error: Invalid service name '$(SERVICE)'"
	@echo "Available services: $(SERVICES)"
	@exit 1
endif
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml build $(SERVICE)

# Push specific service
.PHONY: push
push: check-login
ifndef SERVICE
	@echo "Error: SERVICE is not set. Usage: make push SERVICE=<service-name>"
	@echo "Available services: $(SERVICES)"
	@exit 1
endif
ifeq ($(filter $(SERVICE),$(SERVICES)),)
	@echo "Error: Invalid service name '$(SERVICE)'"
	@echo "Available services: $(SERVICES)"
	@exit 1
endif
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml push $(SERVICE)

# Build and push specific service
.PHONY: build-push
build-push:
ifndef SERVICE
	@echo "Error: SERVICE is not set. Usage: make build-push SERVICE=<service-name>"
	@echo "Available services: $(SERVICES)"
	@exit 1
endif
ifeq ($(filter $(SERVICE),$(SERVICES)),)
	@echo "Error: Invalid service name '$(SERVICE)'"
	@echo "Available services: $(SERVICES)"
	@exit 1
endif
	@echo "Building $(SERVICE)..."
	@if DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml build $(SERVICE); then \
		echo "Build successful! Preparing to push..."; \
		read -p "Do you want to push the image to Docker Hub? [y/N] " answer; \
		if [ "$$answer" = "y" ] || [ "$$answer" = "Y" ]; then \
			make check-login && \
			DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml push $(SERVICE); \
		else \
			echo "Push cancelled."; \
		fi \
	else \
		echo "Build failed. Aborting push."; \
		exit 1; \
	fi

# Clean login status and username
.PHONY: clean
clean:
	@rm -f $(LOGIN_STATUS_FILE) $(USERNAME_FILE)
	@echo "Docker login status and username cleared."