package com.monetka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MonetkaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonetkaApplication.class, args);
    }
}
