package com.example.rag.config;

import com.example.rag.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * Spring Security配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {
    
    @Autowired
    private CustomUserDetailsService userDetailsService;
    
    // 移除多余的UserDetailsService Bean定义，直接使用注入的CustomUserDetailsService
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 配置CSRF保护
            .csrf(csrf -> csrf.disable())
            // 配置授权规则
            .authorizeHttpRequests(authorize -> authorize
                // 允许静态资源访问（始终允许，不考虑认证状态）
                .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/webjars/**", "/static/**").permitAll()
                // 允许登录、注册和微信登录页面访问
                .requestMatchers("/login", "/register", "/wechat-login", "/wechat/login", "/wechat/callback", "/forgot-password", "/reset-password").permitAll()
                // 允许H2控制台访问（开发环境）
                .requestMatchers("/h2-console/**").permitAll()
                // 允许错误页面访问
                .requestMatchers("/access-denied", "/error/**").permitAll()
                // 允许所有认证用户访问自己的资料页面
                .requestMatchers("/users/profile", "/users/profile/update").authenticated()
                // 需要管理员权限的其他用户管理路径
                .requestMatchers("/admin/**", "/users", "/users/add", "/users/edit/**", "/users/delete/**", "/users/{id}").hasRole("ADMIN")
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            )
            // 配置记住我功能
            .rememberMe(remember -> remember
                        .tokenValiditySeconds(86400) // 24小时
                        .key("rememberMeKey")
                        .userDetailsService(userDetailsService)
                    )
            // 配置异常处理
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error/access-denied")
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            );
        
        return http.build();
    }
    
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // 登录成功后的处理逻辑
            response.sendRedirect("/dashboard");
        };
    }
    
    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            // 登出成功后的处理逻辑
            response.sendRedirect("/login?logout=true");
        };
    }
}