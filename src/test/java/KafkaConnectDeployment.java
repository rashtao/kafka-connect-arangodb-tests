import java.util.Collection;
import java.util.Map;

public interface KafkaConnectDeployment {

    static KafkaConnectDeployment getInstance() {
        if (getKafkaConnectHost() != null) {
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
