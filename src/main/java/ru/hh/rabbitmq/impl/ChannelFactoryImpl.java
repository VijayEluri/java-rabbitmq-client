package ru.hh.rabbitmq.impl;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.rabbitmq.ChannelFactory;
import ru.hh.rabbitmq.ConnectionFactory;
import ru.hh.rabbitmq.ConnectionFailedException;

// TODO merge with SingleConnectionFactory
public class ChannelFactoryImpl implements ChannelFactory {
  private static final Logger logger = LoggerFactory.getLogger(ChannelFactoryImpl.class);

  private ConnectionFactory connectionFactory;
  
  public ChannelFactoryImpl(ConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public Channel getChannel() {
    logger.debug("Opening channel");
    try {
      return connectionFactory.getConnection().createChannel();
    } catch (IOException e) {
      throw new ConnectionFailedException("failed to create channel", e);
    }
  }

  public void returnChannel(Channel channel) {
    if (channel != null && channel.isOpen()) {
      try {
        logger.debug("Closing channel");
        channel.close();
      } catch (IOException e) {
        logger.warn("Error while closing channel, ignoring", e);
      }
    }
    if (channel != null) {
      connectionFactory.returnConnection(channel.getConnection());
    }
  }

  public void close() {
    // nothing to do
  }
}
