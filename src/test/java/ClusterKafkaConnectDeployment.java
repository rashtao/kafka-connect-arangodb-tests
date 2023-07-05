import org.sourcelab.kafka.connect.apiclient.Configuration;
import org.sourcelab.kafka.connect.apiclient.KafkaConnectClient;
import org.sourcelab.kafka.connect.apiclient.request.dto.NewConnectorDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

enum ClusterKafkaConnectDeployment implements KafkaConnectDeployment {
    INSTANCE;

    private final String kafkaBootstrapServers;
    private final KafkaConnectClient client;

    ClusterKafkaConnectDeployment() {
        kafkaBootstrapServers = KafkaDeployment.getKafkaBootstrapServers();
        Objects.requireNonNull(kafkaBootstrapServers);
        assert !kafkaBootstrapServers.isEmpty();
        String kafkaConnectHost = KafkaConnectDeployment.getKafkaConnectHost();
        Objects.requireNonNull(kafkaConnectHost);
        assert !kafkaConnectHost.isEmpty();
        client = new KafkaConnectClient(new Configuration(kafkaConnectHost));
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }

    @Override
    public String getBootstrapServers() {
        return kafkaBootstrapServers;
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

}
