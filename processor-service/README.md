```shell
docker run -d --hostname rabbitmq \
    --name rabbitmq \
    -p 5672:5672 \
    -p 15672:15672 \
    rabbitmq:3-management-alpine  
```
```shell
docker run --name localstack \
   -p 127.0.0.1:4566:4566 \
   -p 127.0.0.1:4510-4559:4510-4559 \
   -v /var/run/docker.sock:/var/run/docker.sock \
   -d localstack/localstack
```

```shell
docker run -p 9200:9200 \
--name elasticsearch \
-e "discovery.type=single-node" \
-e "xpack.security.enabled=true" \
-e "ELASTIC_PASSWORD=NX9DYn+_4WSXRmHwymTo" \
-e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
-e "xpack.security.http.ssl.enabled=false" \
-d docker.elastic.co/elasticsearch/elasticsearch:8.8.1
```

```shell
docker run -p 9200:9200 -p 9600:9600 \
--name opensearch \
-e "discovery.type=single-node" \
-e "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m" \
-e "DISABLE_SECURITY_PLUGIN=false" \
-e "plugins.security.ssl.http.enabled=false" \
-e "plugins.security.allow_unsafe_democertificates=true" \
-e "plugins.security.allow_default_init_securityindex=true" \
-e "plugins.security.audit.type=internal_opensearch" \
-e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=27H4Gwxb24XGsARfuh3b#" \
-d opensearchproject/opensearch:2.17.0
```


Add authentication
```shell
curl -X GET "localhost:9200/documents/_count" \
-H "Content-Type: application/json" \
-u "elastic:NX9DYn+_4WSXRmHwymTo"
```


```shell
curl -X DELETE "localhost:9200/documents" -u "admin:admin"
```


curl -X GET "localhost:9200/documents/_search" -H "Content-Type: application/json" -d '{}' -u "admin:27H4Gwxb24XGsARfuh3b#"

curl -X GET "https://vpc-dms-opensearch-7m6m3jd7mq6uz4r5bh3quasela.ap-southeast-1.es.amazonaws.com/documents/_search" -H "Content-Type: application/json" -d '{}' -u "dms_master:hzE8MYCuBvzkV3KT8kedTyWthea6ar1xC#"
 

curl -X PUT "localhost:9200/documents" -H "Content-Type: application/json" -d @mapping.json

To get a specific document by ID:
```shell
curl -X GET "localhost:9200/documents/_doc/{document_id}" -H "Content-Type: application/json"
```

To search for documents by user ID:
```shell
curl -X GET "localhost:9200/documents/_search" -H "Content-Type: application/json" -d'
{
  "query": {
    "term": {
      "userId": "your-user-id"
    }
  }
}'
```

```shell
curl -X GET "localhost:9200/documents/_search" -H "Content-Type: application/json" -d'
{
  "query": {
    "term": {
      "id": "678e97961e345b75cc5b7af2"
    }
  }
}'
```