package com.udit.subscriptionmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SubscriptionmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubscriptionmanagerApplication.class, args);
	}

}
