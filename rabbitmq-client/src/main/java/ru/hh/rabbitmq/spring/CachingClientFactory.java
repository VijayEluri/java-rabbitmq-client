package ru.hh.rabbitmq.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import ru.hh.nab.metrics.StatsDSender;
import ru.hh.rabbitmq.spring.send.PublisherBuilder;
import ru.hh.rabbitmq.spring.send.SyncPublisherBuilder;
import static ru.hh.rabbitmq.spring.ConfigKeys.HOST;
import static ru.hh.rabbitmq.spring.ConfigKeys.HOSTS;
import static ru.hh.rabbitmq.spring.ConfigKeys.PUBLISHER_HOSTS;
import static ru.hh.rabbitmq.spring.ConfigKeys.RECEIVER_HOSTS;

/**
 * <p>
 * Create and configure {@link Receiver} and/or {@link PublisherBuilder}, reusing connection factories.
 * </p>
 * <p>
 * See {@link ConfigKeys} constants for configuration options.
 * </p>
 */
public class CachingClientFactory extends ConnectionsFactory {

  private final List<ConnectionFactory> receiverFactories;
  private final List<ConnectionFactory> publisherFactories;

  @Nullable
  protected final StatsDSender statsDSender;
  @Nullable
  protected final String serviceName;

  public CachingClientFactory(Properties properties, @Nullable String serviceName, @Nullable StatsDSender statsDSender, boolean sendStats) {
    this(properties, serviceName, sendStats ? statsDSender : null);
  }

  public CachingClientFactory(Properties properties, @Nullable String serviceName, @Nullable StatsDSender statsDSender) {
    super(properties);
    List<ConnectionFactory> generic = createConnectionFactories(false, HOSTS, HOST);
    receiverFactories = new ArrayList<>(generic);
    receiverFactories.addAll(createConnectionFactories(false, RECEIVER_HOSTS));

    publisherFactories = new ArrayList<>(generic);
    publisherFactories.addAll(createConnectionFactories(false, PUBLISHER_HOSTS));

    this.serviceName = serviceName;
    this.statsDSender = statsDSender;
  }

  public Receiver createReceiver(Properties properties) {
    if (receiverFactories.isEmpty()) {
      throw new ConfigException(String.format("Any of these properties must be set and not empty: %s",
        String.join(",", RECEIVER_HOSTS, HOSTS, HOST)));
    }
    return new Receiver(receiverFactories, properties, serviceName, statsDSender);
  }

  public PublisherBuilder createPublisherBuilder(Properties properties) {
    if (publisherFactories.isEmpty()) {
      throw new ConfigException(String.format("Any of these properties must be set and not empty: %s",
        String.join(",", PUBLISHER_HOSTS, HOSTS, HOST)));
    }
    return new PublisherBuilder(publisherFactories, properties, serviceName, statsDSender);
  }

  public SyncPublisherBuilder createSyncPublisherBuilder(Properties properties) {
    if (publisherFactories.isEmpty()) {
      throw new ConfigException(String.format("Any of these properties must be set and not empty: %s",
        String.join(",", PUBLISHER_HOSTS, HOSTS, HOST)));
    }
    return new SyncPublisherBuilder(publisherFactories, properties, serviceName, statsDSender);
  }
}
