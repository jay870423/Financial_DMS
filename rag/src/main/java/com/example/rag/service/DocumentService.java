package com.example.rag.service;

import com.example.rag.model.Document;
import com.example.rag.model.DocumentChunk;
import com.example.rag.model.User;
import com.example.rag.repository.DocumentRepository;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 文档服务实现类
 */
@Service
public class DocumentService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;
    
    @Autowired
    private ElasticsearchService elasticsearchService;
    
    @Autowired
    private RagService ragService;
    
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;
    
    private long storageLimit; // Will be set to actual disk size
    
    /**
     * 初始化上传目录并设置磁盘大小限制
     */
    @PostConstruct
    public void init() {
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 获取上传目录所在磁盘的大小
        try {
            Path uploadPath = dir.toPath();
            FileStore store = Files.getFileStore(uploadPath);
            storageLimit = store.getTotalSpace();
        } catch (IOException e) {
            // 发生错误时使用默认值12 GB
            storageLimit = 12884901888L;
            System.err.println("Failed to get disk size: " + e.getMessage());
        }
    }
    
    /**
     * 格式化文件大小为人类可读的字符串
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1048576) {
            return String.format("%.1f KB", (double) size / 1024);
        } else if (size < 1073741824) {
            return String.format("%.1f MB", (double) size / 1048576);
        } else {
            return String.format("%.1f GB", (double) size / 1073741824);
        }
    }
    
    /**
     * 上传文档
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, String title, String category, 
                                  String tags, String description, User user) throws IOException {
        // 创建上传目录
        init();
        
        // 生成唯一文件名
        String uniqueFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path targetLocation = Paths.get(uploadDir).resolve(uniqueFileName);
        
        // 保存文件
        Files.copy(file.getInputStream(), targetLocation);
        
        // 提取文件内容
        String content = extractContent(file);
        
        // 创建文档实体
        Document document = new Document();
        document.setTitle(title);
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(targetLocation.toString());
        document.setFileSize(file.getSize());
        document.setContentType(file.getContentType());
        document.setContent(content);
        document.setCategory(category);
        document.setTags(tags);
        document.setDescription(description);
        document.setUser(user);
        
        // 生成文档总结
        String summary = ragService.generateSummary(content);
        document.setSummary(summary);
        
        // 保存文档到数据库，获取生成的ID
        Document savedDocument = documentRepository.save(document);
        
        // 将文档内容分割成块
        List<String> chunks = splitDocumentIntoChunks(content, 1000, 200);
        
        // 批量生成所有分块的向量嵌入
        List<List<Double>> embeddings = vectorEmbeddingService.batchGenerateEmbeddings(chunks);
        
        // 创建文档分块列表
        List<DocumentChunk> documentChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = UUID.randomUUID().toString();
            Map<String, Object> meta = new HashMap<>();
            meta.put("documentId", savedDocument.getId());
            meta.put("userId", user.getId());
            meta.put("title", title);
            meta.put("category", category);
            meta.put("tags", tags);
            meta.put("description", description);
            meta.put("filePath", targetLocation.toString());
            meta.put("fileSize", file.getSize());
            meta.put("contentType", file.getContentType());
            
            DocumentChunk chunk = new DocumentChunk(
                chunkId,
                file.getOriginalFilename(),
                chunks.get(i),
                embeddings.get(i),
                i,
                meta
            );
            documentChunks.add(chunk);
        }
        
        // 将文档分块存储到Elasticsearch
        elasticsearchService.storeDocumentChunks(documentChunks);
        
        return savedDocument;
    }
    
    /**
     * 提取文件内容
     */
    private String extractContent(MultipartFile file) throws IOException {
        try {
            // 使用Apache Tika提取文件内容
            org.apache.tika.Tika tika = new org.apache.tika.Tika();
            // 设置元数据以优化提取过程
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            metadata.set("resourceName", file.getOriginalFilename());
            
            // 提取纯文本内容
            String content = tika.parseToString(file.getInputStream(), metadata);
            
            // 限制内容长度，防止过大的文件导致内存问题
            if (content.length() > 100000) { // 限制为100KB
                content = content.substring(0, 100000) + "\n\n... 内容过长，已截断";
            }
            
            return content;
        } catch (Exception e) {
            // 记录异常并返回错误信息
            return "无法提取文件内容: " + e.getMessage();
        }
    }
    
    /**
     * 向量转字符串存储
     */
    private String embeddingToString(List<Double> embedding) {
        return embedding.stream()
            .map(Object::toString)
            .collect(Collectors.joining(","));
    }
    
    /**
     * 字符串转向量
     */
    private List<Double> stringToEmbedding(String embeddingStr) {
        List<Double> embedding = new ArrayList<>();
        if (embeddingStr != null && !embeddingStr.isEmpty()) {
            String[] parts = embeddingStr.split(",");
            for (String part : parts) {
                try {
                    embedding.add(Double.parseDouble(part));
                } catch (NumberFormatException e) {
                    // 忽略格式错误
                }
            }
        }
        return embedding;
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
        String[] sentenceArray = text.split("(?<=[.!?])\\s+");
        for (String sentence : sentenceArray) {
            sentence = sentence.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }
    
    /**
     * 获取用户的所有文档（分页）
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Document> getUserDocumentsPaged(User user, org.springframework.data.domain.Pageable pageable) {
        return documentRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }
    
    /**
     * 获取用户的所有文档（不分页，支持排序）
     */
    @Transactional(readOnly = true)
    public List<Document> getUserDocuments(User user, String sort) {
        List<Document> documents = documentRepository.findByUserOrderByCreatedAtDesc(user);
        return sortDocuments(documents, sort);
    }
    
    /**
     * 获取用户的所有文档（不分页，支持排序和日期范围过滤）
     */
    @Transactional(readOnly = true)
    public List<Document> getUserDocuments(User user, String sort, LocalDateTime dateFrom, LocalDateTime dateTo) {
        List<Document> documents = documentRepository.findByUserOrderByCreatedAtDesc(user);
        documents = filterDocumentsByDateRange(documents, dateFrom, dateTo);
        return sortDocuments(documents, sort);
    }
    
    /**
     * 获取用户的所有文档（不分页，默认按最新排序）
     * 兼容旧版本的方法调用
     */
    @Transactional(readOnly = true)
    public List<Document> getUserDocuments(User user) {
        return getUserDocuments(user, "newest");
    }
    
    /**
     * 搜索文档，支持关键词搜索和排序
     */
    @Transactional(readOnly = true)
    public List<Document> searchDocuments(User user, String keyword, String sort) {
        List<Document> documents = documentRepository.findByUserAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(user, keyword);
        return sortDocuments(documents, sort);
    }
    
    /**
     * 日期范围过滤方法
     */
    private List<Document> filterDocumentsByDateRange(List<Document> documents, LocalDateTime dateFrom, LocalDateTime dateTo) {
        if (dateFrom == null && dateTo == null) {
            return documents;
        }
        
        return documents.stream()
            .filter(doc -> {
                LocalDateTime docDate = doc.getCreatedAt();
                boolean afterFrom = (dateFrom == null) || docDate.isAfter(dateFrom) || docDate.isEqual(dateFrom);
                boolean beforeTo = (dateTo == null) || docDate.isBefore(dateTo.plusDays(1)) || docDate.isEqual(dateTo.plusDays(1));
                return afterFrom && beforeTo;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 通用文档排序方法
     */
    private List<Document> sortDocuments(List<Document> documents, String sort) {
        if (sort == null) {
            return documents;
        }
        
        switch (sort) {
            case "newest":
                return documents.stream()
                        .sorted((d1, d2) -> d2.getCreatedAt().compareTo(d1.getCreatedAt()))
                        .collect(Collectors.toList());
            case "oldest":
                return documents.stream()
                        .sorted((d1, d2) -> d1.getCreatedAt().compareTo(d2.getCreatedAt()))
                        .collect(Collectors.toList());
            case "nameAsc":
                return documents.stream()
                        .sorted((d1, d2) -> d1.getTitle().compareToIgnoreCase(d2.getTitle()))
                        .collect(Collectors.toList());
            case "nameDesc":
                return documents.stream()
                        .sorted((d1, d2) -> d2.getTitle().compareToIgnoreCase(d1.getTitle()))
                        .collect(Collectors.toList());
            default:
                return documents;
        }
    }
    
    /**
     * 获取文档详情
     */
    @Transactional
    public Document getDocumentById(Long id, User user) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("文档不存在"));
        
        // 验证权限
        if (!document.getUser().equals(user)) {
            throw new RuntimeException("无权限访问此文档");
        }
        
        // 更新最后访问时间
        document.setLastAccessed(LocalDateTime.now());
        documentRepository.save(document);
        
        return document;
    }
    
    /**
     * 更新文档信息
     */
    @Transactional
    public Document updateDocument(Long id, String title, String category, 
                                 String tags, String description, User user) {
        Document document = getDocumentById(id, user);
        
        document.setTitle(title);
        document.setCategory(category);
        document.setTags(tags);
        document.setDescription(description);
        
        return documentRepository.save(document);
    }
    
    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long id, User user) {
        Document document = getDocumentById(id, user);
        
        // 删除物理文件
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            // 记录日志但继续删除数据库记录
        }
        
        // 删除数据库记录
        documentRepository.delete(document);
    }
    
    /**
     * 向量化搜索文档
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> vectorSearch(String query, User user, int limit) {
        // 生成查询向量
        List<Double> queryVector = vectorEmbeddingService.generateEmbedding(query);
        
        // 获取用户的所有文档
        List<Document> userDocuments = documentRepository.findByUserOrderByCreatedAtDesc(user);
        
        // 计算相似度并排序
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Document doc : userDocuments) {
            List<Double> docVector = stringToEmbedding(doc.getEmbeddingVector());
            if (!docVector.isEmpty()) {
                double similarity = calculateCosineSimilarity(queryVector, docVector);
                
                Map<String, Object> result = new HashMap<>();
                result.put("document", doc);
                result.put("similarity", similarity);
                result.put("source", doc.getFileName()); // 标注文件来源
                
                results.add(result);
            }
        }
        
        // 按相似度降序排序并限制结果数量
        return results.stream()
            .sorted((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * 计算余弦相似度
     */
    private double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1.size() != vec2.size() || vec1.isEmpty()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 按类别搜索文档（分页）
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Document> searchByCategoryPaged(String category, User user, org.springframework.data.domain.Pageable pageable) {
        return documentRepository.findByUserAndCategoryOrderByCreatedAtDesc(user, category, pageable);
    }
    
    /**
     * 按类别搜索文档（不分页，支持排序和日期范围过滤）
     */
    @Transactional(readOnly = true)
    public List<Document> searchByCategory(String category, User user, String sort, LocalDateTime dateFrom, LocalDateTime dateTo) {
        List<Document> documents = documentRepository.findByUserAndCategoryOrderByCreatedAtDesc(user, category);
        documents = filterDocumentsByDateRange(documents, dateFrom, dateTo);
        return sortDocuments(documents, sort);
    }
    
    /**
     * 按标题搜索文档（分页）
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Document> searchByTitlePaged(String keyword, User user, org.springframework.data.domain.Pageable pageable) {
        return documentRepository.findByUserAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(user, keyword, pageable);
    }
    
    /**
     * 按标题搜索文档（不分页，支持排序和日期范围过滤）
     */
    @Transactional(readOnly = true)
    public List<Document> searchByTitle(String keyword, User user, String sort, LocalDateTime dateFrom, LocalDateTime dateTo) {
        List<Document> documents = documentRepository.findByUserAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(user, keyword);
        documents = filterDocumentsByDateRange(documents, dateFrom, dateTo);
        return sortDocuments(documents, sort);
    }
    
    /**
     * 获取文档统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentStatistics(User user) {
        Map<String, Object> stats = new HashMap<>();
        List<Document> documents = documentRepository.findByUserOrderByCreatedAtDesc(user);
        
        // 获取当前时间用于计算
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime monthAgo = now.minusMonths(1);
        
        // 总文档数
        long totalCount = documents.size();
        stats.put("totalCount", totalCount);
        
        // 今日新增
        long todayCount = documents.stream()
            .filter(doc -> doc.getCreatedAt().isAfter(todayStart))
            .count();
        stats.put("todayCount", todayCount);
        
        // 昨日新增
        long yesterdayCount = documents.stream()
            .filter(doc -> doc.getCreatedAt().isAfter(yesterdayStart) && doc.getCreatedAt().isBefore(todayStart))
            .count();
        
        // 计算每日增长率
        double dailyIncrease = 0.0;
        if (yesterdayCount > 0) {
            dailyIncrease = ((double)(todayCount - yesterdayCount) / yesterdayCount) * 100;
        }
        stats.put("dailyIncrease", String.format("%.1f", dailyIncrease));
        
        // 上月文档数
        long lastMonthCount = documents.stream()
            .filter(doc -> doc.getCreatedAt().isAfter(monthAgo) && doc.getCreatedAt().isBefore(todayStart))
            .count();
        
        // 计算月度增长率
        double monthlyIncrease = 0.0;
        if (lastMonthCount > 0) {
            long currentMonthCount = documents.stream()
                .filter(doc -> doc.getCreatedAt().isAfter(todayStart))
                .count();
            monthlyIncrease = ((double)(currentMonthCount) / lastMonthCount) * 100;
        }
        stats.put("monthlyIncrease", String.format("%.1f", monthlyIncrease));
        
        // 待处理文档（这里假设没有明确的状态字段，暂时返回0）
        stats.put("pendingCount", 0L);
        
        // 紧急文档（这里假设没有明确的状态字段，暂时返回0）
        stats.put("urgentCount", 0L);
        
        // 文档分类数量
        long currentCategories = documentRepository.countUserCategories(user);
        
        // 昨日文档分类数量
        long yesterdayCategories = documentRepository.findByCreatedAtBetweenAndUser(yesterdayStart, todayStart, user)
                .stream().map(Document::getCategory).distinct().filter(cat -> cat != null && !cat.isEmpty()).count();
        
        // 计算文档分类每日增长率
        double categoryDailyGrowth = 0.0;
        if (yesterdayCategories > 0) {
            categoryDailyGrowth = ((double)(currentCategories - yesterdayCategories) / yesterdayCategories) * 100;
        }
        stats.put("categoryDailyGrowth", String.format("%.1f", categoryDailyGrowth));
        
        // 存储空间使用情况
        long usedStorage = documentRepository.sumSizeByUser(user);
        double storagePercentage = 0.0;
        if (storageLimit > 0) {
            storagePercentage = ((double) usedStorage / storageLimit) * 100;
        }
        stats.put("usedStorage", formatFileSize(usedStorage));
        stats.put("storagePercentage", Math.round(storagePercentage));
        
        // 兼容旧代码
        stats.put("total", totalCount);
        
        return stats;
    }
    
    /**
     * 获取文档分类数量
     */
    @Transactional(readOnly = true)
    public long getDocumentCategoriesCount(User user) {
        return documentRepository.countUserCategories(user);
    }
}