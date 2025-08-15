# Deployment Makefile for DMS Frontend and Backend
# Track login and configuration status
LOGIN_STATUS_FILE := .docker_login_status
USERNAME_FILE := .docker_username
ENV_FILE := .env

# Available services
SERVICES := eureka-discovery-server api-gateway auth-service document-interaction-service document-search-service document-processor-service frontend

# Frontend configuration
FRONTEND_IMAGE_NAME := loidinh/dms-frontend
FRONTEND_IMAGE_TAG := latest
FRONTEND_CONTAINER_NAME := dms-frontend
FRONTEND_HOST_PORT := 3000
FRONTEND_CONTAINER_PORT := 3000

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
	@echo "  make deploy-frontend              - Deploy only the frontend container"
	@echo "  make deploy-stack [ENV=<env-file>] [PROFILE=profile] - Deploy the backend stack"
	@echo "  make deploy-all [ENV=<env-file>] [PROFILE=profile]   - Deploy both frontend and backend"
	@echo "  make down-frontend                - Stop and remove frontend container"
	@echo "  make down-stack                   - Remove the backend stack"
	@echo "  make down-all                     - Remove both frontend and backend"
	@echo ""
	@echo "Available services:"
	@echo "  $(SERVICES)"

# Get username (stored or prompt)
.PHONY: get-username
get-username:
	@if [ ! -f $(USERNAME_FILE) ]; then \
		read -p "Docker Hub username: " username; \
		echo -n "$$username" > $(USERNAME_FILE); \
	fi

# Get the stored username
define get_docker_username
$(shell cat $(USERNAME_FILE) 2>/dev/null || echo "")
endef

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
build-all: get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
	@if [ -z "$(DOCKER_HUB_USERNAME)" ]; then \
		echo "Error: Docker Hub username is empty. Please run 'make clean' and try again."; \
		exit 1; \
	fi
	@echo "Building all services using username: $(DOCKER_HUB_USERNAME)"
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose --env-file .env -f docker-compose.build.yml build

# Push all services
.PHONY: push-all
push-all: check-login get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
	@if [ -z "$(DOCKER_HUB_USERNAME)" ]; then \
		echo "Error: Docker Hub username is empty. Please run 'make clean' and try again."; \
		exit 1; \
	fi
	@echo "Pushing all services using username: $(DOCKER_HUB_USERNAME)"
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml push

# Build and push all services
.PHONY: build-push-all
build-push-all: get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
	@echo "Building and pushing all services using username: $(DOCKER_HUB_USERNAME)"
	@make build-all && make push-all

# Build specific service
.PHONY: build
build: get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
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
	@echo "Building $(SERVICE) using username: $(DOCKER_HUB_USERNAME)"
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml build $(SERVICE)

# Push specific service
.PHONY: push
push: check-login get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
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
	@echo "Pushing $(SERVICE) using username: $(DOCKER_HUB_USERNAME)"
	DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml push $(SERVICE)

# Build and push specific service
.PHONY: build-push
build-push: get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
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
	@echo "Building and pushing $(SERVICE) using username: $(DOCKER_HUB_USERNAME)"
	@if DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml build $(SERVICE); then \
		echo "Build successful! Preparing to push..."; \
		read -p "Do you want to push the image to Docker Hub? [y/N] " answer; \
		if [ "$$answer" = "y" ] || [ "$$answer" = "Y" ]; then \
			make check-login && \
			echo "Pushing $(SERVICE) using username: $(DOCKER_HUB_USERNAME)" && \
			DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) docker compose -f docker-compose.build.yml push $(SERVICE); \
		else \
			echo "Push cancelled."; \
		fi \
	else \
		echo "Build failed. Aborting push."; \
		exit 1; \
	fi

# Deploy frontend container
.PHONY: deploy-frontend
deploy-frontend:
	@echo "Deploying frontend container..."
	@echo "Stopping and removing existing container if it exists..."
	docker stop $(FRONTEND_CONTAINER_NAME) 2>/dev/null || true
	docker rm $(FRONTEND_CONTAINER_NAME) 2>/dev/null || true
	@echo "Pulling latest image from Docker Hub..."
	docker pull $(FRONTEND_IMAGE_NAME):$(FRONTEND_IMAGE_TAG)
	@echo "Starting container..."
	docker run -d \
	  --name $(FRONTEND_CONTAINER_NAME) \
	  -p $(FRONTEND_HOST_PORT):$(FRONTEND_CONTAINER_PORT) \
	  -e NODE_ENV=production \
	  --restart always \
	  $(FRONTEND_IMAGE_NAME):$(FRONTEND_IMAGE_TAG)
	@echo "Frontend deployment complete. Running at http://localhost:$(FRONTEND_HOST_PORT)"

# Load and export environment variables
.PHONY: set-env
set-env: get-username
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
	@if [ -z "$(DOCKER_HUB_USERNAME)" ]; then \
		echo "Error: Docker Hub username is empty. Please run 'make clean' and try again."; \
		exit 1; \
	fi
	
	# Set environment file
	$(eval ENV_FILE_TO_USE := $(if $(ENV),$(ENV),.env))
	
	# Check if env file exists
	@if [ -f "$(ENV_FILE_TO_USE)" ]; then \
		echo "Loading environment variables from $(ENV_FILE_TO_USE)"; \
	else \
		echo "No environment file found at $(ENV_FILE_TO_USE). Using command line arguments."; \
	fi
	
	# Set spring profile if provided or use default
	$(eval SPRING_PROFILE := $(if $(PROFILE),$(PROFILE),dev))
	
	# Display the environment variables that will be used
	@echo "Using the following environment variables:"
	@echo "DOCKER_HUB_USERNAME: $(DOCKER_HUB_USERNAME)"
	@echo "SPRING_PROFILE: $(SPRING_PROFILE)"

# Deploy backend stack
.PHONY: deploy-stack
deploy-stack: set-env
	$(eval DOCKER_HUB_USERNAME := $(call get_docker_username))
	
	# Deploy the stack with environment variables
	@echo "Deploying backend stack..."
	
	# If env file exists, use it with environment variables
	@if [ -f "$(ENV_FILE_TO_USE)" ]; then \
		echo "Using environment file: $(ENV_FILE_TO_USE)"; \
		env $(shell grep -v '^#' $(ENV_FILE_TO_USE) | tr -d '\r' | xargs) \
		DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) \
		SPRING_PROFILE=$(SPRING_PROFILE) \
		docker stack deploy -c docker-compose.yml dms; \
	else \
		echo "Using command line environment variables"; \
		DOCKER_HUB_USERNAME=$(DOCKER_HUB_USERNAME) \
		SPRING_PROFILE=$(SPRING_PROFILE) \
		docker stack deploy -c docker-compose.yml dms; \
	fi
	
	# Wait for services to start
	@echo "Waiting for services to start..."
	@sleep 30
	
	# Check service status
	@echo "Service status:"
	docker service ls

# Deploy both frontend and backend
.PHONY: deploy-all
deploy-all: deploy-stack deploy-frontend
	@echo "Full deployment completed successfully!"

# Stop and remove frontend container
.PHONY: down-frontend
down-frontend:
	@echo "Stopping and removing frontend container..."
	docker stop $(FRONTEND_CONTAINER_NAME) 2>/dev/null || true
	docker rm $(FRONTEND_CONTAINER_NAME) 2>/dev/null || true
	@echo "Frontend container removed successfully"

# Remove backend stack
.PHONY: down-stack
down-stack:
	@echo "Removing backend stack..."
	docker stack rm dms
	@echo "Backend stack removal initiated"
	@echo "Note: It may take a few moments for all services to be completely removed"

# Remove both frontend and backend
.PHONY: down-all
down-all: down-stack down-frontend
	@echo "Full cleanup completed successfully!"

# Clean login status and username
.PHONY: clean
clean:
	@rm -f $(LOGIN_STATUS_FILE) $(USERNAME_FILE)
	@echo "Docker login status and username cleared."