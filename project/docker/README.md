```shell
./build-and-push.sh loidinh
```

```shell
docker service logs dms_document-processor-service
```

```shell
docker exec -it $(docker ps -q -f name=dms_document-processor) sh
```

```shell
docker stats $(docker ps -q -f name=dms_document-processor)
```