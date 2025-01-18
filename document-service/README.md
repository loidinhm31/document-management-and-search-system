docker run -p 9200:9200 \
-e "discovery.type=single-node" \
-e "xpack.security.enabled=false" \
docker.elastic.co/elasticsearch/elasticsearch:8.8.1


curl -X DELETE "localhost:9200/documents"


curl -X GET "localhost:9200/documents/_search" -H "Content-Type: application/json" -d '{}'