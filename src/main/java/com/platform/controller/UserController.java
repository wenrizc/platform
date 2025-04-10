package com.platform.controller;

import com.platform.entity.User;
import com.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册/登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request,
                                   HttpServletRequest httpRequest,
                                   HttpSession session) {
        String username = request.get("username");
        String password = request.get("password");
        String clientAddress = httpRequest.getRemoteAddr();
        String sessionId = session.getId();

        if (username == null || username.trim().isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "用户名不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        if (password == null || password.trim().isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "密码不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        // 检查用户名是否已存在
        User existingUser = userService.findByUsername(username);
        if (existingUser != null) {
            // 用户存在，验证密码
            if (!userService.validatePassword(existingUser, password)) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "密码错误");
                return ResponseEntity.badRequest().body(response);
            }

            // 密码正确，更新会话
            User updatedUser = userService.updateUserSession(existingUser, clientAddress, sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedUser.getId());
            response.put("username", updatedUser.getUsername());
            response.put("message", "登录成功");
            return ResponseEntity.ok(response);
        }

        // 新用户，注册
        User newUser = userService.registerUser(username, password, clientAddress, sessionId);
        if (newUser == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "注册失败");
            return ResponseEntity.badRequest().body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", newUser.getId());
        response.put("username", newUser.getUsername());
        response.put("message", "注册并登录成功");

        return ResponseEntity.ok(response);
    }

    /**
     * 用户登出接口
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        boolean success = userService.logoutUser(session.getId());

        Map<String, Object> response = new HashMap<>();
        if (success) {
            response.put("message", "登出成功");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "用户未登录或会话已失效");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = userService.findBySessionId(session.getId());
        if (user == null || !user.isActive()) {
            return ResponseEntity.notFound().build();
        }
        userService.updateUserActivity(session.getId());

        // 不返回密码
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("clientAddress", user.getClientAddress());
        userInfo.put("virtualIp", user.getVirtualIp());
        userInfo.put("active", user.isActive());
        userInfo.put("lastActiveTime", user.getLastActiveTime());

        return ResponseEntity.ok(userInfo);
    }

    /**
     * 获取所有活跃用户
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllActiveUsers() {
        return ResponseEntity.ok(userService.getAllActiveUsers());
    }
}