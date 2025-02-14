#!/bin/bash

# Check if Docker Hub username is provided
if [ -z "$1" ]; then
    echo "Please provide your Docker Hub username"
    echo "Usage: ./deploy-stack.sh <dockerhub-username> <spring-profile>"
    exit 1
fi

export DOCKER_HUB_USERNAME=$1
export SPRING_PROFILE=$2
export FRONT_END_URL=$3
export SERVICE_ENDPOINT_URL=$4
export RESOURCE_URL=$5
export EUREKA_URI=$6

# Deploy the stack
docker stack deploy -c docker-compose.yml dms

# Wait for services to start
echo "Waiting for services to start..."
sleep 30

# Check service status
docker service ls