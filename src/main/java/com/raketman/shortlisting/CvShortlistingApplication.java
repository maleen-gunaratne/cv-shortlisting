package com.raketman.shortlisting;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@EnableBatchProcessing
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableTransactionManagement
@SpringBootApplication
public class CvShortlistingApplication {

	public static void main(String[] args) {
		SpringApplication.run(CvShortlistingApplication.class, args);
	}

}
