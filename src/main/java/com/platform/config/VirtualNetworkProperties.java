package com.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 虚拟网络配置属性类
 * 支持多种虚拟网络技术的配置
 */
@Component
@ConfigurationProperties(prefix = "virtual.network")
public class VirtualNetworkProperties {

    /**
     * 默认网络技术类型
     */
    private String defaultNetwork = "N2N";

    /**
     * 清理任务配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    /**
     * 各种网络技术的配置
     */
    private N2nConfig n2n = new N2nConfig();
    private Map<String, Object> customNetworks = new HashMap<>();

    /**
     * 清理任务配置类
     */
    public static class CleanupConfig {
        private boolean enabled = true;
        private String cronExpression = "0 0 */6 * * *"; // 默认每6小时执行一次
        private int inactiveTimeoutDays = 30; // 默认30天不活跃视为可清理

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
        public int getInactiveTimeoutDays() { return inactiveTimeoutDays; }
        public void setInactiveTimeoutDays(int inactiveTimeoutDays) { this.inactiveTimeoutDays = inactiveTimeoutDays; }
    }

    /**
     * N2N网络配置
     */
    public static class N2nConfig {
        private String supernode = "supernodes.n2n.example.com:7654";
        private String subnet = "10.0.0.0/24";
        private int maxUsersPerNetwork = 250;
        private boolean autoReconnect = true;

        public String getSupernode() { return supernode; }
        public void setSupernode(String supernode) { this.supernode = supernode; }
        public String getSubnet() { return subnet; }
        public void setSubnet(String subnet) { this.subnet = subnet; }
        public int getMaxUsersPerNetwork() { return maxUsersPerNetwork; }
        public void setMaxUsersPerNetwork(int maxUsersPerNetwork) { this.maxUsersPerNetwork = maxUsersPerNetwork; }
        public boolean isAutoReconnect() { return autoReconnect; }
        public void setAutoReconnect(boolean autoReconnect) { this.autoReconnect = autoReconnect; }
    }


    // Getters and Setters
    public String getDefaultNetwork() { return defaultNetwork; }
    public void setDefaultNetwork(String defaultNetwork) { this.defaultNetwork = defaultNetwork; }
    public N2nConfig getN2n() { return n2n; }
    public void setN2n(N2nConfig n2n) { this.n2n = n2n; }
    public CleanupConfig getCleanup() { return cleanup; }
    public void setCleanup(CleanupConfig cleanup) { this.cleanup = cleanup; }
    public Map<String, Object> getCustomNetworks() { return customNetworks; }
    public void setCustomNetworks(Map<String, Object> customNetworks) { this.customNetworks = customNetworks; }
}