#!/bin/bash

ENV_FILE=".env"
CUSTOM_ENV_FILE=""

# Check for --env parameter
for i in "$@"; do
  case $i in
    --env=*)
      CUSTOM_ENV_FILE="${i#*=}"
      shift
      ;;
    *)
      # Keep this for other parameters
      ;;
  esac
done

# If a custom env file is specified, use it instead of the default
if [ ! -z "$CUSTOM_ENV_FILE" ]; then
  ENV_FILE="$CUSTOM_ENV_FILE"
fi

# Check if env file exists and load it
if [ -f "$ENV_FILE" ]; then
  echo "Loading environment variables from $ENV_FILE"
  # Use tr to remove any carriage returns before exporting
  export $(grep -v '^#' $ENV_FILE | tr -d '\r' | xargs)
else
  echo "No .env file found at $ENV_FILE. Will use command line arguments if provided."
fi

# Override with command-line arguments if provided
if [ ! -z "$1" ]; then
  export DOCKER_HUB_USERNAME=$1
fi

if [ ! -z "$2" ]; then
  export SPRING_PROFILE=$2
fi

if [ ! -z "$3" ]; then
  export FRONT_END_URL=$3
fi

if [ ! -z "$4" ]; then
  export SERVICE_ENDPOINT_URL=$4
fi

if [ ! -z "$5" ]; then
  export RESOURCE_URL=$5
fi

if [ ! -z "$6" ]; then
  export EUREKA_URI=$6
fi

# Check if Docker Hub username is set
if [ -z "$DOCKER_HUB_USERNAME" ]; then
    echo "Docker Hub username not set. Please provide it in the .env file or as the first argument."
    echo "Usage: ./deploy-stack.sh [--env=<env-file>] [<dockerhub-username> <spring-profile> <frontend-url> <service-endpoint-url> <resource-url> <eureka-uri>]"
    echo "Alternatively, create a .env file with the required variables"
    exit 1
fi

# Check other required variables
if [ -z "$SPRING_PROFILE" ] || [ -z "$DOCKER_HUB_USERNAME" ]; then
    echo "One or more required environment variables are not set."
    echo "Please set the following variables in the .env file or provide them as arguments:"
    echo "- DOCKER_HUB_USERNAME"
    echo "- SPRING_PROFILE"
    exit 1
fi

# Clean variables of any potential carriage returns
DOCKER_HUB_USERNAME=$(echo "$DOCKER_HUB_USERNAME" | tr -d '\r')
SPRING_PROFILE=$(echo "$SPRING_PROFILE" | tr -d '\r')

# Display the environment variables that will be used
echo "Using the following environment variables:"
echo "DOCKER_HUB_USERNAME: $DOCKER_HUB_USERNAME"
echo "SPRING_PROFILE: $SPRING_PROFILE"

# Deploy the stack
docker stack deploy -c docker-compose.yml dms

# Wait for services to start
echo "Waiting for services to start..."
sleep 30

# Check service status
docker service ls