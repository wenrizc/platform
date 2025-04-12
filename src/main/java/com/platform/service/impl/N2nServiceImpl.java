package com.platform.service.impl;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class N2nServiceImpl extends AbstractVirtualNetworkService {

    @Override
    public String createNetwork() {
        // TODO: 调用外部N2N服务创建虚拟网络
        String networkId = generateRandomId();
        logger.info("创建N2N虚拟网络: {}", networkId);
        return networkId;
    }

    @Override
    public boolean deleteNetwork(String networkId) {
        logger.info("删除N2N虚拟网络: {}", networkId);
        return true; // 假设成功
    }

    @Override
    public String assignIpAddress(String username, String networkId) {
        int lastOctet = Math.abs(username.hashCode() % 250) + 1;
        String virtualIp = "10.0.0." + lastOctet;
        logger.info("为用户 {} 在N2N网络 {} 中分配虚拟IP: {}", username, networkId, virtualIp);
        return virtualIp;
    }

    @Override
    public boolean removeIpAddress(String username, String networkId) {
        logger.info("移除用户 {} 在N2N网络 {} 中的虚拟IP", username, networkId);
        return true;
    }

    @Override
    public Map<String, Object> getNetworkInfo(String networkId) {
        Map<String, Object> networkInfo = new HashMap<>();
        networkInfo.put("networkId", networkId);
        networkInfo.put("networkName", "n2n_" + networkId);
        networkInfo.put("supernode", "supernodes.n2n.example.com:7654");
        networkInfo.put("subnet", "10.0.0.0/24");
        return networkInfo;
    }

    @Override
    public String getConnectionCommand(String networkName, String networkSecret) {
        return "edge -c " + networkName + " -k " + networkSecret + " -l supernode:1234 -a 10.0.0.x";
    }

    @Override
    public String getTechnologyName() {
        return "N2N";
    }
}