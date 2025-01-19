docker run -d --hostname rabbitmq --name rabbitmq -p 5672:5672 15672:15672 rabbitmq:3-management-alpine  

docker run -p 9200:9200 \
--name elasticsearch \
-e "discovery.type=single-node" \
-e "xpack.security.enabled=false" \
-d docker.elastic.co/elasticsearch/elasticsearch:8.8.1


curl -X DELETE "localhost:9200/documents"


curl -X GET "localhost:9200/documents/_search" -H "Content-Type: application/json" -d '{}'

curl -X PUT "localhost:9200/documents" -H "Content-Type: application/json" -d @mapping.json