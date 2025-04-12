#!/bin/bash

# Script to deploy the React frontend Docker image directly with Spring Gateway URL

# Configuration
IMAGE_NAME="loidinh/dms-frontend"
IMAGE_TAG="latest"
CONTAINER_NAME="dms-frontend"
HOST_PORT=3000
CONTAINER_PORT=3000

# Stop and remove existing container if it exists
echo "Stopping and removing existing container if it exists..."
docker stop $CONTAINER_NAME 2>/dev/null || true
docker rm $CONTAINER_NAME 2>/dev/null || true

# Pull the latest image
echo "Pulling latest image from Docker Hub..."
docker pull $IMAGE_NAME:$IMAGE_TAG

# Run the container with the environment variable
echo "Starting container..."
docker run -d \
  --name $CONTAINER_NAME \
  -p $HOST_PORT:$CONTAINER_PORT \
  -e NODE_ENV=production \
  --restart always \
  $IMAGE_NAME:$IMAGE_TAG

echo "Deployment complete. Frontend is running at http://localhost:$HOST_PORT"