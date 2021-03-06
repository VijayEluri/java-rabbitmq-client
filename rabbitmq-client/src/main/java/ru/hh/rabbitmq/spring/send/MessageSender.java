package ru.hh.rabbitmq.spring.send;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import ru.hh.nab.metrics.Counters;
import ru.hh.nab.metrics.StatsDSender;
import ru.hh.nab.metrics.Tag;

public class MessageSender {
  private final RabbitTemplate template;
  @Nullable
  private final Counters publishedCounters;
  @Nullable
  private final Counters errorsCounters;

  public MessageSender(RabbitTemplate template,
                @Nullable
                String serviceName,
                @Nullable
                StatsDSender statsDSender) {
    this.template = template;
    if (statsDSender != null) {
      publishedCounters = new Counters(20);
      errorsCounters = new Counters(20);

      statsDSender.sendPeriodically(() -> {
        statsDSender.sendCounters(serviceName + ".rabbit.publishers.messages", publishedCounters);
        statsDSender.sendCounters(serviceName + ".rabbit.publishers.errors", errorsCounters);
      });

    } else {
      publishedCounters = null;
      errorsCounters = null;
    }
  }

  public void publishMessages(Map<?, ? extends Destination> messages) {
    for (Map.Entry<?, ? extends Destination> entry : messages.entrySet()) {
      Object message = entry.getKey();
      Destination destination = entry.getValue();
      publishMessage(message, destination);
    }
  }

  public void publishMessage(Object message, Destination destination) {
    CorrelationData correlationData = null;
    if (message instanceof CorrelatedMessage) {
      CorrelatedMessage correlated = (CorrelatedMessage) message;
      correlationData = correlated.getCorrelationData();
      message = correlated.getMessage();
    }

    try {
      if (destination != null && destination.getRoutingKey() != null) {
          template.convertAndSend(destination.getExchange(), destination.getRoutingKey(), message, correlationData);
      } else {
          template.correlationConvertAndSend(message, correlationData);
      }
    } catch (AmqpException e) {
      if (errorsCounters != null) {
        addValueToCountersWithDestinationTag(errorsCounters, destination);
      }
      throw e;
    } finally {
      if (publishedCounters != null) {
        addValueToCountersWithDestinationTag(publishedCounters, destination);
      }
    }
  }

  RabbitTemplate getTemplate() {
    return template;
  }

  private static void addValueToCountersWithDestinationTag(Counters counters, Destination destination) {
    String routingKey = Optional.ofNullable(destination).map(Destination::getRoutingKey).orElse("unknown");
    counters.add(1, new Tag("routing_key", routingKey));
  }
}
