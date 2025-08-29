package com.raketman.shortlisting.config;


import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
@EnableBatchProcessing
public class BatchDatabaseConfiguration {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void initializeSchema() {
        try {
            // Try to create Spring Batch tables manually if they don't exist
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            // Check if BATCH_JOB_INSTANCE table exists
            try {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE", Integer.class);
                System.out.println("Spring Batch tables already exist.");
            } catch (Exception e) {
                System.out.println("Creating Spring Batch tables...");

                // Initialize the schema using Spring's ResourceDatabasePopulator
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
                populator.setContinueOnError(false);
                populator.execute(dataSource);

                System.out.println("Spring Batch tables created successfully.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing Spring Batch schema: " + e.getMessage());
            throw new RuntimeException("Failed to initialize Spring Batch schema", e);
        }
    }
}
