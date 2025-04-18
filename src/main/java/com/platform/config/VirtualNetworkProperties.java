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
     * 各种网络技术的配置
     */
    private Map<String, Object> customNetworks = new HashMap<>();

    public String getDefaultNetwork() { return defaultNetwork; }
    public void setDefaultNetwork(String defaultNetwork) { this.defaultNetwork = defaultNetwork; }
    public Map<String, Object> getCustomNetworks() { return customNetworks; }
    public void setCustomNetworks(Map<String, Object> customNetworks) { this.customNetworks = customNetworks; }
}