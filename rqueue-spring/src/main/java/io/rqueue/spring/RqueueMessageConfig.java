package io.rqueue.spring;

import io.rqueue.config.SimpleRqueueListenerContainerFactory;
import io.rqueue.core.RqueueMessageTemplate;
import io.rqueue.listener.RqueueMessageHandler;
import io.rqueue.listener.RqueueMessageListenerContainer;
import io.rqueue.producer.RqueueMessageSender;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class RqueueMessageConfig {

  @Autowired(required = false)
  private final SimpleRqueueListenerContainerFactory simpleRqueueListenerContainerFactory =
      new SimpleRqueueListenerContainerFactory();

  @Autowired private BeanFactory beanFactory;

  private RqueueMessageTemplate getMessageTemplate(RedisConnectionFactory connectionFactory) {
    if (simpleRqueueListenerContainerFactory.getRqueueMessageTemplate() != null) {
      return simpleRqueueListenerContainerFactory.getRqueueMessageTemplate();
    }
    simpleRqueueListenerContainerFactory.setRqueueMessageTemplate(
        new RqueueMessageTemplate(connectionFactory));
    return simpleRqueueListenerContainerFactory.getRqueueMessageTemplate();
  }

  private RedisConnectionFactory getRedisConnectionFactory() {
    if (simpleRqueueListenerContainerFactory.getRedisConnectionFactory() == null) {
      simpleRqueueListenerContainerFactory.setRedisConnectionFactory(
          beanFactory.getBean(RedisConnectionFactory.class));
    }
    return simpleRqueueListenerContainerFactory.getRedisConnectionFactory();
  }

  @Bean
  public RqueueMessageHandler rqueueMessageHandler() {
    if (simpleRqueueListenerContainerFactory.getRqueueMessageHandler() != null) {
      return simpleRqueueListenerContainerFactory.getRqueueMessageHandler();
    }
    if (simpleRqueueListenerContainerFactory.getMessageConverters() != null) {
      return new RqueueMessageHandler(simpleRqueueListenerContainerFactory.getMessageConverters());
    }
    return new RqueueMessageHandler();
  }

  @Bean
  public RqueueMessageListenerContainer simpleMessageListenerContainer(
      RqueueMessageHandler rqueueMessageHandler) {
    if (simpleRqueueListenerContainerFactory.getRqueueMessageHandler() == null) {
      simpleRqueueListenerContainerFactory.setRqueueMessageHandler(rqueueMessageHandler);
    }
    if (simpleRqueueListenerContainerFactory.getRedisConnectionFactory() == null) {
      simpleRqueueListenerContainerFactory.setRedisConnectionFactory(getRedisConnectionFactory());
    }
    return simpleRqueueListenerContainerFactory.createMessageListenerContainer();
  }

  @Bean
  public RqueueMessageSender rqueueMessageSender() {
    return new RqueueMessageSender(getMessageTemplate(getRedisConnectionFactory()));
  }
}