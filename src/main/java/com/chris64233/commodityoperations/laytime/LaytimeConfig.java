package com.chris64233.commodityoperations.laytime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LaytimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
