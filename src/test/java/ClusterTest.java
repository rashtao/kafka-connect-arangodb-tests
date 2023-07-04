import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sourcelab.kafka.connect.apiclient.Configuration;
import org.sourcelab.kafka.connect.apiclient.KafkaConnectClient;
import org.sourcelab.kafka.connect.apiclient.request.dto.NewConnectorDefinition;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


public class ClusterTest {

    private static final String ADB_HOST = "172.28.0.1";
    private static final int ADB_PORT = 8529;
    private static final String BOOTSTRAP_SERVERS_CONFIG = "127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292";
    private static final String CONNECTOR_NAME = "my-connector-standalone";
    private static final String CONNECTOR_CLASS = "io.github.jaredpetersen.kafkaconnectarangodb.sink.ArangoDbSinkConnector";

    private String topicName;
    private ArangoCollection col;
    private AdminClient adminClient;
    private KafkaProducer<JsonNode, JsonNode> producer;


    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        String colName = "col-" + UUID.randomUUID();
        topicName = "stream." + colName;

        col = new ArangoDB.Builder()
                .host(ADB_HOST, ADB_PORT)
                .password("test")
                .build()
                .db("_system")
                .collection(colName);
        if (col.exists()) {
            col.drop();
        }
        col.create();

        Properties adminClientConfig = new Properties();
        adminClientConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS_CONFIG);
        adminClient = AdminClient.create(adminClientConfig);
        adminClient.createTopics(Collections.singletonList(new NewTopic(topicName, 2, (short) 1))).all().get();

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS_CONFIG);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.connect.json.JsonSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.connect.json.JsonSerializer");
        producerProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");
        producer = new KafkaProducer<>(producerProps);
    }

    @AfterEach
    void tearDown() {
        col.db().arango().shutdown();
        adminClient.close();
        producer.close();
    }

    @Test
    @Timeout(30)
    void testBasicDelivery() throws ExecutionException, InterruptedException {
        KafkaConnectClient client = new KafkaConnectClient(new Configuration("http://localhost:18083"));
        if (client.getConnectors().contains(CONNECTOR_NAME)) {
            client.deleteConnector(CONNECTOR_NAME);
        }

        Map<String, String> config = new HashMap<>();
        config.put("name", CONNECTOR_NAME);
        config.put("connector.class", CONNECTOR_CLASS);
        config.put("topics", topicName);
        config.put("tasks.max", "2");
        config.put("arangodb.host", ADB_HOST);
        config.put("arangodb.port", String.valueOf(ADB_PORT));
        config.put("arangodb.user", "root");
        config.put("arangodb.password", "test");
        config.put("arangodb.database.name", "_system");

        client.addConnector(NewConnectorDefinition.newBuilder()
                .withName(CONNECTOR_NAME)
                .withConfig(config)
                .build());

        assertThat(client.getConnectors()).contains(CONNECTOR_NAME);
        assertThat(client.getConnectorStatus(CONNECTOR_NAME).getConnector().get("state"))
                .isEqualTo("RUNNING");

        assertThat(col.count().getCount()).isEqualTo(0L);

        for (int i = 0; i < 1_000; i++) {
            producer.send(new ProducerRecord<>(topicName,
                    JsonNodeFactory.instance.objectNode().put("id", "foo-" + i),
                    JsonNodeFactory.instance.objectNode().put("foo", "bar-" + i)
            )).get();
        }
        producer.flush();

        await("Request received by ADB")
                .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> col.count().getCount() >= 1_000L);

        client.deleteConnector(CONNECTOR_NAME);
        assertThat(client.getConnectors()).doesNotContain(CONNECTOR_NAME);
    }

}
