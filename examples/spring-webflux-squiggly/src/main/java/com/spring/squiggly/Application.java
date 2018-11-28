package com.spring.squiggly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

//  @Bean
//  public Jackson2ObjectMapperFactoryBean factoryBean() {
//    return new Jackson2ObjectMapperFactoryBean();
//  }

}
