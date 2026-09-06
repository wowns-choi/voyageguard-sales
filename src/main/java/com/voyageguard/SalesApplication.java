package com.voyageguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // SalesOutboxRelay/WaitlistService/ReservationService의 @Scheduled 폴링을 위해 필요
public class SalesApplication {
	public static void main(String[] args) {
		SpringApplication.run(SalesApplication.class, args);
	}
}
