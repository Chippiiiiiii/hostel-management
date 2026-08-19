package com.outpass.portal.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

/**
 * Environment Configuration
 * Loads environment variables from .env file
 * This runs early in the Spring Boot lifecycle
 */
@Configuration
public class EnvConfig {

    static {
        try {
            // Load .env file from the project root
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
            );

            System.out.println("Environment variables loaded from .env file");
        } catch (Exception e) {
            System.err.println("Warning: Could not load .env file. Using system environment variables.");
        }
    }
}



