package com.platform.service;

import com.platform.entity.User;
import com.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Value("${user.session.timeout-minutes:30}")
    private int userTimeoutMinutes;

    private final UserRepository userRepository;
    private final WebSocketService webSocketService;

    @Autowired
    public UserService(UserRepository userRepository, WebSocketService webSocketService) {
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
    }

    /**
     * 获取用户超时时间
     */
    private Duration getUserTimeout() {
        return Duration.ofMinutes(userTimeoutMinutes);
    }

    /**
     * 注册新用户
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
     * 更新现有用户会话
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
     * 验证用户密码
     */
    public boolean validatePassword(User user, String password) {
        return user.getPassword().equals(password);
    }

    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据会话ID查找用户
     */
    public User findBySessionId(String sessionId) {
        return userRepository.findBySessionId(sessionId);
    }

    /**
     * 获取所有活跃用户
     */
    public List<User> getAllActiveUsers() {
        return userRepository.findAllActiveUsers();
    }

    /**
     * 更新用户活动时间
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
     * 用户退出
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
     * 为用户分配虚拟IP (房间创建或加入时)
     */
    public String assignVirtualIp(String username) {
        User user = userRepository.findByUsername(username);
        if (user != null && user.getVirtualIp() == null) {
            // TODO 需要从n2n服务获取IP

        }
        return user != null ? user.getVirtualIp() : null;
    }

    /**
     * 更新用户的虚拟IP
     */
    public User updateUserVirtualIp(String username, String virtualIp) {
        User user = userRepository.findByUsername(username);
        if (user != null) {
            user.setVirtualIp(virtualIp);
            return userRepository.save(user);
        }
        return null;
    }

    /**
     * 判断用户是否活跃
     */
    public boolean isUserActive(User user) {
        if (user == null) return false;
        return user.isActive() &&
                Duration.between(user.getLastActiveTime(), Instant.now())
                        .compareTo(getUserTimeout()) <= 0;
    }

    /**
     * 定时清理不活跃用户
     * 每半年执行一次 (cron表达式：秒 分 时 日 月 周)
     */
    @Scheduled(cron = "0 0 0 1 */6 *")  // 每半年的1号0点执行
    public void cleanupInactiveUsers() {
        Instant cutoffTime = Instant.now().minus(Duration.ofDays(180)); // 半年不活跃的用户

        // 记录之前的情况
        int activeUsers = userRepository.findAllActiveUsers().size();

        // 执行清理 - 直接删除不活跃用户而不是仅标记
        int deletedCount = userRepository.deleteInactiveUsers(cutoffTime);

        logger.info("清理不活跃用户完成，截止时间: {}，清理前活跃用户: {}，删除用户数: {}",
                cutoffTime, activeUsers, deletedCount);
    }

    /**
     * 更新用户的会话ID
     */
    public boolean updateUserSessionId(Long userId, String newSessionId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setSessionId(newSessionId);
            user.setLastActiveTime(System.currentTimeMillis());
            userRepository.save(user);
            return true;
        }
        return false;
    }
}