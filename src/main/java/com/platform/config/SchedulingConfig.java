package com.platform.config;

import com.platform.service.UserService;
import com.platform.service.RoomService;
import com.platform.service.VirtualNetworkService;
import com.platform.service.VirtualNetworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfig.class);

    private final UserService userService;
    private final RoomService roomService;
    private final VirtualNetworkFactory virtualNetworkFactory;
    private final VirtualNetworkProperties networkProperties;

    @Autowired
    public SchedulingConfig(UserService userService, RoomService roomService,
                            VirtualNetworkFactory virtualNetworkFactory,
                            VirtualNetworkProperties networkProperties) {
        this.userService = userService;
        this.roomService = roomService;
        this.virtualNetworkFactory = virtualNetworkFactory;
        this.networkProperties = networkProperties;
    }

    /**
     * 定时清理不活跃用户
     * 每月执行一次 (cron表达式：秒 分 时 日 月 周)
     */
    @Scheduled(cron = "0 0 0 1 * *")  // 每月1号0点执行
    public void cleanupInactiveUsers() {
        logger.info("执行定时任务: 清理不活跃用户");
        userService.cleanupInactiveUsers();
    }

    /**
     * 定时清理空房间
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * *") // 每小时整点执行
    public void cleanupEmptyRooms() {
        logger.info("执行定时任务: 清理空房间");
        roomService.cleanupEmptyRooms();
    }

    /**
     * 定期获取可加入的房间并清理过期数据
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 每5分钟
    public void refreshJoinableRooms() {
        logger.info("执行定时任务: 刷新可加入房间列表并清理数据");
        roomService.getJoinableRoomsWithCleanup();
    }

    /**
     * 清理未使用的虚拟网络资源
     */
    @Scheduled(cron = "${virtual.network.cleanup.cron-expression:0 0 */6 * * *}")
    public void cleanupUnusedNetworks() {
        if (!networkProperties.getCleanup().isEnabled()) {
            logger.debug("虚拟网络清理任务已禁用");
            return;
        }

        logger.info("执行定时任务: 清理未使用的虚拟网络资源");

        // 获取所有虚拟网络服务
        Map<String, VirtualNetworkService> services = virtualNetworkFactory.getAllServices();

        for (Map.Entry<String, VirtualNetworkService> entry : services.entrySet()) {
            String networkType = entry.getKey();
            VirtualNetworkService service = entry.getValue();

            try {
                // 获取所有网络信息并检查是否需要清理
                Map<String, Object> networkStats = service.getNetworkInfo(null);

                if (networkStats != null && networkStats.containsKey("networks")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> networks = (List<Map<String, Object>>) networkStats.get("networks");

                    int cleanedCount = 0;
                    for (Map<String, Object> network : networks) {
                        String networkId = (String) network.get("networkId");
                        Instant lastActive = (Instant) network.get("lastActiveTime");
                        int userCount = (Integer) network.getOrDefault("activeUsers", 0);

                        // 如果网络无活跃用户且超过指定时间未使用，则清理
                        if (userCount == 0 && lastActive != null &&
                                lastActive.isBefore(Instant.now().minus(
                                        networkProperties.getCleanup().getInactiveTimeoutDays(), ChronoUnit.DAYS))) {

                            boolean deleted = service.deleteNetwork(networkId);
                            if (deleted) {
                                cleanedCount++;
                                logger.info("已清理未使用的{}网络: {}, 最后活跃时间: {}",
                                        networkType, networkId, lastActive);
                            }
                        }
                    }

                    if (cleanedCount > 0) {
                        logger.info("{}网络清理完成，共清理 {} 个未使用网络", networkType, cleanedCount);
                    } else {
                        logger.debug("{}网络清理完成，未发现需要清理的网络", networkType);
                    }
                }
            } catch (Exception e) {
                logger.error("清理{}网络资源时发生错误: {}", networkType, e.getMessage(), e);
            }
        }
    }

    /**
     * 定期检查虚拟网络状态
     * 每15分钟执行一次
     */
    @Scheduled(fixedRate = 900000) // 每15分钟
    public void checkNetworkStatus() {
        logger.debug("执行定时任务: 检查虚拟网络状态");

        // 获取默认网络服务
        VirtualNetworkService service = virtualNetworkFactory.getService();

        try {
            // 获取网络状态信息
            Map<String, Object> status = service.getNetworkInfo(null);

            if (status != null && status.containsKey("status")) {
                String healthStatus = (String) status.get("status");
                if (!"healthy".equalsIgnoreCase(healthStatus)) {
                    logger.warn("虚拟网络状态异常: {}", healthStatus);
                    // 可以添加告警逻辑
                }
            }
        } catch (Exception e) {
            logger.error("检查虚拟网络状态时发生错误: {}", e.getMessage(), e);
        }
    }
}