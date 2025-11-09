package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hello {
    private static final Logger logger = LoggerFactory.getLogger(Hello.class);

    public static void main(String[] args) {
        logger.info("Hello, SonarQube!");
    }

    public int add(int a, int b) {
        return a + b;
    }
}

