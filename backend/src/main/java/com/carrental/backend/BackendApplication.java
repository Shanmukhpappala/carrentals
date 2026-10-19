package com.carrental.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.carrental.backend.config.ProductionConfigurationValidator;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(BackendApplication.class);
		application.addInitializers(new ProductionConfigurationValidator());
		application.run(args);
	}

}
