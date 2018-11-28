package com.spring.squiggly.resources;

import com.spring.squiggly.filters.JsonResponseFilter;
import com.spring.squiggly.services.impl.PayloadServiceImpl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

@RunWith(SpringRunner.class)
@WebFluxTest
@ContextConfiguration(classes = {
    PayloadResource.class,
    PayloadServiceImpl.class
    , Jackson2ObjectMapperFactoryBean.class
    , JsonResponseFilter.class
})
public class PayloadResourceTests {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  public void whenInvokeWithFilters_shouldRetrieveSpecificFields() {

    webTestClient.get()
        .uri("/data/one?fields=value,message")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.value", "$.message").isNotEmpty()
        .jsonPath("$.extraData").doesNotExist();

    whenInvokeWithoutFilters_shouldRetrieveAllFields();
  }

  @Test
  public void whenInvokeWithoutFilters_shouldRetrieveAllFields() {

    webTestClient.get()
        .uri("/data/one")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.value", "$.message").isNotEmpty()
        .jsonPath("$.extraData").exists();

  }
}
