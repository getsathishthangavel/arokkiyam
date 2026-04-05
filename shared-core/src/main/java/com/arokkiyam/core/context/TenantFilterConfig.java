package com.arokkiyam.core.context;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link TenantFilter} with the servlet container.
 *
 * <p>Explicit FilterRegistrationBean ensures the filter is registered
 * with Order 1 (before all other filters) and mapped to all URL patterns.
 */
@Configuration
public class TenantFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(
            TenantFilter tenantFilter
    ) {
        FilterRegistrationBean<TenantFilter> registration =
            new FilterRegistrationBean<>(tenantFilter);

        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("tenantFilter");

        return registration;
    }
}