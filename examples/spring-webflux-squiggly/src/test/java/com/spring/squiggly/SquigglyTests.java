package com.spring.squiggly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bohnman.squiggly.Squiggly;
import com.github.bohnman.squiggly.util.SquigglyUtils;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Test;

/**
 * [Description] <br/>
 * <b>Class</b>: SquigglyTests<br/>
 * <b>Copyright</b>: &copy; 2018 Banco de Cr&eacute;dito del Per&uacute;. <br/>
 * <b>Company</b>: Banco de Cr&eacute;dito del Per&uacute;. <br/>
 *
 * @author Banco de Cr&eacute;dito del Per&uacute;. (BCP) <br/>
 * <u>Service Provider</u>: Everis <br/>
 * <u>Developed by</ul>: <br/>
 * <ul>
 * <li>Jonathan Rodr&iacute;guez @S69409</li>
 * </ul>
 * <ul>
 * <li>dic 06, 2018 Creation class.</li>
 * </ul>
 */
@Slf4j
public class SquigglyTests {

  private static String json = "[" +
      "    {" +
      "        \"value\": 167," +
      "        \"message\": \"message number: 167\"," +
      "        \"extraData\": {" +
      "            \"extra\": \"extra: 334\"," +
      "            \"order\": 1" +
      "        }" +
      "    }," +
      "    {" +
      "        \"value\": 95," +
      "        \"message\": \"message number: 95\"," +
      "        \"extraData\": {" +
      "            \"extra\": \"extra: 285\"," +
      "            \"order\": 2" +
      "        }" +
      "    }" +
      "]";

  @Test
  public void test() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper = Squiggly.init(mapper, "value,extraData.extra");

    String result = SquigglyUtils
        .stringify(mapper, mapper
            .readValue(json.getBytes(), new TypeReference<Object>() {
            }));
    log.info("result: {}", result);
    Assert.assertFalse(result.contains("order"));
  }
}
