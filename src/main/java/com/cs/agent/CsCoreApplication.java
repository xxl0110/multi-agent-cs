package com.cs.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CsCoreApplication {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CsCoreApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CsCoreApplication.class, args);
    }
}
