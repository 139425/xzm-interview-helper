package com.xzm.xzm_interview_helper;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.xzm.xzm_interview_helper.mapper")
public class XzmInterviewHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(XzmInterviewHelperApplication.class, args);
    }

}
