package io.rqueue.listener;

import static io.rqueue.constants.Constants.QUEUE_NAME;
import static io.rqueue.utils.ValueResolver.resolveToBoolean;
import static io.rqueue.utils.ValueResolver.resolveValueToArrayOfStrings;
import static io.rqueue.utils.ValueResolver.resolveValueToInteger;

import io.rqueue.annotation.RqueueListener;
import io.rqueue.converter.GenericMessageConverter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.annotation.support.AnnotationExceptionHandlerMethodResolver;
import org.springframework.messaging.handler.annotation.support.PayloadArgumentResolver;
import org.springframework.messaging.handler.invocation.AbstractExceptionHandlerMethodResolver;
import org.springframework.messaging.handler.invocation.AbstractMethodMessageHandler;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.util.CollectionUtils;
import org.springframework.util.comparator.ComparableComparator;

public class RqueueMessageHandler extends AbstractMethodMessageHandler<MappingInformation> {
  private List<MessageConverter> messageConverters;

  public RqueueMessageHandler() {
    this.messageConverters = new ArrayList<>();
    addDefaultMessageConverter();
  }

  public RqueueMessageHandler(List<MessageConverter> messageConverters) {
    setMessageConverters(messageConverters);
  }

  private void addDefaultMessageConverter() {
    this.messageConverters.add(new GenericMessageConverter());
  }

  @Override
  protected List<? extends HandlerMethodArgumentResolver> initArgumentResolvers() {
    List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(getCustomArgumentResolvers());
    CompositeMessageConverter compositeMessageConverter =
        new CompositeMessageConverter(getMessageConverters());
    resolvers.add(new PayloadArgumentResolver(compositeMessageConverter));
    return resolvers;
  }

  @Override
  protected List<? extends HandlerMethodReturnValueHandler> initReturnValueHandlers() {
    return new ArrayList<>(this.getCustomReturnValueHandlers());
  }

  @Override
  protected boolean isHandler(Class<?> beanType) {
    return true;
  }

  @Override
  protected MappingInformation getMappingForMethod(Method method, Class<?> handlerType) {
    RqueueListener rqueueListener = AnnotationUtils.findAnnotation(method, RqueueListener.class);
    if (rqueueListener != null) {
      MappingInformation mappingInformation =
          new MappingInformation(
              resolveQueueNames(rqueueListener.value()),
              resolveToBoolean(getApplicationContext(), rqueueListener.delayedQueue()),
              resolveValueToInteger(getApplicationContext(), rqueueListener.numRetries()),
              resolveDelayedQueue(rqueueListener.deadLaterQueue()));
      if (mappingInformation.isValid()) {
        return mappingInformation;
      }
      logger.warn("Queue '" + mappingInformation + "' not configured properly");
    }
    return null;
  }

  private String resolveDelayedQueue(String dlqName) {
    String[] resolvedValues = resolveValueToArrayOfStrings(getApplicationContext(), dlqName);
    if (resolvedValues.length == 1) {
      return resolvedValues[0];
    }
    throw new IllegalStateException(
        "more than one dead later queue can not be configure '" + dlqName + "'");
  }

  private Set<String> resolveQueueNames(String[] queueNames) {
    Set<String> result = new HashSet<>(queueNames.length);
    for (String queueName : queueNames) {
      result.addAll(
          Arrays.asList(resolveValueToArrayOfStrings(getApplicationContext(), queueName)));
    }
    return result;
  }

  @Override
  protected Set<String> getDirectLookupDestinations(MappingInformation mapping) {
    return mapping.getQueueNames();
  }

  @Override
  protected String getDestination(Message<?> message) {
    return (String) message.getHeaders().get(QUEUE_NAME);
  }

  @Override
  protected MappingInformation getMatchingMapping(MappingInformation mapping, Message<?> message) {
    if (mapping.getQueueNames().contains(getDestination(message))) {
      return mapping;
    }
    return null;
  }

  @Override
  protected Comparator<MappingInformation> getMappingComparator(Message<?> message) {
    return new ComparableComparator<>();
  }

  @Override
  protected AbstractExceptionHandlerMethodResolver createExceptionHandlerMethodResolverFor(
      Class<?> beanType) {
    return new AnnotationExceptionHandlerMethodResolver(beanType);
  }

  @Override
  protected void processHandlerMethodException(
      HandlerMethod handlerMethod, Exception ex, Message<?> message) {
    super.processHandlerMethodException(handlerMethod, ex, message);
    throw new MessagingException("An exception occurred while invoking the handler method", ex);
  }

  public List<MessageConverter> getMessageConverters() {
    return messageConverters;
  }

  public void setMessageConverters(List<MessageConverter> messageConverters) {
    if (CollectionUtils.isEmpty(messageConverters)) {
      throw new IllegalArgumentException("messageConverters list can not be empty or null");
    }
    this.messageConverters = messageConverters;
    addDefaultMessageConverter();
  }
}