package com.platform.service;

import java.util.Map;

/**
 * 虚拟局域网服务接口
 * 定义了创建和管理虚拟网络的通用操作
 */
public interface VirtualNetworkService {

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
     * 如果networkId为null，则返回所有网络的概览信息
     * @param networkId 网络ID
     * @return 包含网络配置的Map
     */
    Map<String, Object> getNetworkInfo(String networkId);

    /**
     * 生成随机的网络密钥
     * @return 随机生成的密钥
     */
    String generateNetworkSecret();

    /**
     * 获取连接指令
     * @param networkName 网络名称
     * @param networkSecret 网络密钥
     * @return 可执行的连接指令
     */
    String getConnectionCommand(String networkName, String networkSecret);

    /**
     * 获取虚拟网络技术名称
     * @return 技术名称(如"N2N"、"ZeroTier"等)
     */
    String getTechnologyName();
}