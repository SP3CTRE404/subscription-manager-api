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

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner dropCheckConstraint(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_billing_cycle_check");
				System.out.println("Dropped subscriptions_billing_cycle_check constraint successfully.");
			} catch (Exception e) {
				// Constraint might not exist or already dropped, ignore
			}
			try {
				jdbcTemplate.execute("ALTER TABLE subscriptions ALTER COLUMN next_billing_date DROP NOT NULL");
				System.out.println("Dropped NOT NULL constraint on next_billing_date successfully.");
			} catch (Exception e) {
				// Constraint might not exist or already dropped, ignore
			}
		};
	}
}