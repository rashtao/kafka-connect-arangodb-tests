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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


public class WriteTest {

    private static final String ADB_HOST = "172.28.0.1";
    private static final int ADB_PORT = 8529;
    private static final String BOOTSTRAP_SERVERS_CONFIG = "127.0.0.1:9092,127.0.0.1:9192,127.0.0.1:9292";
    private static final String CONNECTOR_NAME = "my-connector-standalone";
    private static final String CONNECTOR_CLASS = "io.github.jaredpetersen.kafkaconnectarangodb.sink.ArangoDbSinkConnector";

    private static final List<KConnect> K_CONNECTS = Arrays.asList(
            new KConnectStandalone(Paths.get("data/kafka-connect-arangodb/kafka-connect-arangodb-1.0.7.jar"), BOOTSTRAP_SERVERS_CONFIG),
            new KConnectCluster()
    );

    private String topicName;
    private ArangoCollection col;
    private AdminClient adminClient;
    private KafkaProducer<JsonNode, JsonNode> producer;

    @BeforeAll
    static void setUpAll() {
        K_CONNECTS.forEach(KConnect::start);
    }

    @AfterAll
    static void tearDownAll() {
        K_CONNECTS.forEach(KConnect::stop);
    }

    public static List<KConnect> kconnects() {
        return K_CONNECTS;
    }

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

    @Timeout(30)
    @ParameterizedTest //(name = "{index}")
    @MethodSource("kconnects")
    void testBasicDelivery(KConnect kConnect) throws ExecutionException, InterruptedException {
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

        kConnect.createConnector(config);
        assertThat(kConnect.getConnectors()).contains(CONNECTOR_NAME);
        assertThat(kConnect.getConnectorState(CONNECTOR_NAME)).isEqualTo("RUNNING");

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

        kConnect.deleteConnector(CONNECTOR_NAME);
        assertThat(kConnect.getConnectors()).doesNotContain(CONNECTOR_NAME);
    }

}
