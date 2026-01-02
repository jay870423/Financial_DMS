package com.example.rag.service;
import com.example.rag.client.OllamaClient;
import com.example.rag.model.DocumentChunk;
import com.example.rag.service.ElasticsearchService;
import com.example.rag.service.VectorEmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * RAG服务类，处理文档上传、查询和向量检索
 */

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    private final ElasticsearchService elasticsearchService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final OllamaClient ollamaClient;
    private final Tika tika;
    private final String model;
    private final ObjectMapper objectMapper;
    
    // 用于存储文件ID和文件详情的映射关系
    private final Map<String, Map<String, Object>> fileMappings = new ConcurrentHashMap<>();

    @Autowired
    public RagService(ElasticsearchService elasticsearchService, VectorEmbeddingService vectorEmbeddingService, 
                     OllamaClient ollamaClient, Tika tika,
                     @Value("${spring.ai.openai.chat.options.model:qwen2.5:1.5b}") String model,
                     ObjectMapper objectMapper) {
        this.elasticsearchService = elasticsearchService;
        this.vectorEmbeddingService = vectorEmbeddingService;
        this.ollamaClient = ollamaClient;
        this.tika = tika;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    // 添加文档到向量存储
    public void addDocument(File file) throws IOException, org.apache.tika.exception.TikaException {
        logger.info("开始解析文件: {}", file.getName());
        // 使用Tika解析文档内容
        String content = tika.parseToString(file);
        logger.info("文件解析完成，内容长度: {} 字符", content.length());
        logger.debug("解析内容前100个字符: {}", content.length() > 100 ? content.substring(0, 100) : content);
        
        // 将文档分割成块
        List<String> chunks = splitDocumentIntoChunks(content, 1000, 200);
        
        // 生成文件ID
        String fileId = "file-" + UUID.randomUUID().toString();
        
        // 创建DocumentChunk对象列表并添加到Elasticsearch
        List<DocumentChunk> documentChunks = IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    String chunkText = chunks.get(index);
                    int chunkSeq = index;
                    List<Double> embedding = vectorEmbeddingService.generateEmbedding(chunkText);
                    
                    // 生成唯一的chunk ID
                    String chunkId = "chunk-" + UUID.randomUUID().toString();
                    
                    // 创建DocumentChunk对象
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setChunk_id(chunkId);
                    chunk.setFile_name(file.getName());
                    chunk.setFile_content(chunkText);
                    chunk.setEmbedding(embedding);
                    chunk.setChunk_seq(chunkSeq);
                    
                    // 设置元数据
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("fileId", fileId);
                    meta.put("fileName", file.getName());
                    meta.put("fileSize", file.length());
                    chunk.setMeta(meta);
                    
                    return chunk;
                })
                .collect(Collectors.toList());
        
        // 存储文档分块到Elasticsearch
        elasticsearchService.storeDocumentChunks(documentChunks);
        
        // 保存文件ID和文件详情的映射
        Map<String, Object> fileDetails = new HashMap<>();
        fileDetails.put("fileName", file.getName());
        fileDetails.put("fileSize", file.length());
        fileDetails.put("fileId", fileId);
        fileMappings.put(fileId, fileDetails);
    }

    // 从向量存储中检索相关文档
    public List<DocumentChunk> search(String query, int topK) throws IOException {
        return elasticsearchService.searchRelevantChunks(query, topK);
    }

    // 批量执行RAG查询，生成回答
    public String batchRagQuery(String query, List<String> fileIds) throws IOException {
        try {
            // 1. 从Elasticsearch中检索相关文档分块
            List<DocumentChunk> relevantChunks = elasticsearchService.searchRelevantChunksByFileIds(query, fileIds, 10);
            
            // 2. 构建提示，在每个文档块中嵌入来源信息，并收集实际相关的文档来源
            // 使用更严格的过滤和排序，确保只使用最相关的文档
            List<DocumentChunk> filteredChunks = relevantChunks.stream()
                    .filter(chunk -> {
                        // 检查文档嵌入向量是否存在且有效
                        return chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty() && 
                               chunk.getFile_content() != null && !chunk.getFile_content().trim().isEmpty();
                    })
                    .limit(5)  // 只使用最相关的前5个文档
                    .collect(Collectors.toList());
            
            // 构建上下文并收集实际使用的文档来源
            StringBuilder contextBuilder = new StringBuilder();
            Map<String, DocumentChunk> sourceChunks = new LinkedHashMap<>();
            
            for (DocumentChunk chunk : filteredChunks) {
                String fileName = chunk.getFile_name() != null ? chunk.getFile_name() : "未知来源";
                contextBuilder.append("[SOURCE: " + fileName + "]\n");
                contextBuilder.append(chunk.getFile_content());
                contextBuilder.append("\n\n");
            }
            
            String context = contextBuilder.toString().trim();
            
            // 检查是否有匹配的内容
            if (context.isEmpty()) {
                return "抱歉无法进行总结";
            }
            
            // 构建优化后的提示词
            String prompt = String.format("根据以下上下文信息回答问题。\n\n上下文:\n%s\n\n问题: %s", context, query);
            
            // 3. 使用Ollama生成回答
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是一个专业的文档总结助手，必须严格按照以下要求进行总结：\n1. 仅使用提供的上下文信息，不得添加任何外部内容或个人观点；\n2. 总结内容需清晰、简洁，避免重复信息；\n3. 每个段落需独立分明，并在段落前添加适当的图标（如📌、✨、📝等）；\n4. 若上下文信息不足或无匹配内容，请直接回答'抱歉无法进行总结'。"));
            messages.add(Map.of("role", "user", "content", prompt));
            
            // 发送请求到Ollama生成回答
            String answer = ollamaClient.generateChatCompletion(model, messages);
            return answer;
        } catch (Exception e) {
            logger.error("RAG查询失败: {}, 错误信息: {}", query, e.getMessage(), e);
            throw new IOException("处理查询时出错", e);
        }
    }

    // 批量执行RAG查询，生成流式回答
    public CompletableFuture<Void> batchRagQueryStream(String query, List<String> fileIds, OllamaClient.ResponseCallback callback) throws IOException {
        try {
            // 1. 从Elasticsearch中检索相关文档分块
            List<DocumentChunk> relevantChunks = elasticsearchService.searchRelevantChunksByFileIds(query, fileIds, 10);
            
            // 2. 构建提示，在每个文档块中嵌入来源信息，并收集实际相关的文档来源
            // 使用更严格的过滤和排序，确保只使用最相关的文档
            List<DocumentChunk> filteredChunks = relevantChunks.stream()
                    .filter(chunk -> {
                        // 检查文档嵌入向量是否存在且有效
                        return chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty() && 
                               chunk.getFile_content() != null && !chunk.getFile_content().trim().isEmpty();
                    })
                    .limit(5)  // 只使用最相关的前5个文档
                    .collect(Collectors.toList());
            
            // 构建上下文并收集实际使用的文档来源
            StringBuilder contextBuilder = new StringBuilder();
            Map<String, DocumentChunk> sourceChunks = new LinkedHashMap<>();
            
            for (DocumentChunk chunk : filteredChunks) {
                String fileName = chunk.getFile_name() != null ? chunk.getFile_name() : "未知来源";
                contextBuilder.append("[SOURCE: " + fileName + "]\n");
                contextBuilder.append(chunk.getFile_content());
                contextBuilder.append("\n\n");
            }
            
            String context = contextBuilder.toString().trim();
            
            // 检查是否有匹配的内容
            if (context.isEmpty()) {
                // 直接返回无匹配内容的提示
                callback.onResponse("抱歉无法进行总结");
                callback.onComplete();
                return CompletableFuture.completedFuture(null);
            }
            
            // 构建优化后的提示词
            String prompt = String.format("根据以下上下文信息回答问题。\n\n上下文:\n%s\n\n问题: %s", context, query);
            
            // 3. 使用Ollama生成流式回答
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是一个专业的文档总结助手，必须严格按照以下要求进行总结：\n1. 仅使用提供的上下文信息，不得添加任何外部内容或个人观点；\n2. 总结内容需清晰、简洁，避免重复信息；\n3. 每个段落需独立分明，并在段落前添加适当的图标（如📌、✨、📝等）；\n4. 若上下文信息不足或无匹配内容，请直接回答'抱歉无法进行总结'。"));
            messages.add(Map.of("role", "user", "content", prompt));
            
            // 使用Ollama生成流式回答
            return ollamaClient.generateChatCompletionStreamSSE(model, messages, callback);
        } catch (Exception e) {
            logger.error("RAG流式查询失败: {}, 错误信息: {}", query, e.getMessage(), e);
            throw new IOException("处理流式查询时出错", e);
        }
    }
    
    // 执行RAG查询，生成回答（非流式）
    public String ragQuery(String query) throws IOException {
        try {
            // 1. 从Elasticsearch中检索相关文档分块
            List<DocumentChunk> relevantChunks = elasticsearchService.searchRelevantChunks(query, 10);
            
            // 2. 构建提示，在每个文档块中嵌入来源信息，并收集实际相关的文档来源
            // 使用更严格的过滤和排序，确保只使用最相关的文档
            List<DocumentChunk> filteredChunks = relevantChunks.stream()
                    .filter(chunk -> {
                        // 检查文档嵌入向量是否存在且有效
                        return chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty() && 
                               chunk.getFile_content() != null && !chunk.getFile_content().trim().isEmpty();
                    })
                    .limit(5)  // 只使用最相关的前5个文档
                    .collect(Collectors.toList());
            
            // 构建上下文并收集实际使用的文档来源
            StringBuilder contextBuilder = new StringBuilder();
            Map<String, DocumentChunk> sourceChunks = new LinkedHashMap<>();
            
            for (DocumentChunk chunk : filteredChunks) {
                String fileName = chunk.getFile_name() != null ? chunk.getFile_name() : "未知来源";
                contextBuilder.append("[SOURCE: " + fileName + "]\n");
                contextBuilder.append(chunk.getFile_content());
                contextBuilder.append("\n\n");
            }
            
            String context = contextBuilder.toString().trim();
            
            // 检查是否有匹配的内容
            if (context.isEmpty()) {
                return "抱歉无法进行总结";
            }
            
            // 构建提示词
            String prompt = String.format("根据以下上下文信息回答问题。\n\n上下文:\n%s\n\n问题: %s", context, query);
            
            // 3. 使用Ollama生成回答
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是一个专业的文档总结助手，必须严格按照以下要求进行总结：\n1. 仅使用提供的上下文信息，不得添加任何外部内容或个人观点；\n2. 总结内容需清晰、简洁，避免重复信息；\n3. 每个段落需独立分明，并在段落前添加适当的图标（如📌、✨、📝等）；\n4. 若上下文信息不足或无匹配内容，请直接回答'抱歉无法进行总结'。"));
            messages.add(Map.of("role", "user", "content", prompt));
            
            // 生成回答
            String answer = ollamaClient.generateChatCompletion(model, messages);
            
            // 不再添加参考来源信息
            
            return answer;
        } catch (Exception e) {
            logger.error("Error during RAG query: {}", e.getMessage());
            throw new IOException("处理查询时出错", e);
        }
    }

    // 生成文档总结
    public String generateSummary(String content) {
        try {
            // 检查是否有内容需要总结
            if (content == null || content.trim().isEmpty()) {
                return "抱歉无法进行总结";
            }
            
            // 构建总结提示词
            String prompt = String.format("请总结以下内容，确保涵盖所有重要信息：\n\n%s", content);
            
            // 准备消息
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是一个专业的文档总结助手，必须严格按照以下要求进行总结：\n1. 仅使用提供的上下文信息，不得添加任何外部内容或个人观点；\n2. 总结内容需清晰、简洁，避免重复信息；\n3. 每个段落需独立分明，并在段落前添加适当的图标（如📌、✨、📝等）；\n4. 若上下文信息不足或无匹配内容，请直接回答'抱歉无法进行总结'。"));
            messages.add(Map.of("role", "user", "content", prompt));
            
            // 使用Ollama生成总结
            return ollamaClient.generateChatCompletion(model, messages);
        } catch (Exception e) {
            System.err.println("Error during summary generation: " + e.getMessage());
            return "生成总结时出错: " + e.getMessage();
        }
    }
    
    // 执行RAG查询，生成流式回答
    public CompletableFuture<Void> ragQueryStream(String query, OllamaClient.ResponseCallback callback) throws IOException {
        try {
            // 1. 从Elasticsearch中检索相关文档分块
            List<DocumentChunk> relevantChunks = elasticsearchService.searchRelevantChunks(query, 10);
            
            // 2. 构建提示，在每个文档块中嵌入来源信息，并收集实际相关的文档来源
            // 使用更严格的过滤和排序，确保只使用最相关的文档
            List<DocumentChunk> filteredChunks = relevantChunks.stream()
                    .filter(chunk -> {
                        // 检查文档嵌入向量是否存在且有效
                        return chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty() && 
                               chunk.getFile_content() != null && !chunk.getFile_content().trim().isEmpty();
                    })
                    .limit(5)  // 只使用最相关的前5个文档
                    .collect(Collectors.toList());
            
            // 构建上下文并收集实际使用的文档来源
            StringBuilder contextBuilder = new StringBuilder();
            Map<String, DocumentChunk> sourceChunks = new LinkedHashMap<>();
            
            for (DocumentChunk chunk : filteredChunks) {
                String fileName = chunk.getFile_name() != null ? chunk.getFile_name() : "未知来源";
                contextBuilder.append("[SOURCE: " + fileName + "]\n");
                contextBuilder.append(chunk.getFile_content());
                contextBuilder.append("\n\n");
            }
            
            String context = contextBuilder.toString().trim();
            
            // 构建提示词
            String prompt = String.format("根据以下上下文信息回答问题。\n\n上下文:\n%s\n\n问题: %s", context, query);
            
            // 使用Ollama生成流式回答
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是一个有用的助手，必须根据提供的上下文回答问题。"));
            messages.add(Map.of("role", "user", "content", prompt));
            
            // 创建包装后的回调函数，在回答完成后添加来源信息
            OllamaClient.ResponseCallback wrappedCallback = new OllamaClient.ResponseCallback() {
                @Override
                public void onResponse(String content) {
                    callback.onResponse(content);
                }
                
                @Override
                public void onComplete() {
                    // 不再添加参考来源信息
                    callback.onComplete();
                }
                
                @Override
                public void onError(Exception e) {
                    callback.onError(e);
                }
            };
            
            // 使用包装后的回调
            return ollamaClient.generateChatCompletionStreamSSE(model, messages, wrappedCallback);
        } catch (Exception e) {
            logger.error("Error during RAG query stream: {}", e.getMessage());
            callback.onError(e);
            return CompletableFuture.completedFuture(null);
        }
    }

    // 清除向量存储中的所有文档
    public void clearVectorStore() throws IOException {
        elasticsearchService.deleteAllChunks();
        fileMappings.clear();
    }
    
    // 保存文件ID和文件名的映射
    private void saveFileMapping(String fileId, Map<String, Object> fileName) {
        fileMappings.put(fileId, fileName);
    }
    
    // 根据文件ID删除文件
    public boolean deleteFile(String fileId) throws IOException {
        if (!fileMappings.containsKey(fileId)) {
            return false;
        }
        
        elasticsearchService.deleteChunksByFileId(fileId);
        Map<String, Object> fileDetails = fileMappings.remove(fileId);
        logger.info("删除文件成功: {}, 文件名: {}", fileId, fileDetails.get("file_name"));
        return true;
    }
    
    // 获取所有已上传的文件列表，包含详细信息
    public List<Map<String, String>> getAllFiles() {
        List<Map<String, String>> files = new ArrayList<>();
        
        // 使用fileMappings获取所有文件的详细信息
        for (Map.Entry<String, Map<String, Object>> entry : fileMappings.entrySet()) {
            String fileId = entry.getKey();
            Map<String, Object> fileDetails = entry.getValue();
            
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("id", fileId);
            
            // 添加文件名
            fileInfo.put("name", fileDetails.get("file_name").toString());
            
            // 添加文件大小
            fileInfo.put("size", fileDetails.get("file_size").toString());
            
            // 添加文件类型
            fileInfo.put("type", fileDetails.get("file_type").toString());
            
            // 添加上传时间
            fileInfo.put("uploadedAt", fileDetails.get("uploadedAt").toString());
            
            files.add(fileInfo);
        }
        
        // 对于在fileMappings中但没有详细信息的文件（可能是旧数据）
        for (Map.Entry<String, Map<String, Object>> entry : fileMappings.entrySet()) {
            String fileId = entry.getKey();
            Map<String, Object> fileDetails = entry.getValue();
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("id", fileId);
            fileInfo.put("name", fileDetails.get("file_name").toString());
            fileInfo.put("size", "0");
            fileInfo.put("type", "未知类型");
                fileInfo.put("uploadedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date()));
                files.add(fileInfo);
        }
         return files;

    }
    
    // 辅助方法：将文档分割成块（改进版，基于句子和段落进行智能分割）
    private List<String> splitDocumentIntoChunks(String content, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        // 首先按段落分割
        String[] paragraphs = content.split("\n\s*\n");
        
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;
            
            // 如果当前段落已经超过chunkSize，需要进一步分割
            if (paragraph.length() > chunkSize) {
                // 先添加当前累积的内容
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk.setLength(0);
                }
                
                // 按句子分割段落
                List<String> sentences = splitIntoSentences(paragraph);
                for (String sentence : sentences) {
                    if (currentChunk.length() + sentence.length() + 1 > chunkSize) {
                        // 如果添加当前句子会超过chunkSize，保存当前chunk
                        chunks.add(currentChunk.toString());
                        currentChunk.setLength(0);
                        
                        // 处理超长句子（不太可能，但为了安全起见）
                        if (sentence.length() > chunkSize) {
                            // 对于超长句子，使用字符分割并确保overlap
                            for (int i = 0; i < sentence.length(); i += chunkSize - overlap) {
                                int end = Math.min(i + chunkSize, sentence.length());
                                chunks.add(sentence.substring(i, end));
                            }
                            continue;
                        }
                    }
                    if (currentChunk.length() > 0) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(sentence);
                }
                
                // 添加最后一个chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk.setLength(0);
                }
            } else {
                // 如果添加当前段落不会超过chunkSize，添加到当前chunk
                if (currentChunk.length() + paragraph.length() + 2 <= chunkSize) {
                    if (currentChunk.length() > 0) {
                        currentChunk.append("\n\n");
                    }
                    currentChunk.append(paragraph);
                } else {
                    // 否则保存当前chunk并开始新的chunk
                    chunks.add(currentChunk.toString());
                    // 考虑overlap，从当前chunk末尾取overlap长度的内容
                    if (overlap > 0 && chunks.size() > 0) {
                        String lastChunk = chunks.get(chunks.size() - 1);
                        if (lastChunk.length() > overlap) {
                            currentChunk.append(lastChunk.substring(lastChunk.length() - overlap));
                            // 添加一个分隔符
                            if (currentChunk.length() > 0) {
                                currentChunk.append(" ");
                            }
                        } else {
                            currentChunk.append(lastChunk);
                            currentChunk.append(" ");
                        }
                    }
                    currentChunk.append(paragraph);
                }
            }
        }
        
        // 添加最后一个chunk（如果有）
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
    }
    
    // 辅助方法：将文本分割成句子
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 简单的句子分割正则表达式，匹配句号、问号、感叹号后跟空格或换行
        // 注意：这是一个简化版本，实际应用中可能需要更复杂的NLP处理
        String[] sentenceArray = text.split("(?<=[.!?])\\s+");
        for (String sentence : sentenceArray) {
            sentence = sentence.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }
}