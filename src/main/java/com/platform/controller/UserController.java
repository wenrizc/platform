package com.platform.controller;

import com.platform.entity.Room;
import com.platform.entity.User;
import com.platform.service.RoomService;
import com.platform.service.UserService;
import com.platform.service.VirtualNetworkFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 * 处理用户认证、信息查询和网络状态相关的API请求
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RoomService roomService;
    private final VirtualNetworkFactory virtualNetworkFactory;

    @Autowired
    public UserController(UserService userService, RoomService roomService,
                          VirtualNetworkFactory virtualNetworkFactory) {
        this.userService = userService;
        this.roomService = roomService;
        this.virtualNetworkFactory = virtualNetworkFactory;
    }

    /**
     * 用户注册/登录接口
     * 如果用户不存在则注册新用户，存在则验证密码并登录
     *
     * @param request HTTP请求体，包含username和password
     * @param httpRequest HTTP请求对象，用于获取客户端IP
     * @param session HTTP会话对象
     * @return 用户信息或错误消息
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request,
                                   HttpServletRequest httpRequest,
                                   HttpSession session) {
        String username = request.get("username");
        String password = request.get("password");

        // 验证输入参数
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户名不能为空"));
        }

        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("密码不能为空"));
        }

        String clientAddress = httpRequest.getRemoteAddr();
        String sessionId = session.getId();

        // 检查用户名是否已存在
        User existingUser = userService.findByUsername(username);
        if (existingUser != null) {
            // 用户存在，验证密码
            if (!userService.validatePassword(existingUser, password)) {
                return ResponseEntity.badRequest().body(createErrorResponse("密码错误"));
            }

            // 密码正确，更新会话
            User updatedUser = userService.updateUserSession(existingUser, clientAddress, sessionId);
            return ResponseEntity.ok(createLoginResponse(updatedUser, "登录成功"));
        }

        // 新用户，注册
        User newUser = userService.registerUser(username, password, clientAddress, sessionId);
        if (newUser == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("注册失败"));
        }

        return ResponseEntity.ok(createLoginResponse(newUser, "注册并登录成功"));
    }

    /**
     * 用户登出接口
     *
     * @param session HTTP会话对象
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        boolean success = userService.logoutUser(session.getId());

        if (success) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "登出成功");
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录或会话已失效"));
        }
    }

    /**
     * 获取当前用户信息
     *
     * @param session HTTP会话对象
     * @return 当前登录用户的信息
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = userService.findBySessionId(session.getId());
        if (user == null || !user.isActive()) {
            return ResponseEntity.notFound().build();
        }
        userService.updateUserActivity(session.getId());

        return ResponseEntity.ok(createUserInfoResponse(user));
    }

    /**
     * 获取所有活跃用户列表
     *
     * @return 当前在线的所有用户列表
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllActiveUsers() {
        return ResponseEntity.ok(userService.getAllActiveUsers());
    }

    /**
     * 获取当前用户的网络连接信息
     * 包括虚拟IP、房间网络配置等
     *
     * @param session HTTP会话对象
     * @return 用户网络信息
     */
    @GetMapping("/network-info")
    public ResponseEntity<?> getUserNetworkInfo(HttpSession session) {
        // 通过会话ID查找用户
        User user = userService.findBySessionId(session.getId());
        if (user == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "用户未登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // 构建网络信息响应
        Map<String, Object> networkInfo = new HashMap<>();
        networkInfo.put("username", user.getUsername());
        networkInfo.put("virtualIp", user.getVirtualIp());
        networkInfo.put("inRoom", user.getRoomId() > 0);
        networkInfo.put("roomId", user.getRoomId());

        // 如果用户在房间中，添加房间的网络详情
        if (user.getRoomId() > 0) {
            Room room = roomService.getRoomInfo(user.getRoomId());
            if (room != null) {
                networkInfo.put("roomName", room.getName());
                networkInfo.put("networkId", room.getNetworkId());
                networkInfo.put("networkName", room.getNetworkName());
                networkInfo.put("networkType", room.getNetworkType());
                networkInfo.put("networkSecret", room.getNetworkSecret());
                networkInfo.put("supernode", virtualNetworkFactory.getSuperNodeAddress());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", networkInfo);

        return ResponseEntity.ok(response);
    }

    /**
     * 创建错误响应
     */
    private Map<String, String> createErrorResponse(String errorMessage) {
        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);
        return response;
    }

    /**
     * 创建登录成功响应
     */
    private Map<String, Object> createLoginResponse(User user, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("message", message);
        return response;
    }

    /**
     * 创建用户信息响应
     */
    private Map<String, Object> createUserInfoResponse(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("clientAddress", user.getClientAddress());
        userInfo.put("virtualIp", user.getVirtualIp());
        userInfo.put("active", user.isActive());
        userInfo.put("lastActiveTime", user.getLastActiveTime());
        return userInfo;
    }
}