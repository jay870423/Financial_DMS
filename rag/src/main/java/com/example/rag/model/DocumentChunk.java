package com.example.rag.model;

import java.util.List;
import java.util.Map;

/**
 * 文档分块实体类，用于Elasticsearch存储
 * 表示文档的一个分块及其相关信息
 */
public class DocumentChunk {

    private String chunk_id;
    private String file_name;
    private String file_content;
    private List<Double> embedding;
    private int chunk_seq;
    private Map<String, Object> meta;

    // 默认构造函数
    public DocumentChunk() {
    }

    // 全参构造函数
    public DocumentChunk(String chunk_id, String file_name, String file_content, List<Double> embedding, int chunk_seq, Map<String, Object> meta) {
        this.chunk_id = chunk_id;
        this.file_name = file_name;
        this.file_content = file_content;
        this.embedding = embedding;
        this.chunk_seq = chunk_seq;
        this.meta = meta;
    }

    // Getter和Setter方法
    public String getChunk_id() {
        return chunk_id;
    }

    public void setChunk_id(String chunk_id) {
        this.chunk_id = chunk_id;
    }

    public String getFile_name() {
        return file_name;
    }

    public void setFile_name(String file_name) {
        this.file_name = file_name;
    }

    public String getFile_content() {
        return file_content;
    }

    public void setFile_content(String file_content) {
        this.file_content = file_content;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public int getChunk_seq() {
        return chunk_seq;
    }

    public void setChunk_seq(int chunk_seq) {
        this.chunk_seq = chunk_seq;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    @Override
    public String toString() {
        return "DocumentChunk{\n" +
                "  chunk_id='" + chunk_id + "'\n" +
                "  file_name='" + file_name + "'\n" +
                "  file_content='" + file_content.substring(0, Math.min(file_content.length(), 50)) + (file_content.length() > 50 ? "..." : "") + "'\n" +
                "  embedding=" + (embedding != null ? embedding.size() : 0) + "维向量\n" +
                "  chunk_seq=" + chunk_seq + "\n" +
                "  meta=" + meta + "\n" +
                '}';
    }
}