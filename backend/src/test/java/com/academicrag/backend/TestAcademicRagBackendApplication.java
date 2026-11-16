package com.academicrag.backend;

import org.springframework.boot.SpringApplication;

public class TestAcademicRagBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(AcademicRagBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
