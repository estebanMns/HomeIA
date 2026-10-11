package com.homeia;

import com.homeia.config.HomeIaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(HomeIaProperties.class)
public class HomeIaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeIaApplication.class, args);
    }
}
