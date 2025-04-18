package com.platform.service;

import com.platform.service.impl.n2n.N2NVirtualNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟网络工厂
 * <p>
 * 管理不同类型的虚拟网络服务，提供服务实例获取和网络资源管理功能
 * </p>
 */
@Component
public class VirtualNetworkFactory {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VirtualNetworkFactory.class);

    @Value("${virtual.network.default:N2N}")
    private String networkType;

    private final Map<String, VirtualNetworkService> serviceMap = new ConcurrentHashMap<>();

    /**
     * 构造函数，注入并初始化可用的虚拟网络服务
     *
     * @param serviceImpls 所有实现了VirtualNetworkService的服务
     */
    @Autowired
    public VirtualNetworkFactory(Map<String, VirtualNetworkService> serviceImpls) {
        serviceImpls.forEach((name, service) ->
                serviceMap.put(service.getTechnologyName().toUpperCase(), service));
    }

    /**
     * 获取默认的虚拟网络服务
     *
     * @return 默认配置的虚拟网络服务
     */
    public VirtualNetworkService getService() {
        return getService(networkType);
    }

    /**
     * 获取指定类型的虚拟网络服务
     *
     * @param type 网络类型名称
     * @return 对应类型的虚拟网络服务
     * @throws IllegalArgumentException 当请求的网络类型不支持时
     */
    public VirtualNetworkService getService(String type) {
        if (type == null || type.isEmpty()) {
            type = networkType;
        }
        VirtualNetworkService service = serviceMap.get(type.toUpperCase());
        if (service == null) {
            throw new IllegalArgumentException("不支持的虚拟网络类型: " + type);
        }
        return service;
    }

    /**
     * 获取所有可用的虚拟网络服务
     *
     * @return 所有注册的服务映射
     */
    public Map<String, VirtualNetworkService> getAllServices() {
        return serviceMap;
    }

    /**
     * 获取N2N超级节点地址
     *
     * @return 超级节点地址
     * @throws UnsupportedOperationException 当默认服务不是N2N类型时
     */
    public String getSuperNodeAddress() {
        VirtualNetworkService service = getService();
        if (service instanceof N2NVirtualNetworkService) {
            return ((N2NVirtualNetworkService) service).getSuperNodeAddress();
        } else {
            throw new UnsupportedOperationException(
                    "不支持在 " + service.getTechnologyName() + " 类型网络上获取超级节点地址");
        }
    }

    /**
     * 执行定时任务检查所有虚拟网络状态
     */
    public void checkNetworkStatus() {
        VirtualNetworkService service = getService();
        try {
            Map<String, Object> status = service.getNetworkInfo(null);
            if (status != null && status.containsKey("status")) {
                String healthStatus = (String) status.get("status");
                if (!"healthy".equalsIgnoreCase(healthStatus)) {
                    logger.warn("虚拟网络状态异常: {}", healthStatus);
                }
            }
        } catch (Exception e) {
            logger.error("检查虚拟网络状态时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理所有未使用的虚拟网络
     * 遍历所有网络服务，清理没有活跃用户的网络
     */
    public void cleanupUnusedNetworks() {
        Map<String, VirtualNetworkService> services = getAllServices();
        for (Map.Entry<String, VirtualNetworkService> entry : services.entrySet()) {
            String networkType = entry.getKey();
            VirtualNetworkService service = entry.getValue();

            try {
                Map<String, Object> networkStats = service.getNetworkInfo(null);
                if (networkStats == null || !networkStats.containsKey("networks")) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> networks = (List<Map<String, Object>>) networkStats.get("networks");
                int cleanedCount = cleanInactiveNetworks(networks, service, networkType);

                if (cleanedCount > 0) {
                    logger.info("{}网络清理完成，共清理 {} 个未使用网络", networkType, cleanedCount);
                } else {
                    logger.debug("{}网络清理完成，未发现需要清理的网络", networkType);
                }
            } catch (Exception e) {
                logger.error("清理{}网络资源时发生错误: {}", networkType, e.getMessage(), e);
            }
        }
    }

    /**
     * 清理不活跃的网络资源
     *
     * @param networks 网络列表
     * @param service 虚拟网络服务
     * @param networkType 网络类型
     * @return 已清理的网络数量
     */
    private int cleanInactiveNetworks(List<Map<String, Object>> networks, VirtualNetworkService service, String networkType) {
        int cleanedCount = 0;
        // 设置清理阈值：24小时未活动的网络将被清理
        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);

        for (Map<String, Object> network : networks) {
            String networkId = (String) network.get("networkId");
            Instant lastActive = (Instant) network.get("lastActiveTime");
            int userCount = (Integer) network.getOrDefault("activeUsers", 0);

            // 只清理没有活跃用户且超过阈值时间的网络
            if (userCount == 0 && lastActive != null && lastActive.isBefore(threshold)) {
                if (service.deleteNetwork(networkId)) {
                    cleanedCount++;
                    logger.info("已清理未使用的{}网络: {}, 最后活跃时间: {}", networkType, networkId, lastActive);
                }
            }
        }
        return cleanedCount;
    }
}