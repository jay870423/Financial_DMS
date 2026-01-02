package com.example.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * Ollama客户端，用于与本地Ollama模型交互
 */

@Component
public class OllamaClient {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    
    private final String ollamaBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WebClient webClient; // 用于SSE的WebClient

    // 无参构造函数，默认使用localhost:11434
    public OllamaClient() {
        this("http://localhost:11434");
    }

    // 带参构造函数，支持自定义URL
    public OllamaClient(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        // 创建使用连接池的HttpClient
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)) // 连接超时时间
                .version(HttpClient.Version.HTTP_2) // 使用HTTP/2
                .followRedirects(HttpClient.Redirect.NORMAL) // 正常重定向
                // 注意：Java 11的HttpClient不直接支持设置连接池大小
                // 这些参数由底层实现管理，可以通过JVM参数调整
                .build(); // 构建客户端，内部会使用连接池
        this.objectMapper = new ObjectMapper();
        // 初始化WebClient用于SSE流式处理，优化配置
        this.webClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "text/event-stream")
                .defaultHeader("Connection", "keep-alive") // 保持连接，减少握手开销
                .defaultHeader("Cache-Control", "no-cache") // 避免缓存
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 增加缓冲大小
                .clientConnector(new ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofMinutes(5))
                                .keepAlive(true)
                                .resolver(spec -> spec.queryTimeout(Duration.ofSeconds(10)))
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                                .doOnConnected(conn -> conn
                                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(5 * 60))
                                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(5 * 60))
                                )
                ))
                .build();
    }

    // 生成嵌入向量
    public List<Double> generateEmbedding(String model, String text) throws IOException, InterruptedException {
        String url = ollamaBaseUrl + "/api/embeddings";
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", text                  // text 里可以任意字符
        );
        String requestBody = this.objectMapper.writeValueAsString(body);
        
        // 构建请求并设置响应超时时间
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofSeconds(60)) // 嵌入向量请求的响应超时时间
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());

        // 解析嵌入向量
        List<Double> embedding = new ArrayList<>();
        JsonNode embeddingNode = root.path("embedding");
        if (embeddingNode.isArray()) {
            for (JsonNode element : embeddingNode) {
                embedding.add(element.asDouble());
            }
        }
        return embedding;
    }

    // 生成聊天完成（非流式）
    public String generateChatCompletion(String model, List<Map<String, String>> messages) throws IOException, InterruptedException {
        String url = ollamaBaseUrl + "/api/chat";
        
        // 构建请求体
        Map<String, Object> requestData = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );
        String requestBody = objectMapper.writeValueAsString(requestData);

        // 构建请求并设置响应超时时间
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        
        return root.path("message").path("content").asText();
    }
    
    // 生成聊天完成（流式）- 原有实现，保持向后兼容
    public CompletableFuture<Void> generateChatCompletionStream(
            String model, 
            List<Map<String, String>> messages, 
            ResponseCallback callback) throws IOException, InterruptedException {
        String url = ollamaBaseUrl + "/api/chat";
        
        // 构建请求体，设置stream=true
        // 使用HashMap替代Map.of()以支持Java 8
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("model", model);
        requestData.put("messages", messages);
        requestData.put("stream", true);
        requestData.put("temperature", 0.0);
        requestData.put("top_p", 0.7);
        requestData.put("top_k", 40); // 限制考虑的词汇数量，提升速度
        requestData.put("num_predict", 400); // 减少生成的token数量
        requestData.put("repeat_penalty", 1.2); // 增加惩罚以避免重复思考
        requestData.put("repeat_last_n", 256); // 惩罚最近的token
        requestData.put("num_thread", 4); // 使用多线程处理
        requestData.put("seed", 42); // 固定随机种子，使结果更可预测
        requestData.put("keep_alive", "10m"); // 保持模型热状态10分钟
        String requestBody = objectMapper.writeValueAsString(requestData);

        // 构建请求，设置更长的超时时间（5分钟）以避免流式请求超时
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream") // 明确指定接收流式响应
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofMinutes(5)) // 流式请求设置5分钟超时时间
                .build();

        // 发送请求并处理流式响应
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    try {
                        // 确保响应状态码正确
                        if (response.statusCode() != 200) {
                            throw new IOException("Unexpected status code: " + response.statusCode());
                        }
                        
                        // 逐行处理响应
                        response.body().forEach(line -> {
                            try {
                                if (!line.isEmpty()) {
                                    // 解析每行JSON
                                    JsonNode root = objectMapper.readTree(line);
                                    
                                    // 提取content
                                    String content = root.path("message").path("content").asText();
                                    
                                    if (!content.isEmpty()) {
                                        // 立即发送内容，确保流式效果
                                        callback.onResponse(content);
                                    }
                                }
                            } catch (Exception e) {
                                // 记录错误但继续处理其他行
                                System.err.println("Error processing line: " + e.getMessage());
                            }
                        });
                        
                        // 通知完成
                        callback.onComplete();
                    } catch (Exception e) {
                        callback.onError(e);
                    }
                })
                .exceptionally(e -> {
                    // 处理异常情况
                    System.err.println("Stream request failed: " + e.getMessage());
                    callback.onError(new Exception("流式请求失败: " + e.getMessage(), e));
                    return null;
                });
    }
    
    // 生成聊天完成（流式）- 基于WebClient和SSE的新实现
    public CompletableFuture<Void> generateChatCompletionStreamSSE(
            String model, 
            List<Map<String, String>> messages, 
            ResponseCallback callback) {
        // 构建请求体，设置stream=true
        // 使用HashMap替代Map.of()以支持Java 8
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("model", model);
        requestData.put("messages", messages);
        requestData.put("stream", true);
        requestData.put("temperature", 0.0);
        requestData.put("top_p", 0.7);
        requestData.put("top_k", 40); // 限制考虑的词汇数量，提升速度
        requestData.put("num_predict", 400); // 减少生成的token数量
        requestData.put("repeat_penalty", 1.2); // 增加惩罚以避免重复思考
        requestData.put("repeat_last_n", 256); // 惩罚最近的token
        requestData.put("num_thread", 4); // 使用多线程处理
        requestData.put("seed", 42); // 固定随机种子，使结果更可预测
        requestData.put("keep_alive", "10m"); // 保持模型热状态10分钟
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        // 性能监控变量
        final long startTime = System.currentTimeMillis();
        final long[] tokenCount = {0};
        final long[] lastResponseTime = {startTime};
        
        try {
            // 使用WebClient发送请求并处理SSE响应
            webClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestData)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), 
                            response -> response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new IOException(
                                            "Ollama API错误 (" + response.statusCode() + "): " + body))))
                    .bodyToFlux(String.class) // 直接以字符串形式获取每一行
                    .timeout(Duration.ofMinutes(5)) // 设置5分钟超时
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)) // 重试机制：最多3次，初始间隔2秒
                            .filter(error -> error instanceof IOException || error instanceof TimeoutException)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .doOnSubscribe(subscription -> {
                        // 记录开始订阅
                        logger.info("[性能监控] 开始SSE流式订阅，模型: {}, 请求发送时间: {}", 
                                model, startTime);
                    })
                    .doOnError(error -> {
                        // 详细的错误记录
                        long duration = System.currentTimeMillis() - startTime;
                        String errorMsg = "SSE流处理错误: " + error.getMessage();
                        logger.error("[性能监控] {}，总耗时: {}ms, 已处理token: {}", 
                                errorMsg, duration, tokenCount[0]);
                        logger.error("[性能监控] 错误详情:", error);
                        
                        // 区分不同类型的错误
                        if (error instanceof TimeoutException) {
                            callback.onError(new Exception("SSE流式请求超时，请检查网络连接或Ollama服务状态", error));
                        } else if (error instanceof IOException) {
                            callback.onError(new Exception("SSE流式请求IO错误: " + error.getMessage(), error));
                        } else {
                            callback.onError(new Exception("SSE流式请求处理错误: " + error.getMessage(), error));
                        }
                    })
                    .subscribe(
                            line -> {
                                try {
                                    long currentTime = System.currentTimeMillis();
                                    
                                    if (!line.isEmpty()) {
                                        // 解析每行JSON
                                        JsonNode root = objectMapper.readTree(line);
                                        
                                        // 检查是否有错误
                                        if (root.has("error")) {
                                            String errorMsg = root.path("error").asText();
                                            logger.error("[性能监控] Ollama返回错误: {}, 已处理token: {}", 
                                                    errorMsg, tokenCount[0]);
                                            callback.onError(new Exception("Ollama模型错误: " + errorMsg));
                                            return;
                                        }
                                        
                                        // 提取content
                                        String content = root.path("message").path("content").asText();
                                        
                                        if (!content.isEmpty()) {
                                            // 统计token数量（粗略估计）
                                            int newTokens = content.length() / 4; // 粗略估计每个token长度
                                            tokenCount[0] += newTokens;
                                            
                                            // 计算间隔时间和处理速度
                                            long timeSinceLastResponse = currentTime - lastResponseTime[0];
                                            double tokensPerSecond = timeSinceLastResponse > 0 ? 
                                                    (newTokens * 1000.0 / timeSinceLastResponse) : 0;
                                            
                                            // 记录响应块处理信息
                                            if (tokenCount[0] % 10 == 0 || timeSinceLastResponse > 1000) {
                                                logger.info("[性能监控] 收到响应块，长度: {}字符, 估计{}tokens, 间隔: {}ms, 当前速度: {:.2f} tokens/sec, 累计: {}tokens",
                                                        content.length(), newTokens, timeSinceLastResponse, 
                                                        tokensPerSecond, tokenCount[0]);
                                            }
                                            
                                            // 更新最后响应时间
                                            lastResponseTime[0] = currentTime;
                                            
                                            // 立即发送内容，确保流式效果
                                            callback.onResponse(content);
                                        }
                                    }
                                } catch (Exception e) {
                                    // 记录错误但继续处理其他行
                                    logger.warn("[性能监控] 处理SSE响应行时出错: {}, 已处理token: {}", 
                                            e.getMessage(), tokenCount[0]);
                                    logger.debug("[性能监控] 错误详情:", e);
                                    // 不中断整个流处理，继续处理下一行
                                }
                            },
                            error -> {
                                // 订阅错误处理
                                long duration = System.currentTimeMillis() - startTime;
                                String errorMsg = "SSE订阅失败: " + error.getMessage();
                                logger.error("[性能监控] {}，总耗时: {}ms, 已处理token: {}", 
                                        errorMsg, duration, tokenCount[0]);
                                callback.onError(new Exception(errorMsg, error));
                                future.completeExceptionally(error);
                            },
                            () -> {
                                // 流完成时通知
                                long duration = System.currentTimeMillis() - startTime;
                                double avgSpeed = tokenCount[0] > 0 ? (tokenCount[0] * 1000.0 / duration) : 0;
                                logger.info("[性能监控] SSE流处理完成，总耗时: {}ms, 总token数: {}, 平均速度: {:.2f} tokens/sec",
                                        duration, tokenCount[0], avgSpeed);
                                callback.onComplete();
                                future.complete(null);
                            }
                    );
        } catch (Exception e) {
            // 请求设置错误处理
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = "SSE请求设置失败: " + e.getMessage();
            logger.error("[性能监控] {}，总耗时: {}ms", errorMsg, duration);
            logger.error("[性能监控] 错误详情:", e);
            callback.onError(new Exception(errorMsg, e));
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    // 响应回调接口
    public interface ResponseCallback {
        void onResponse(String content);
        void onComplete();
        void onError(Exception e);
    }
}