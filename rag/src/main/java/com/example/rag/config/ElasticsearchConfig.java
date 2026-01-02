package com.example.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch配置类
 * 用于配置Elasticsearch客户端连接
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String[] elasticsearchUris;

    @Value("${spring.elasticsearch.username}")
    private String elasticsearchUsername;

    @Value("${spring.elasticsearch.password}")
    private String elasticsearchPassword;

    @Value("${spring.elasticsearch.connection-timeout:30s}")
    private String connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:30s}")
    private String socketTimeout;


    /**
     * 创建RestClient
     * @return RestClient实例
     */
    @Bean
    public RestClient restClient() {
        // 解析Elasticsearch地址
        List<HttpHost> httpHosts = Arrays.stream(elasticsearchUris)
                .map(HttpHost::create)
                .collect(Collectors.toList());
        
        // 设置认证信息
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));

        // 转换超时设置为毫秒
        int connectTimeoutMs = Integer.parseInt(connectionTimeout.replace("s", "")) * 1000;
        int socketTimeoutMs = Integer.parseInt(socketTimeout.replace("s", "")) * 1000;

        // 创建并返回RestClient
        return RestClient.builder(httpHosts.toArray(new HttpHost[0]))
                .setHttpClientConfigCallback(httpClientBuilder -> 
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                        .setDefaultRequestConfig(
                                        RequestConfig.custom()
                                                .setConnectTimeout(connectTimeoutMs)
                                                .setSocketTimeout(socketTimeoutMs)
                                                .build())
                                .setMaxConnTotal(100)
                                .setMaxConnPerRoute(100))
                .build();
    }

    /**
     * 创建ElasticsearchTransport
     * @param restClient RestClient实例
     * @return ElasticsearchTransport实例
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(
                restClient,
                new JacksonJsonpMapper());
    }

    /**
     * 创建ElasticsearchClient
     * @param elasticsearchTransport ElasticsearchTransport实例
     * @return ElasticsearchClient实例
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport elasticsearchTransport) {
        return new ElasticsearchClient(elasticsearchTransport);
    }
}