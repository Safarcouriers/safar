package com.saffaricarrers.saffaricarrers;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaRepositories
@EntityScan
@EnableScheduling
@ComponentScan(basePackages = "com.saffaricarrers.saffaricarrers")
public class SaffaricarrersApplication {

	public static void main(String[] args) {

		// ============================================================
		// FORCE APPLICATION TIMEZONE TO INDIA
		// ============================================================
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

		System.setProperty("user.timezone", "Asia/Kolkata");

		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();

		dotenv.entries().forEach(entry ->
				System.setProperty(
						entry.getKey(),
						entry.getValue()
				)
		);

		SpringApplication.run(
				SaffaricarrersApplication.class,
				args
		);
	}
}