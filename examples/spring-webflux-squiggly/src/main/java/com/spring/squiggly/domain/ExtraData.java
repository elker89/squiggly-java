package com.spring.squiggly.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderClassName = "Builder", builderMethodName = "build", buildMethodName = "done")
public class ExtraData {

  private String extra;
  private long order;
}
