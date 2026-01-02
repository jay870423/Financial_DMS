package com.example.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Web配置类
 * 用于配置Web相关的Bean，如RestTemplate等
 */
@Configuration
public class WebConfig {

    /**
     * 配置RestTemplate Bean
     * 供WechatLoginController等控制器使用
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}