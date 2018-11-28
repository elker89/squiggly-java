package com.spring.squiggly.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bohnman.squiggly.Squiggly;
import com.github.bohnman.squiggly.util.SquigglyUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Component
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public final class JsonResponseFilter implements WebFilter {

  @Value("${squiggly.json-response-filter.paramName:fields}")
  private String queryParamName;

  @NonNull
  private ObjectMapper originalMapper;

  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return chain
        .filter(Optional
            .of(exchange.getRequest().getQueryParams())
            .map(queryParams -> queryParams.getFirst(queryParamName))
            .map(fields -> exchange
                .mutate()
                .response(new SquigglyServerResponseDecorator(exchange.getResponse(), fields, originalMapper))
                .build())
            .orElse(exchange));
  }

  private class SquigglyServerResponseDecorator extends ServerHttpResponseDecorator {

    private ObjectMapper objectMapper;

    private SquigglyServerResponseDecorator(ServerHttpResponse source,
                                            String fields,
                                            ObjectMapper objectMapper) {
      super(source);
      this.objectMapper = Squiggly.init(objectMapper, fields);
      log.trace("@@@ Applying json response filter [{}]", fields);
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return body instanceof Flux ?
          super.writeWith(((Flux) body)
              .compose(this.dataBufferOperator())) :
          super.writeWith(body);
    }

    private UnaryOperator<Flux<DataBuffer>> dataBufferOperator() {

      return (source) -> source
          .map(this::dataBufferAsByteArray)
          .map((buffer) -> super.bufferFactory()
              .wrap(this.applySquigglyFilter(buffer, objectMapper)));
    }

    private byte[] dataBufferAsByteArray(DataBuffer buffer) {
      byte[] bytes = new byte[buffer.readableByteCount()];
      buffer.read(bytes);
      return bytes;
    }

    private byte[] applySquigglyFilter(byte[] input, ObjectMapper mapper) {
      try {
        return SquigglyUtils
            .stringify(mapper, mapper
                .readValue(input, Object.class))
            .getBytes(StandardCharsets.UTF_8);
      } catch (Throwable var4) {
        JsonResponseFilter.log.trace("@@@ Error applying json response filter...", var4);
        return input;
      }
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return body instanceof Flux ?
          super.writeAndFlushWith(((Flux) body)
              .flatMap(Function.identity())
              .compose(this.dataBufferOperator())
              .map(Flux::just)) :
          super.writeAndFlushWith(body);
    }
  }
}

