package com.marlabs.desk;

import com.marlabs.desk.engine.FakePolicyEngineClient;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestEngineConfig {

    @Bean
    @Primary
    FakePolicyEngineClient fakePolicyEngineClient() {
        return new FakePolicyEngineClient();
    }
}
