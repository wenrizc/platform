package com.platform.service;

import java.util.Map;

/**
 * N2N网络服务接口
 * 用于创建和管理虚拟网络，为房间中的用户提供P2P连接支持
 */
public interface N2nService {

    /**
     * 创建一个新的虚拟网络
     * @return 网络ID
     */
    String createNetwork();

    /**
     * 删除指定的虚拟网络
     * @param networkId 网络ID
     * @return 是否成功删除
     */
    boolean deleteNetwork(String networkId);

    /**
     * 为用户分配虚拟IP
     * @param username 用户名
     * @param networkId 网络ID
     * @return 分配的虚拟IP
     */
    String assignIpAddress(String username, String networkId);

    /**
     * 为用户移除虚拟IP
     * @param username 用户名
     * @param networkId 网络ID
     * @return 是否成功移除
     */
    boolean removeIpAddress(String username, String networkId);

    /**
     * 获取网络配置信息
     * @param networkId 网络ID
     * @return 包含网络配置的Map
     */
    Map<String, Object> getNetworkInfo(String networkId);

    /**
     * 生成随机的网络密钥
     * @return 随机生成的密钥
     */
    String generateNetworkSecret();
}