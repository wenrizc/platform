package com.platform.service;

import com.platform.entity.User;
import com.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 用户服务
 * 负责用户账号管理、认证、会话维护和状态监控
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Value("${user.session.timeout-minutes:30}")
    private int userTimeoutMinutes;

    private final UserRepository userRepository;
    private final WebSocketService webSocketService;

    @Autowired
    public UserService(UserRepository userRepository, WebSocketService webSocketService,
                       VirtualNetworkFactory virtualNetworkFactory) {
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
    }

    /**
     * 根据用户ID查找用户
     *
     * @param userId 用户ID
     * @return 用户对象，不存在则返回null
     */
    public User findById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户对象，不存在则返回null
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据会话ID查找用户
     *
     * @param sessionId 会话ID
     * @return 用户对象，不存在则返回null
     */
    public User findBySessionId(String sessionId) {
        return userRepository.findBySessionId(sessionId);
    }

    /**
     * 获取所有活跃用户
     *
     * @return 活跃用户列表
     */
    public List<User> getAllActiveUsers() {
        return userRepository.findAllActiveUsers();
    }

    /**
     * 注册新用户
     *
     * @param username 用户名
     * @param password 密码
     * @param clientAddress 客户端地址
     * @param sessionId 会话ID
     * @return 注册成功的用户对象，用户名已存在则返回null
     */
    public User registerUser(String username, String password, String clientAddress, String sessionId) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            return null;
        }

        User user = new User(username, password, clientAddress, sessionId);
        User savedUser = userRepository.save(user);

        // 广播用户上线通知
        webSocketService.sendUserStatusUpdate(savedUser, true);
        logger.info("用户注册成功: {}", username);

        return savedUser;
    }

    /**
     * 验证用户密码
     *
     * @param user 用户对象
     * @param password 待验证的密码
     * @return 密码是否正确
     */
    public boolean validatePassword(User user, String password) {
        return user.getPassword().equals(password);
    }

    /**
     * 更新用户信息
     *
     * @param user 需要更新的用户对象
     * @return 更新后的用户对象
     */
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * 更新现有用户会话
     * 用户登录时调用
     *
     * @param existingUser 已存在的用户
     * @param clientAddress 客户端地址
     * @param sessionId 新的会话ID
     * @return 更新后的用户对象
     */
    public User updateUserSession(User existingUser, String clientAddress, String sessionId) {
        existingUser.setSessionId(sessionId);
        existingUser.setClientAddress(clientAddress);
        existingUser.updateLastActiveTime();
        existingUser.setActive(true);
        User updatedUser = userRepository.save(existingUser);

        // 广播用户上线通知
        webSocketService.sendUserStatusUpdate(updatedUser, true);
        logger.info("用户会话更新: {}", existingUser.getUsername());

        return updatedUser;
    }

    /**
     * 更新用户的会话ID
     * WebSocket连接时调用
     *
     * @param userId 用户ID
     * @param newSessionId 新的会话ID
     * @return 更新是否成功
     */
    public boolean updateUserSessionId(Long userId, String newSessionId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setSessionId(newSessionId);
            user.updateLastActiveTime();
            userRepository.save(user);
            return true;
        }
        return false;
    }

    /**
     * 更新用户活动时间
     * 用户活动时调用，延长会话有效期
     *
     * @param sessionId 会话ID
     * @return 更新后的用户对象，会话不存在则返回null
     */
    public User updateUserActivity(String sessionId) {
        User user = userRepository.findBySessionId(sessionId);
        if (user != null) {
            user.updateLastActiveTime();
            user.setActive(true);  // 确保用户状态为活跃
            return userRepository.save(user);
        } else {
            logger.warn("尝试更新不存在的用户会话: {}", sessionId);
            return null;
        }
    }

    /**
     * 用户退出登录
     *
     * @param sessionId 会话ID
     * @return 退出操作是否成功
     */
    public boolean logoutUser(String sessionId) {
        User user = userRepository.findBySessionId(sessionId);
        if (user != null) {
            user.setActive(false);
            userRepository.save(user);
            logger.info("用户登出: {}", user.getUsername());
            return true;
        }
        return false;
    }

    /**
     * 判断用户是否活跃
     * 基于用户活跃标志及最后活动时间
     *
     * @param user 用户对象
     * @return 用户是否处于活跃状态
     */
    public boolean isUserActive(User user) {
        if (user == null) return false;
        return user.isActive() &&
                Duration.between(user.getLastActiveTime(), Instant.now())
                        .compareTo(Duration.ofMinutes(userTimeoutMinutes)) <= 0;
    }

    /**
     * 清理长期不活跃的用户
     * 定时任务每月执行
     */
    public void cleanupInactiveUsers() {
        Instant cutoffTime = Instant.now().minus(Duration.ofDays(180)); // 半年不活跃的用户

        // 记录清理前的活跃用户数
        int activeUsers = userRepository.findAllActiveUsers().size();

        // 执行清理 - 直接删除不活跃用户
        int deletedCount = userRepository.deleteInactiveUsers(cutoffTime);

        logger.info("清理不活跃用户完成，截止时间: {}，清理前活跃用户: {}，删除用户数: {}",
                cutoffTime, activeUsers, deletedCount);
    }
}