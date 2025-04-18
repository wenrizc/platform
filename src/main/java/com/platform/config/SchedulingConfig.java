package com.platform.config;

import com.platform.service.RoomService;
import com.platform.service.UserService;
import com.platform.service.VirtualNetworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 系统定时任务配置
 * 管理各类资源的定期维护任务
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfig.class);

    private final UserService userService;
    private final RoomService roomService;
    private final VirtualNetworkFactory virtualNetworkFactory;

    @Autowired
    public SchedulingConfig(UserService userService, RoomService roomService,
                            VirtualNetworkFactory virtualNetworkFactory) {
        this.userService = userService;
        this.roomService = roomService;
        this.virtualNetworkFactory = virtualNetworkFactory;
    }

    /**
     * 每月1号0点清理不活跃用户
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void cleanupInactiveUsers() {
        logger.info("执行定时任务: 清理不活跃用户");
        userService.cleanupInactiveUsers();
    }

    /**
     * 每小时清理空房间
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupEmptyRooms() {
        logger.info("执行定时任务: 清理空房间");
        roomService.cleanupEmptyRooms();
    }

    /**
     * 每5分钟刷新可加入房间列表
     */
    @Scheduled(fixedRate = 300000)
    public void refreshJoinableRooms() {
        logger.info("执行定时任务: 刷新可加入房间列表并清理数据");
        roomService.getJoinableRooms();
    }

    /**
     * 清理未使用的虚拟网络资源
     * 默认每6小时执行一次，可通过配置修改
     */
    @Scheduled(cron = "${virtual.network.cleanup.cron-expression:0 0 */6 * * *}")
    public void cleanupUnusedNetworks() {
        logger.info("执行定时任务: 清理未使用的虚拟网络资源");
        virtualNetworkFactory.cleanupUnusedNetworks();
    }

    /**
     * 每15分钟检查虚拟网络状态
     */
    @Scheduled(fixedRate = 900000)
    public void checkNetworkStatus() {
        logger.info("执行定时任务: 检查虚拟网络状态");
        virtualNetworkFactory.checkNetworkStatus();
    }
}