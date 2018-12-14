package com.spring.squiggly.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bohnman.squiggly.Squiggly;
import com.github.bohnman.squiggly.context.provider.AbstractSquigglyContextProvider;
import com.github.bohnman.squiggly.util.SquigglyUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
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
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;


@Component
@Slf4j
@SuppressWarnings("unchecked")
public final class JsonResponseFilter implements WebFilter {

    private static final ThreadLocal<String> FILTER_HOLDER = new ThreadLocal<>();

    private String queryParamName;
    private ObjectMapper originalMapper;

    @Autowired
    public JsonResponseFilter(
            @Value("${squiggly.json-response-filter.paramName:fields}")
                    String queryParamName,
            ObjectMapper objectMapper) {
        this.queryParamName = queryParamName;
        this.originalMapper = Squiggly.init(objectMapper, new ContextProvider());
    }

    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain
                .filter(Optional
                        .of(exchange.getRequest().getQueryParams())
                        .map(queryParams -> queryParams.getFirst(queryParamName))
                        .map(fields -> exchange
                                .mutate()
                                .response(new SquigglyServerResponseDecorator(
                                        exchange.getResponse(), fields))
                                .build())
                        .orElse(exchange));
    }

    private class SquigglyServerResponseDecorator extends ServerHttpResponseDecorator {

        private final String fields;

        private SquigglyServerResponseDecorator(ServerHttpResponse source,
                                                String fields) {
            super(source);
            this.fields = fields;
            log.trace("@@@ Applying json response filter [{}]", fields);
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return body instanceof Flux ?
                    super.writeWith(((Flux) body)
                            .compose(dataBufferOperator())) :
                    super.writeWith(body);
        }

        private UnaryOperator<Flux<DataBuffer>> dataBufferOperator() {
            return (source) -> source
                    .publishOn(Schedulers.parallel())
                    .map((buffer) -> bufferFactory()
                            .wrap(applySquigglyFilter(buffer)));
        }


        private byte[] applySquigglyFilter(DataBuffer buffer) {
            try {
                FILTER_HOLDER.set(fields);
                ObjectMapper mapper = originalMapper;
                return SquigglyUtils
                        .stringify(mapper, mapper
                                .readValue(buffer.asInputStream(), Object.class))
                        .getBytes(StandardCharsets.UTF_8);
            } catch (Throwable ex) {
                JsonResponseFilter.log.trace("@@@ Error applying json response filter...", ex);

                byte[] original = new byte[buffer.readableByteCount()];
                buffer.read(original);
                return original;
            } finally {
                FILTER_HOLDER.remove();
            }
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return body instanceof Flux ?
                    super.writeAndFlushWith(((Flux) body)
                            .flatMap(Function.identity())
                            .compose(dataBufferOperator())
                            .map(Flux::just)) :
                    super.writeAndFlushWith(body);
        }
    }

    private class ContextProvider extends AbstractSquigglyContextProvider {

        @Override
        public boolean isFilteringEnabled() {
            return Objects.nonNull(FILTER_HOLDER.get());
        }

        @Override
        protected String getFilter(Class beanClass) {
            return FILTER_HOLDER.get();
        }
    }
}

