package com.longtoast.bilbil_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BilbilApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BilbilApiApplication.class, args);

	}

}
