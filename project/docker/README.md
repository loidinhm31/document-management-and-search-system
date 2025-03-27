```shell
docker service logs dms_document-processor-service
```

```shell
docker exec -it $(docker ps -q -f name=dms_document-processor) sh
```

```shell
docker stats $(docker ps -q -f name=dms_document-processor)
```

## Build all images
```shell
make build-push-all
```

## Build specific image
```shell
make build-push SERVICE=<<service-name>>
```

## Deploy stack services
```shell
./deploy-stack.sh
```

