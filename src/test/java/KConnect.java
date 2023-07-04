import java.util.Collection;
import java.util.Map;

public interface KConnect {
    void start();

    void stop();

    void createConnector(Map<String, String> config);

    String getConnectorState(String name);

    Collection<String> getConnectors();

    void deleteConnector(String name);

    @Override
    String toString();
}
