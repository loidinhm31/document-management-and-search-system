docker run -d --hostname rabbitmq --name rabbitmq -p 5672:5672 15672:15672 rabbitmq:3-management-alpine  

docker run -p 9200:9200 \
--name elasticsearch \
-e "discovery.type=single-node" \
-e "xpack.security.enabled=false" \
-e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
-d docker.elastic.co/elasticsearch/elasticsearch:8.8.1


Add authentication
```shell
curl -X GET "localhost:9200/documents/_count" \
-H "Content-Type: application/json" \
-u "elastic:NX9DYn+_4WSXRmHwymTo"
```


curl -X DELETE "localhost:9200/documents"


curl -X GET "localhost:9200/documents/_search" -H "Content-Type: application/json" -d '{}'

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