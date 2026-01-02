package com.example.rag.service;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 根据用户名查找用户
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("用户名不存在: " + username));
        
        // 更新最后登录时间
        updateLastLoginTime(user);
        
        // 创建用户角色权限列表
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
        
        // 返回Spring Security的User对象
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            user.isEnabled(),
            true, // 账户是否未过期
            true, // 凭证是否未过期
            true, // 账户是否未锁定
            authorities
        );
    }

    /**
     * 更新用户最后登录时间
     */
    private void updateLastLoginTime(User user) {
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 根据邮箱查找用户（用于其他认证方式）
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + email));
    }
}