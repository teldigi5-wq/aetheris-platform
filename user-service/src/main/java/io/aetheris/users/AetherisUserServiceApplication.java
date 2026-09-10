package io.aetheris.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class AetherisUserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AetherisUserServiceApplication.class, args);
    }
}
