package ru.hh.rabbitmq.spring;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import static java.util.stream.Collectors.toList;
import static org.springframework.util.StringUtils.hasText;
import static ru.hh.rabbitmq.spring.ConfigKeys.AUTOMATIC_RECOVERY;
import static ru.hh.rabbitmq.spring.ConfigKeys.CHANNEL_CACHE_SIZE;
import static ru.hh.rabbitmq.spring.ConfigKeys.CLOSE_TIMEOUT;
import static ru.hh.rabbitmq.spring.ConfigKeys.CONNECTION_TIMEOUT_MS;
import static ru.hh.rabbitmq.spring.ConfigKeys.HEARTBIT_SEC;
import static ru.hh.rabbitmq.spring.ConfigKeys.HOSTS_PORT_SEPARATOR_PATTERN;
import static ru.hh.rabbitmq.spring.ConfigKeys.HOSTS_SEPARATOR_PATTERN;
import static ru.hh.rabbitmq.spring.ConfigKeys.PASSWORD;
import static ru.hh.rabbitmq.spring.ConfigKeys.PORT;
import static ru.hh.rabbitmq.spring.ConfigKeys.PUBLISHER_CONFIRMS;
import static ru.hh.rabbitmq.spring.ConfigKeys.PUBLISHER_RETURNS;
import static ru.hh.rabbitmq.spring.ConfigKeys.RECREATE_CONNECTION_INTERVAL_MINUTES;
import static ru.hh.rabbitmq.spring.ConfigKeys.TOPOLOGY_RECOVERY;
import static ru.hh.rabbitmq.spring.ConfigKeys.USERNAME;
import static ru.hh.rabbitmq.spring.ConfigKeys.VIRTUALHOST;

public class ConnectionsFactory {

  protected final PropertiesHelper properties;

  public ConnectionsFactory(Properties properties) {
    this.properties = new PropertiesHelper(properties);
  }

  public List<ConnectionFactory> createConnectionFactories(boolean throwOnEmpty, String... hostsSettingNames) {
    return createConnectionFactories(properties, throwOnEmpty, hostsSettingNames);
  }

  private static List<ConnectionFactory> createConnectionFactories(PropertiesHelper properties, boolean throwOnEmpty, String... hostsSettingNames) {
    try {
      Integer commonPort = properties.getInteger(PORT);
      // something_HOSTS -> HOSTS -> HOST -> exception
      return getHosts(properties, throwOnEmpty, hostsSettingNames).map(hostAndPortString -> {
        String[] hostAndPort = HOSTS_PORT_SEPARATOR_PATTERN.split(hostAndPortString);
        String host = hostAndPort[0];
        Integer port = commonPort;
        if (hostAndPort.length > 1) {
          port = Integer.parseInt(hostAndPort[1]);
        }
        return createConnectionFactory(properties, host, port);
      }).collect(toList());
    }
    catch (ConfigException e) {
      throw e;
    }
    catch (RuntimeException e) {
      throw new ConfigException("Failed to create connection factories", e);
    }
  }

  private static Stream<String> getHosts(PropertiesHelper properties, boolean throwOnEmpty, String... settingNames) {
    String value;
    for (String settingName : settingNames) {
      value = !hasText(settingName) ? null : properties.getString(settingName);
      if (hasText(value)) {
        return HOSTS_SEPARATOR_PATTERN.splitAsStream(value);
      }
    }
    if (throwOnEmpty) {
      throw new ConfigException(String.format("Any of these properties must be set and not empty: %s", String.join(",", settingNames)));
    }
    return Stream.empty();
  }

  private static ConnectionFactory createConnectionFactory(PropertiesHelper properties, String host, Integer port) {
    try {
      com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory = new RefreshingConnectionFactory(
        Duration.ofMinutes(properties.getLong(RECREATE_CONNECTION_INTERVAL_MINUTES, 2L))
      );
      rabbitConnectionFactory.load(properties.getProperties());
      rabbitConnectionFactory.setAutomaticRecoveryEnabled(properties.getBoolean(AUTOMATIC_RECOVERY, false));
      rabbitConnectionFactory.setTopologyRecoveryEnabled(properties.getBoolean(TOPOLOGY_RECOVERY, false));

      CachingConnectionFactory factory = new CachingConnectionFactory(rabbitConnectionFactory);

      factory.setHost(hasText(host) ? host : "localhost");
      factory.setPort(port == null ? com.rabbitmq.client.ConnectionFactory.DEFAULT_AMQP_PORT : port);

      factory.setConnectionTimeout(properties.getInteger(CONNECTION_TIMEOUT_MS, 200));

      factory.setUsername(properties.getNotNullString(USERNAME));
      factory.setPassword(properties.getNotNullString(PASSWORD));
      String virtualhost = properties.getString(VIRTUALHOST);
      if (virtualhost != null) {
        factory.setVirtualHost(virtualhost);
      }

      factory.setRequestedHeartBeat(properties.getInteger(HEARTBIT_SEC, 2));

      Integer channelCacheSize = properties.getInteger(CHANNEL_CACHE_SIZE);
      if (channelCacheSize != null) {
        factory.setChannelCacheSize(channelCacheSize);
      }
      Integer closeTimeout = properties.getInteger(CLOSE_TIMEOUT);
      if (closeTimeout != null) {
        factory.setCloseTimeout(closeTimeout);
      }
      Boolean publisherConfirms = properties.getBoolean(PUBLISHER_CONFIRMS);
      if (publisherConfirms != null) {
        factory.setPublisherConfirms(publisherConfirms);
      }
      Boolean publisherReturns = properties.getBoolean(PUBLISHER_RETURNS);
      if (publisherReturns != null) {
        factory.setPublisherReturns(publisherReturns);
      }
      return factory;
    }
    catch (RuntimeException e) {
      throw new ConfigException(String.format("Failed to create ConnectionFactory (%s)", host), e);
    }
  }
}
