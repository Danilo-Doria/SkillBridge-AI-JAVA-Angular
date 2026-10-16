package com.riwi.skillbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class SkillBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillBridgeApplication.class, args);
    }
}
