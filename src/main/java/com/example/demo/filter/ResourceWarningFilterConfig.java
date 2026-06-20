package com.example.demo.filter;

import java.util.Collections;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * web.xml 대신 Spring Boot 방식으로 ResourceWarningFilter를 등록합니다.
 */
@Configuration
public class ResourceWarningFilterConfig {
    private static final String RESOURCE_WARNING_THRESHOLD_PERCENT = "90";

    @Bean
    public FilterRegistrationBean<ResourceWarningFilter> resourceWarningFilter() {
        FilterRegistrationBean<ResourceWarningFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ResourceWarningFilter());
        registrationBean.setName("resourceWarningFilter");
        registrationBean.setUrlPatterns(Collections.singletonList("/*"));
        registrationBean.addInitParameter(
            ResourceWarningFilter.THRESHOLD_INIT_PARAM,
            RESOURCE_WARNING_THRESHOLD_PERCENT
        );
        return registrationBean;
    }
}
