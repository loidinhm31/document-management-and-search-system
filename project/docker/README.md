```shell
docker service logs dms_document-processor-service
```

```shell
docker exec -it $(docker ps -q -f name=dms_document-processor) sh
```

```shell
docker stats $(docker ps -q -f name=dms_document-processor)
```

```shell
docker service update --force <SERVICE_NAME_OR_ID>
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

## Tunel
### RDS
```shell
ssh -i vockey.pem -L 5555:dms-xxxxxx.ap-southeast-1.rds.amazonaws.com:5432 -N -f ubuntu@11.389.136.118.222
```

## Install nginx
```shell
sudo apt install nginx

sudo vim /etc/nginx/sites-available/default

sudo nginx -t

sudo nginx -s reload
```

## Install SSL Certificate
```shell
sudo apt install python3-certbot-nginx

sudo certbot --nginx -d your-domain -d www.your-domain
```