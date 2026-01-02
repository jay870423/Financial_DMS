package com.example.rag.config;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动时自动初始化超级管理员用户
 */
@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已存在admin用户
        if (!userRepository.existsByUsername("admin")) {
            logger.info("初始化超级管理员用户...");
            
            // 创建超级管理员用户
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("888888")); // 密码加密
            adminUser.setEmail("admin@example.com");
            adminUser.setFullName("超级管理员");
            adminUser.setRole(User.Role.ADMIN); // 设置为管理员角色
            adminUser.setEnabled(true);
            adminUser.setAccountNonLocked(true);
            adminUser.setAccountNonExpired(true);
            adminUser.setCredentialsNonExpired(true);
            adminUser.setCreatedAt(LocalDateTime.now());
            adminUser.setUpdatedAt(LocalDateTime.now());
            
            // 保存用户
            userRepository.save(adminUser);
            logger.info("超级管理员用户初始化成功！");
        } else {
            logger.info("超级管理员用户已存在，跳过初始化。");
        }
    }
}