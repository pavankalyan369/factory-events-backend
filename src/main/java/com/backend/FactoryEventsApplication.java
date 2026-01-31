package com.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.backend.entity")
@EnableJpaRepositories(basePackages = "com.backend.repository")
public class FactoryEventsApplication {
	public static void main(String[] args) {
		SpringApplication.run(FactoryEventsApplication.class, args);
	}
}
