package com.wsi.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.wsi.platform.mapper")
public class WsiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(WsiPlatformApplication.class, args);
    }
}
