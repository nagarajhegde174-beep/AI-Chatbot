package com.nexaai.user;

import com.nexaai.user.config.UserProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * User Service.
 *
 * <p>An independent Spring Boot application. It shares no code, no parent POM and no database
 * with any other NexaAI service.
 *
 * <p><strong>It does not need Auth Service to be running.</strong> It verifies the tokens Auth
 * Service issued, using only the public key, and it learns that accounts exist from an event.
 * Neither is a call into Auth Service, and neither requires reading Auth Service's database.
 * If Auth Service is down, this service keeps serving every request except the ones that
 * actually need a fresh login.
 */
@SpringBootApplication
@EnableConfigurationProperties(UserProperties.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}