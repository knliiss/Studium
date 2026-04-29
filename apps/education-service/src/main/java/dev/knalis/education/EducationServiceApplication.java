package dev.knalis.education;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EducationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EducationServiceApplication.class, args);
    }
    
}
