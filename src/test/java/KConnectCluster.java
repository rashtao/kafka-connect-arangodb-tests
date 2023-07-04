import org.sourcelab.kafka.connect.apiclient.Configuration;
import org.sourcelab.kafka.connect.apiclient.KafkaConnectClient;
import org.sourcelab.kafka.connect.apiclient.request.dto.NewConnectorDefinition;

import java.util.Collection;
import java.util.Map;

public class KConnectCluster implements KConnect {
    private final static String KAFKA_CONNECT_HOST = "http://localhost:18083";
    private final KafkaConnectClient client = new KafkaConnectClient(new Configuration(KAFKA_CONNECT_HOST));

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }

    @Override
    public void createConnector(Map<String, String> config) {
        String name = config.get("name");
        if (name != null && client.getConnectors().contains(name)) {
            client.deleteConnector(name);
        }
        client.addConnector(NewConnectorDefinition.newBuilder()
                .withName(name)
                .withConfig(config)
                .build());
    }

    @Override
    public String getConnectorState(String name) {
        return client.getConnectorStatus(name).getConnector().get("state");
    }

    @Override
    public Collection<String> getConnectors() {
        return client.getConnectors();
    }

    @Override
    public void deleteConnector(String name) {
        client.deleteConnector(name);
    }

    @Override
    public String toString() {
        return "cluster";
    }

}
