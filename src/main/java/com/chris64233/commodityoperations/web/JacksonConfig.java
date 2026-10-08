package com.chris64233.commodityoperations.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class JacksonConfig {

    /**
     * BigDecimal 以 toPlainString 输出，避免科学计数法；数值类型保持 JSON number。
     */
    @Bean
    public SimpleModule plainBigDecimalModule() {
        SimpleModule module = new SimpleModule("plain-bigdecimal");
        module.addSerializer(BigDecimal.class, new PlainBigDecimalSerializer());
        return module;
    }

    private static class PlainBigDecimalSerializer extends ValueSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen,
                              SerializationContext provider) {
            gen.writeNumber(value.stripTrailingZeros().toPlainString());
        }
    }
}
