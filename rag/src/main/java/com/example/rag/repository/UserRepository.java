package com.example.rag.repository;

import com.example.rag.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 用户数据访问接口
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 通过用户名查找用户
     * @param username 用户名
     * @return 用户对象
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 通过邮箱查找用户
     * @param email 邮箱
     * @return 用户对象
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 通过微信OpenID查找用户
     * @param wechatOpenId 微信OpenID
     * @return 用户对象
     */
    Optional<User> findByWechatOpenId(String wechatOpenId);
    
    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 检查邮箱是否已存在
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 按关键词搜索用户（支持用户名、姓名、邮箱模糊搜索）
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 分页用户列表
     */
    Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String keyword, String keyword2, String keyword3, Pageable pageable);

    /**
     * 统计指定时间范围内创建的用户数量
     * @param start 开始时间
     * @param end 结束时间
     * @return 用户数量
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}