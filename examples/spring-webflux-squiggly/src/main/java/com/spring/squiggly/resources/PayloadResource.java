package com.spring.squiggly.resources;

import com.spring.squiggly.domain.Payload;
import com.spring.squiggly.services.PayloadService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class PayloadResource {

  @NonNull
  private PayloadService service;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Flux<Payload> fetch(@RequestParam("fetchSize")
                                 long fetchSize) {
    return service.fetch(fetchSize);
  }

  @GetMapping(value = "/one")
  public Mono<Payload> getOne() {
    return service.findOne();
  }
}
