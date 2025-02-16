#!/bin/bash

# Check if Docker Hub username is provided
if [ -z "$1" ]; then
    echo "Please provide your Docker Hub username"
    echo "Usage: ./build-and-push.sh <dockerhub-username>"
    exit 1
fi

export DOCKER_HUB_USERNAME=$1

# Login to Docker Hub
echo "Logging in to Docker Hub..."
docker login

# Build images
echo "Building images..."
docker compose -f docker-compose.build.yml build

# Push images
echo "Pushing images to Docker Hub..."
docker compose -f docker-compose.build.yml push

echo "Build and push completed successfully!"