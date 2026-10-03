package com.loanflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoanFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanFlowApplication.class, args);
    }

}
