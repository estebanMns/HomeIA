package com.home.ia;

import org.springframework.boot.SpringApplication;

public class TestIaApplication {

	public static void main(String[] args) {
		SpringApplication.from(IaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
