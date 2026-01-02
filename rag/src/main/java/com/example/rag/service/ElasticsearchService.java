package com.example.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.example.rag.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Elasticsearch服务类
 * 用于处理文档分块的向量索引和搜索
 */
@Service
public class ElasticsearchService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchService.class);

    private static final String INDEX_NAME = "rag_idx";
    private static final int EMBEDDING_DIMENSIONS = 1536;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    /**
     * 确保索引存在，如果不存在则创建
     */
    public void ensureIndexExists() throws IOException {
        // 检查索引是否存在
        boolean indexExists = elasticsearchClient.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();

        if (!indexExists) {
            // 创建索引
            CreateIndexRequest createIndexRequest = CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                    )
                    .mappings(m -> m
                            .properties("chunk_id", p -> p.keyword(k -> k))
                            .properties("file_name", p -> p.keyword(k -> k))
                            .properties("file_content", p -> p.text(t -> t.analyzer("standard")))
                            .properties("embedding", p -> p.denseVector(dv -> dv
                                    .dims(EMBEDDING_DIMENSIONS)
                                    .index(true)
                                    .similarity("cosine")
                            ))
                            .properties("chunk_seq", p -> p.short_(s -> s))
                            .properties("meta", p -> p.object(o -> o))
                    )
            );

            elasticsearchClient.indices().create(createIndexRequest);
            logger.info("创建索引: {}", INDEX_NAME);
        } else {
            logger.info("索引已存在: {}", INDEX_NAME);
        }
    }

    /**
     * 存储文档分块到Elasticsearch
     * @param chunks 文档分块列表
     */
    public void storeDocumentChunks(List<DocumentChunk> chunks) throws IOException {
        // 确保索引存在
        ensureIndexExists();

        // 创建批量操作
        List<BulkOperation> bulkOperations = chunks.stream()
                .map(chunk -> BulkOperation.of(b -> b
                        .index(i -> i
                                .index(INDEX_NAME)
                                .id(chunk.getChunk_id())
                                .document(chunk)
                        )
                ))
                .collect(Collectors.toList());

        // 执行批量操作
        BulkResponse bulkResponse = elasticsearchClient.bulk(BulkRequest.of(b -> b
                .operations(bulkOperations)
        ));

        // 检查批量操作结果
        if (bulkResponse.errors()) {
            List<String> errorMessages = new ArrayList<>();
            for (BulkResponseItem item : bulkResponse.items()) {
                if (item.error() != null) {
                    errorMessages.add(String.format("文档分块存储失败: %s - %s",
                            item.id(), item.error().reason()));
                }
            }
            throw new IOException("批量存储文档分块失败: " + String.join(", ", errorMessages));
        }

        logger.info("成功存储 {} 个文档分块到Elasticsearch", chunks.size());
    }

    /**
     * 搜索相关文档分块
     * @param query 查询文本
     * @param limit 返回结果数量
     * @return 相关文档分块列表
     */
    public List<DocumentChunk> searchRelevantChunks(String query, int limit) throws IOException {
        // 生成查询向量
        List<Double> queryEmbedding = vectorEmbeddingService.generateEmbedding(query);

        // 执行近似最近邻搜索
        SearchResponse<DocumentChunk> searchResponse = elasticsearchClient.search(s -> s
                .index(INDEX_NAME)
                .knn(k -> k
                        .field("embedding")
                        .queryVector(queryEmbedding.stream().map(d -> d.floatValue()).collect(Collectors.toList()))
                        .k(limit)
                )
                .source(sf -> sf.filter(f -> f.includes("*")))
        , DocumentChunk.class);

        // 处理搜索结果
        List<Hit<DocumentChunk>> hits = searchResponse.hits().hits();
        return hits.stream()
                .map(Hit::source)
                .collect(Collectors.toList());
    }

    /**
     * 批量搜索相关文档分块
     * @param queries 查询文本列表
     * @param limit 返回结果数量
     * @return 每个查询的相关文档分块列表
     */
    public List<List<DocumentChunk>> batchSearchRelevantChunks(List<String> queries, int limit) throws IOException {
        List<List<DocumentChunk>> results = new ArrayList<>();
        for (String query : queries) {
            List<DocumentChunk> chunks = searchRelevantChunks(query, limit);
            results.add(chunks);
        }
        return results;
    }
    
    /**
     * 根据文档ID搜索相关文档分块
     * @param query 查询文本
     * @param fileIds 文档ID列表
     * @param limit 返回结果数量
     * @return 相关文档分块列表
     */
    public List<DocumentChunk> searchRelevantChunksByFileIds(String query, List<String> fileIds, int limit) throws IOException {
        // 生成查询向量
        List<Double> queryEmbedding = vectorEmbeddingService.generateEmbedding(query);

        // 构造过滤条件，将字符串转换为数字类型以匹配索引中的documentId类型
        List<FieldValue> validIds = fileIds.stream()
                .map(id -> {
                    try {
                        return FieldValue.of(Long.parseLong(id));
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid document ID format: {}", id, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 如果没有有效ID，返回空查询
        if (validIds.isEmpty()) {
            return Collections.emptyList();
        }

        Query filterByFileIds = TermsQuery.of(t -> t
            .field("meta.documentId")
            .terms(ts -> ts.value(validIds)))._toQuery();

        // 执行近似最近邻搜索并根据文件ID过滤
        SearchResponse<DocumentChunk> searchResponse = elasticsearchClient.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q                     
                            .bool(b -> b
                                    .filter(filterByFileIds))) // 业务过滤
                .knn(k -> k
                        .field("embedding")
                        .queryVector(queryEmbedding.stream().map(d -> d.floatValue()).collect(Collectors.toList()))
                        .k(limit)
                        .numCandidates(50) // 增加候选文档数以获得更准确的结果
                         .similarity(0.30f) // 提高最小相似度阈值过滤不相关结果
                )
                .source(sf -> sf.filter(f -> f.includes("*")))
        , DocumentChunk.class);

        // 处理搜索结果
        List<Hit<DocumentChunk>> hits = searchResponse.hits().hits();
        return hits.stream()
                .map(Hit::source)
                .collect(Collectors.toList());
    }

    /**
     * 删除指定文件的所有文档分块
     * @param fileName 文件名
     */
    public void deleteChunksByFileName(String fileName) throws IOException {
        DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(d -> d
                .index(INDEX_NAME)
                .query(q -> q
                        .term(t -> t
                                .field("file_name")
                                .value(fileName)
                        )
                )
        );

        logger.info("删除了 {} 个与文件 {} 相关的文档分块", response.deleted(), fileName);
    }

    /**
     * 删除指定文件ID的所有文档分块
     * @param fileId 文件ID
     */
    public void deleteChunksByFileId(String fileId) throws IOException {
        DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(d -> d
                .index(INDEX_NAME)
                .query(q -> q
                        .term(t -> t
                                .field("meta.fileId")
                                .value(fileId)
                        )
                )
        );

        logger.info("删除了 {} 个与文件ID {} 相关的文档分块", response.deleted(), fileId);
    }

    /**
     * 删除所有文档分块
     */
    public void deleteAllChunks() throws IOException {
        DeleteByQueryResponse response = elasticsearchClient.deleteByQuery(d -> d
                .index(INDEX_NAME)
                .query(q -> q.matchAll(m -> m))
        );

        logger.info("删除了 {} 个文档分块", response.deleted());
    }

    /**
     * 统计文档分块数量
     * @return 文档分块总数
     */
    public long countDocumentChunks() throws IOException {
        CountResponse countResponse = elasticsearchClient.count(c -> c
                .index(INDEX_NAME)
        );

        return countResponse.count();
    }
}