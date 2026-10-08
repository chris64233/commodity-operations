package com.chris64233.commodityoperations.pricing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PricingConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
