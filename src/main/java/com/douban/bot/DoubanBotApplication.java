package com.douban.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DoubanBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoubanBotApplication.class, args);
    }
}
