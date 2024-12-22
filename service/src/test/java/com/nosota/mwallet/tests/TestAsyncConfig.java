package com.nosota.mwallet.tests;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@TestConfiguration
@EnableAsync
public class TestAsyncConfig {
    @Bean(name = "testTaskExecutor")
    public Executor taskExecutor() {
        return Executors.newFixedThreadPool(5);
    }
}
