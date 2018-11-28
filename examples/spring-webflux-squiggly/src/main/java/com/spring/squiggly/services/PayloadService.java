package com.spring.squiggly.services;

import com.spring.squiggly.domain.Payload;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * [Description] <br/>
 * <b>Class</b>: PayloadService<br/>
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
 * <li>nov 26, 2018 Creation class.</li>
 * </ul>
 */
public interface PayloadService {

  Flux<Payload> fetch(long quantity);

  Mono<Payload> findOne();
}
