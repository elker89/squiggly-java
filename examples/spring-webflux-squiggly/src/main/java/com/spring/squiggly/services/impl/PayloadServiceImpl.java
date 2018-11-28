package com.spring.squiggly.services.impl;

import com.spring.squiggly.domain.ExtraData;
import com.spring.squiggly.domain.Payload;
import com.spring.squiggly.services.PayloadService;

import io.netty.util.internal.ThreadLocalRandom;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PayloadServiceImpl implements PayloadService {

  @Override
  public Flux<Payload> fetch(long quantity) {
    AtomicLong order = new AtomicLong(1);

    return Flux
        .create(sink -> {
          ThreadLocalRandom.current()
              .ints(quantity, 1, 500)
              .forEach(random -> sink
                  .next(Payload.build()
                      .value(random)
                      .message("message number: " + random)
                      .extraData(ExtraData.build()
                          .order(order.getAndIncrement())
                          .extra("extra: " + (order.get() * random))
                          .done())
                      .done()));
          sink.complete();
        });
  }

  @Override
  public Mono<Payload> findOne() {
    return Mono
        .fromCallable(() ->
            ThreadLocalRandom
                .current().nextInt(1, 500))
        .map(value -> Payload.build()
            .value(value)
            .message("message number: " + value)
            .extraData(ExtraData.build()
                .order(value)
                .extra("extra: " + value)
                .done())
            .done());

  }
}
