package com.pjjunio.esquentaio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EsquentaioApplication {

	public static void main(String[] args) {
		SpringApplication.run(EsquentaioApplication.class, args);
	}

}
