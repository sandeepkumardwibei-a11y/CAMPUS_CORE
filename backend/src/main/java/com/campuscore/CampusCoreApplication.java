package com.campuscore;
 
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
 
@SpringBootApplication
@EnableTransactionManagement 
@EnableJpaRepositories(basePackages = "com.campuscore.repository") 
public class CampusCoreApplication {
 
    public static void main(String[] args) {
        SpringApplication.run(CampusCoreApplication.class, args);
    }
}