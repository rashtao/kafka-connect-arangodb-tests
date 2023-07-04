/*
 * Copyright 2019 Aiven Oy and http-connector-for-apache-kafka project contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.kafka.common.utils.Time;
import org.apache.kafka.connect.connector.policy.AllConnectorClientConfigOverridePolicy;
import org.apache.kafka.connect.runtime.Connect;
import org.apache.kafka.connect.runtime.ConnectorConfig;
import org.apache.kafka.connect.runtime.Herder;
import org.apache.kafka.connect.runtime.Worker;
import org.apache.kafka.connect.runtime.isolation.Plugins;
import org.apache.kafka.connect.runtime.rest.RestServer;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;
import org.apache.kafka.connect.runtime.standalone.StandaloneConfig;
import org.apache.kafka.connect.runtime.standalone.StandaloneHerder;
import org.apache.kafka.connect.storage.MemoryOffsetBackingStore;
import org.apache.kafka.connect.util.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

final class KConnectStandalone implements KConnect {
    private static final Logger log = LoggerFactory.getLogger(KConnectStandalone.class);

    private final Path pluginDir;
    private final String bootstrapServers;

    private Herder herder;
    private Connect connect;

    KConnectStandalone(final Path pluginDir,
                       final String bootstrapServers) {
        this.pluginDir = pluginDir;
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public void start() {
        Map<String, String> workerProps = new HashMap<>();
        workerProps.put("bootstrap.servers", bootstrapServers);
        workerProps.put("plugin.path", pluginDir.toString());
        workerProps.put("offset.storage.file.filename", "");
        workerProps.put("key.converter", "org.apache.kafka.connect.json.JsonConverter");
        workerProps.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        workerProps.put("key.converter.schemas.enable", "false");
        workerProps.put("value.converter.schemas.enable", "false");

        Time time = Time.SYSTEM;
        String workerId = "test-worker";

        Plugins plugins = new Plugins(workerProps);
        StandaloneConfig config = new StandaloneConfig(workerProps);

        AllConnectorClientConfigOverridePolicy allConnectorClientConfigOverridePolicy =
                new AllConnectorClientConfigOverridePolicy();

        Worker worker = new Worker(
                workerId, time, plugins, config, new MemoryOffsetBackingStore(),
                allConnectorClientConfigOverridePolicy);
        herder = new StandaloneHerder(worker, "cluster-id", allConnectorClientConfigOverridePolicy);

        RestServer rest = new RestServer(config, null);
        rest.initializeServer();

        connect = new Connect(herder, rest);
        connect.start();
    }

    @Override
    public void stop() {
        connect.stop();
        connect.awaitStop();
    }

    @Override
    public void createConnector(final Map<String, String> config) {
        FutureCallback<Herder.Created<ConnectorInfo>> cb = new FutureCallback<>(
                (error, info) -> {
                    if (error != null) {
                        log.error("Failed to execute job");
                    } else {
                        log.info("Created connector {}", info.result().name());
                    }
                });
        herder.putConnectorConfig(
                config.get(ConnectorConfig.NAME_CONFIG),
                config, false, cb
        );
        awaitFuture(cb);
    }

    @Override
    public String getConnectorState(final String connectorName) {
        return herder.connectorStatus(connectorName).connector().state();
    }

    @Override
    public Collection<String> getConnectors() {
        return herder.connectors();
    }

    @Override
    public void deleteConnector(String name) {
        final FutureCallback<Herder.Created<ConnectorInfo>> cb = new FutureCallback<>(
                (error, info) -> {
                    if (error != null) {
                        log.error("Failed to execute job");
                    } else {
                        log.info("Deleted connector {}", name);
                    }
                });
        herder.deleteConnectorConfig(name, cb);
        awaitFuture(cb);
    }

    @Override
    public String toString() {
        return "standalone";
    }

    private void awaitFuture(FutureCallback<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
