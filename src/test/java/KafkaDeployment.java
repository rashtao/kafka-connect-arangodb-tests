public interface KafkaDeployment {

    static KafkaDeployment getInstance() {
        String kafkaBootstrapServers = getKafkaBootstrapServers();
        if (kafkaBootstrapServers != null && !kafkaBootstrapServers.isEmpty()) {
            return ExternalKafkaDeployment.INSTANCE;
        } else {
            return StandaloneKafkaDeployment.INSTANCE;
        }
    }

    static String getKafkaBootstrapServers() {
        return System.getProperty("kafka.bootstrap.servers");
    }

    void start();

    void stop();

    String getBootstrapServers();
}
