# kafka-connect-arangodb-tests

Start db:
```shell
./start_db.sh
```

Create collections `airports` and `flights` in `_system` database:
```shell
curl -u root:test http://localhost:8529/_api/collection -d '{"name":"airports"}' | jq
curl -u root:test http://localhost:8529/_api/collection -d '{"name":"flights"}' | jq
```

Start Kafka cluster with Connect:
```shell
docker-compose up
```

Create connector:
```shell
curl --request POST \
    --url "http://127.0.0.1:18083/connectors" \
    --header 'content-type: application/json' \
    --data '{
        "name": "demo-arangodb-connector",
        "config": {
            "connector.class": "io.github.jaredpetersen.kafkaconnectarangodb.sink.ArangoDbSinkConnector",
            "tasks.max": "1",
            "topics": "stream.airports,stream.flights",
            "arangodb.host": "172.28.0.1",
            "arangodb.port": 8529,
            "arangodb.user": "root",
            "arangodb.password": "test",
            "arangodb.database.name": "_system"
        }
    }' | jq
```

Publish data to Kafka:
```shell
~/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092,localhost:9192,localhost:9292 --topic stream.airports --property "parse.key=true" --property "key.separator=|"
{"id":"PDX"}|{"airport":"Portland International Airport","city":"Portland","state":"OR","country":"USA","lat":45.58872222,"long":-122.5975}
{"id":"BOI"}|{"airport":"Boise Airport","city":"Boise","state":"ID","country":"USA","lat":43.56444444,"long":-116.2227778}
{"id":"HNL"}|{"airport":"Daniel K. Inouye International Airport","city":"Honolulu","state":"HI","country":"USA","lat":21.31869111,"long":-157.9224072}
{"id":"KOA"}|{"airport":"Ellison Onizuka Kona International Airport at Keāhole","city":"Kailua-Kona","state":"HI","country":"USA","lat":19.73876583,"long":-156.0456314}
```

Observe documents in collections `airports` and `flights` in `_system` database:
```shell
curl -u root:test http://localhost:8529/_api/cursor -d '{"query":"FOR d IN airports RETURN d"}' | jq
curl -u root:test http://localhost:8529/_api/cursor -d '{"query":"FOR d IN flights RETURN d"}' | jq
```
