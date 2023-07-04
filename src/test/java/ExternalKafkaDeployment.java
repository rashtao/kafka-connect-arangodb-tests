import java.util.Objects;

enum ExternalKafkaDeployment implements KafkaDeployment {
    INSTANCE;

    ExternalKafkaDeployment() {
        Objects.requireNonNull(getBootstrapServers());
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
        return KafkaDeployment.getKafkaBootstrapServers();
    }

}
