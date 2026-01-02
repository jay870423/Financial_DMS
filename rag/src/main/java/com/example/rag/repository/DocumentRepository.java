package com.example.rag.repository;

import com.example.rag.model.Document;
import com.example.rag.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 文档数据访问接口
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    /**
     * 查找用户的所有文档（分页）
     * @param user 用户对象
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    org.springframework.data.domain.Page<Document> findByUserOrderByCreatedAtDesc(User user, org.springframework.data.domain.Pageable pageable);
    
    /**
     * 按类别查找用户的文档（分页）
     * @param user 用户对象
     * @param category 文档类别
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    org.springframework.data.domain.Page<Document> findByUserAndCategoryOrderByCreatedAtDesc(User user, String category, org.springframework.data.domain.Pageable pageable);
    
    /**
     * 通过标题模糊搜索用户的文档（分页）
     * @param user 用户对象
     * @param keyword 关键词
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    org.springframework.data.domain.Page<Document> findByUserAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(User user, String keyword, org.springframework.data.domain.Pageable pageable);
    
    /**
     * 查找用户的所有文档（不分页，用于旧版方法兼容）
     * @param user 用户对象
     * @return 文档列表
     */
    List<Document> findByUserOrderByCreatedAtDesc(User user);
    
    /**
     * 按类别查找用户的文档（不分页，用于旧版方法兼容）
     * @param user 用户对象
     * @param category 文档类别
     * @return 文档列表
     */
    List<Document> findByUserAndCategoryOrderByCreatedAtDesc(User user, String category);
    
    /**
     * 通过标题模糊搜索用户的文档（不分页，用于旧版方法兼容）
     * @param user 用户对象
     * @param keyword 关键词
     * @return 文档列表
     */
    List<Document> findByUserAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(User user, String keyword);
    
    /**
     * 通过标签查找文档
     * @param user 用户对象
     * @param tag 标签
     * @return 文档列表
     */
    @Query("SELECT d FROM Document d WHERE d.user = :user AND d.tags LIKE %:tag%")
    List<Document> findByUserAndTag(@Param("user") User user, @Param("tag") String tag);
    
    /**
     * 查找最近访问的文档
     * @param user 用户对象
     * @param limit 限制数量
     * @return 文档列表
     */
    List<Document> findTop10ByUserOrderByLastAccessedDesc(User user);
    
    /**
     * 检查文件名是否已存在
     * @param user 用户对象
     * @param fileName 文件名
     * @return 是否存在
     */
    boolean existsByUserAndFileName(User user, String fileName);
    
    /**
     * 获取用户的所有文档类别
     * @param userId 用户ID
     * @return 类别列表
     */
    @Query("SELECT DISTINCT d.category FROM Document d WHERE d.user.id = :userId AND d.category IS NOT NULL")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);
    
    /**
     * 支持排序的用户文档查询
     * @param user 用户对象
     * @param sort 排序参数
     * @return 文档列表
     */
    List<Document> findByUser(User user, Sort sort);
    
    /**
     * 支持关键词搜索的文档查询，可按标题或描述搜索
     * @param user1 用户对象
     * @param title 标题关键词
     * @param user2 用户对象
     * @param description 描述关键词
     * @param sort 排序参数
     * @return 文档列表
     */
    List<Document> findByUserAndTitleContainingOrUserAndDescriptionContaining(User user1, String title, User user2, String description, Sort sort);
    
    /**
     * 通过ID查找特定用户的文档
     * @param user 用户对象
     * @param id 文档ID
     * @return 文档对象
     */
    Document findByUserAndId(User user, Long id);
    
    /**
     * 统计用户文档数量
     * @param user 用户对象
     * @return 文档数量
     */
    long countByUser(User user);
    
    /**
     * 获取用户所有文档类别（不包含空字符串）
     * @param user 用户对象
     * @return 类别列表
     */
    @Query("SELECT DISTINCT d.category FROM Document d WHERE d.user = ?1 AND d.category IS NOT NULL AND d.category != ''")
    List<String> findDistinctCategoriesByUser(User user);
    
    /**
     * 统计用户文档类别数量
     * @param user 用户对象
     * @return 类别数量
     */
    @Query("SELECT COUNT(DISTINCT d.category) FROM Document d WHERE d.user = ?1 AND d.category IS NOT NULL AND d.category != ''")
    long countUserCategories(User user);
    
    /**
     * 统计用户所有文档大小
     * @param user 用户对象
     * @return 总大小
     */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d WHERE d.user = ?1")
    long sumSizeByUser(User user);
    
    /**
     * 按创建时间范围和用户查找文档
     * @param start 开始时间
     * @param end 结束时间
     * @param user 用户对象
     * @return 文档列表
     */
    List<Document> findByCreatedAtBetweenAndUser(LocalDateTime start, LocalDateTime end, User user);
}