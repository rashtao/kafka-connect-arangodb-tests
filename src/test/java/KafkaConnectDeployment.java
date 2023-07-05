import java.util.Collection;
import java.util.Map;

public interface KafkaConnectDeployment {

    static KafkaConnectDeployment getInstance() {
        String kafkaConnectHost = getKafkaConnectHost();
        if (kafkaConnectHost != null && !kafkaConnectHost.isEmpty()) {
            return ClusterKafkaConnectDeployment.INSTANCE;
        } else {
            return StandaloneKafkaConnectDeployment.INSTANCE;
        }
    }

    static String getKafkaConnectHost() {
        return System.getProperty("kafka.connect.host");
    }


    void start();

    void stop();

    String getBootstrapServers();

    void createConnector(Map<String, String> config);

    String getConnectorState(String name);

    Collection<String> getConnectors();

    void deleteConnector(String name);
}
