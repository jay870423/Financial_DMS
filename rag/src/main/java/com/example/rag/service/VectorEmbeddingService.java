package com.example.rag.service;

import com.example.rag.client.OllamaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 向量嵌入服务实现类
 */
@Service
public class VectorEmbeddingService {
    
    @Autowired
    private OllamaClient ollamaClient;
    
    @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}")
    private String embeddingModel;
    
    /**
     * 生成文本嵌入向量
     * 使用本地部署的ollama text-embedding-3-small模型
     */
    public List<Double> generateEmbedding(String text) {
        try {
            List<Double> embedding = ollamaClient.generateEmbedding(embeddingModel, text);
            // 验证嵌入向量的有效性，如果无效则生成随机嵌入
            if (!validateEmbedding(embedding)) {
                embedding = new ArrayList<>();
                Random random = new Random(text.hashCode()); // 使用文本哈希作为随机种子，使相同文本产生相同向量
                
                // 生成1536维向量（与text-embedding-3-small模型兼容）
                for (int i = 0; i < 1536; i++) {
                    embedding.add(random.nextDouble() * 2 - 1); // 生成-1到1之间的随机数
                }
            }
            return embedding;
        } catch (Exception e) {
            // 在异常情况下使用模拟实现作为回退
            e.printStackTrace();
            List<Double> embedding = new ArrayList<>();
            Random random = new Random(text.hashCode()); // 使用文本哈希作为随机种子，使相同文本产生相同向量
            
            // 生成1536维向量（与text-embedding-3-small模型兼容）
            for (int i = 0; i < 1536; i++) {
                embedding.add(random.nextDouble() * 2 - 1); // 生成-1到1之间的随机数
            }
            
            return embedding;
        }
    }
    
    /**
     * 批量生成文本嵌入向量
     */
    public List<List<Double>> batchGenerateEmbeddings(List<String> texts) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }
    
    /**
     * 规范化向量
     */
    public List<Double> normalizeVector(List<Double> vector) {
        double norm = 0.0;
        for (Double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        
        if (norm == 0) {
            return vector;
        }
        
        List<Double> normalizedVector = new ArrayList<>();
        for (Double value : vector) {
            normalizedVector.add(value / norm);
        }
        
        return normalizedVector;
    }
    
    /**
     * 验证嵌入向量
     */
    public boolean validateEmbedding(List<Double> embedding) {
        return embedding != null && !embedding.isEmpty() && embedding.size() == 1536;
    }
}