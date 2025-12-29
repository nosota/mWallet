package com.nosota.mwallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MwalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(MwalletApplication.class, args);
    }
}
