package com.rapidtutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RapidtutorApplication {

	public static void main(String[] args) {
		SpringApplication.run(RapidtutorApplication.class, args);
	}

}
