package com.arokkiyam.core.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Web layer configuration shared across all services.
 *
 * <p>Registers the GlobalExceptionHandler and configures a shared
 * Jackson ObjectMapper with the settings appropriate for a
 * healthcare API:
 * <ul>
 *   <li>All timestamps serialised as ISO-8601 strings (not Unix epoch).
 *       Readable in logs and audit trails.</li>
 *   <li>Unknown properties ignored on deserialization — allows
 *       adding new fields without breaking old clients.</li>
 *   <li>Nulls included — explicit nulls are meaningful in PATCH operations.</li>
 * </ul>
 */
@Configuration
public class WebConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // Write Instant/LocalDate as ISO strings not arrays
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Tolerate unknown fields from newer service versions
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Treat single values as arrays where arrays expected
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}