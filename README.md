# kafka-connect-arangodb-tests

Create Docker network:
```shell
./docker/create_network.sh
```

Start db:
```shell
./docker/start_db.sh
```

Create collections `products` in `_system` database:
```shell
curl -u root:test http://localhost:8529/_api/collection -d '{"name": "products"}' | jq
```

Check UI at [http://localhost:8529](http://localhost:8529).

Start Kafka cluster with Connect:
```shell
docker-compose -f ./docker/docker-compose.yml up
```

Check UI at [http://localhost:8080](http://localhost:8080).

Create Kafka topic:
```shell
~/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092,localhost:9192,localhost:9292, --topic stream.products --create --partitions 3 --replication-factor 1
```

Publish data to Kafka:
```shell
curl --request POST \
    --url "http://localhost:18083/connectors" \
    --header 'content-type: application/json' \
    --data '{
        "name": "datagen-products",
        "config": {
          "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
          "kafka.topic": "stream.products",
          "quickstart": "product",
          "key.converter": "org.apache.kafka.connect.json.JsonConverter",
          "key.converter.schemas.enable": "false",
          "value.converter": "org.apache.kafka.connect.json.JsonConverter",
          "value.converter.schemas.enable": "false",
          "max.interval": 1000,
          "iterations": 10000000,
          "tasks.max": "1",
          "transforms": "ValueToKey",
          "transforms.ValueToKey.type": "org.apache.kafka.connect.transforms.ValueToKey",
          "transforms.ValueToKey.fields": "id"
        }
    }' | jq
```

Create ArangoDB Sink connector:
```shell
curl --request POST \
    --url "http://localhost:18083/connectors" \
    --header 'content-type: application/json' \
    --data '{
        "name": "demo-arangodb-connector",
        "config": {
            "connector.class": "io.github.jaredpetersen.kafkaconnectarangodb.sink.ArangoDbSinkConnector",
            "tasks.max": "2",
            "topics": "stream.products",
            "arangodb.host": "172.28.0.1",
            "arangodb.port": 8529,
            "arangodb.user": "root",
            "arangodb.password": "test",
            "arangodb.database.name": "_system"
        }
    }' | jq
```

Observe documents in collections `products` in `_system` database:
```shell
curl -u root:test http://localhost:8529/_api/cursor -d '{"query":"FOR d IN products RETURN d"}' | jq
```

Check if any document is missing:
```AQL
LET count = (   
    FOR d IN products 
    COLLECT WITH COUNT INTO c
    RETURN c
)[0]

LET doc = (
    DOCUMENT(products, TO_STRING(count - 1))
)
RETURN {count, doc}
```


## Integration Tests

- standalone Kafka Cluster
- standalone Kafka Connect
```shell
mvn test
```

- external Kafka Cluster
- standalone Kafka Connect
```shell
mvn test -Dkafka.bootstrap.servers=127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292
```

- external Kafka Cluster
- external Kafka Connect
```shell
mvn test -Dkafka.bootstrap.servers=127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292 -Dkafka.connect.host=http://localhost:18083
```
