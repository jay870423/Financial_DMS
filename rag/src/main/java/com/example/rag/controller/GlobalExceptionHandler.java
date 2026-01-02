package com.example.rag.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.security.Principal;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public String handle404(NoHandlerFoundException ex, Model model) {
        model.addAttribute("errorMessage", "页面不存在或已被移除");
        model.addAttribute("errorCode", "404");
        return "error/404";
    }

    /**
     * 处理访问被拒绝异常
     */
    @ExceptionHandler(SecurityException.class)
    public String handleAccessDenied(SecurityException ex, Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("message", "您没有权限访问此资源");
        } else {
            model.addAttribute("message", "请先登录再访问此资源");
        }
        return "error/access-denied";
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException ex, Model model) {
        model.addAttribute("errorMessage", "上传的文件大小超过了允许的最大值");
        return "error/error";
    }

    /**
     * 处理IO异常
     */
    @ExceptionHandler(IOException.class)
    public String handleIOException(IOException ex, Model model) {
        model.addAttribute("errorMessage", "文件操作失败：" + ex.getMessage());
        return "error/error";
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception ex, Model model) {
        model.addAttribute("errorMessage", "发生了一个错误：" + ex.getMessage());
        return "error/error";
    }

    // 移除与ErrorController冲突的GET方法，让ErrorController处理这些路径的请求
}