package com.campus.canteen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.campus.canteen.mapper")
@EnableScheduling
public class CanteenApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanteenApplication.class, args);
    }
}
