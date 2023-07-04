import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

enum StandaloneKafkaDeployment implements KafkaDeployment {
    INSTANCE;

    private final SharedKafkaTestResource sharedKafkaTestResource = new SharedKafkaTestResource();

    @Override
    public void start() {
        try {
            sharedKafkaTestResource.beforeAll(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        sharedKafkaTestResource.afterAll(null);
    }

    @Override
    public String getBootstrapServers() {
        return sharedKafkaTestResource.getKafkaConnectString();
    }
}
