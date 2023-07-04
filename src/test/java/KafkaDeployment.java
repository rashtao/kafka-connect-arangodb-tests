public interface KafkaDeployment {

    static KafkaDeployment getInstance() {
        if (getKafkaBootstrapServers() != null) {
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
