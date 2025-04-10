package com.platform.service.impl;

import com.platform.service.N2nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class N2nServiceImpl implements N2nService {

    private static final Logger logger = LoggerFactory.getLogger(N2nServiceImpl.class);

    @Override
    public String createNetwork() {
        // TODO: 调用外部N2N服务创建虚拟网络
        // 目前返回一个随机的网络ID作为示例
        String networkId = generateRandomId();
        logger.info("创建虚拟网络: {}", networkId);
        return networkId;
    }

    @Override
    public boolean deleteNetwork(String networkId) {
        // TODO: 调用外部N2N服务删除虚拟网络
        logger.info("删除虚拟网络: {}", networkId);
        return true; // 假设成功
    }

    @Override
    public String assignIpAddress(String username, String networkId) {
        // TODO: 调用外部N2N服务为用户分配虚拟IP
        // 这里简单生成一个10.0.0.x的IP作为示例
        int lastOctet = Math.abs(username.hashCode() % 250) + 1;
        String virtualIp = "10.0.0." + lastOctet;
        logger.info("为用户 {} 在网络 {} 中分配虚拟IP: {}", username, networkId, virtualIp);
        return virtualIp;
    }

    @Override
    public boolean removeIpAddress(String username, String networkId) {
        // TODO: 调用外部N2N服务移除用户的虚拟IP
        logger.info("移除用户 {} 在网络 {} 中的虚拟IP", username, networkId);
        return true; // 假设成功
    }

    @Override
    public Map<String, Object> getNetworkInfo(String networkId) {
        // TODO: 调用外部N2N服务获取网络信息
        Map<String, Object> networkInfo = new HashMap<>();
        networkInfo.put("networkId", networkId);
        networkInfo.put("networkName", "n2n_" + networkId);
        networkInfo.put("supernode", "supernodes.n2n.example.com:7654");
        networkInfo.put("subnet", "10.0.0.0/24");
        return networkInfo;
    }

    @Override
    public String generateNetworkSecret() {
        // 生成一个随机的16字节密钥并使用Base64编码
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 生成随机ID作为网络标识
     */
    private String generateRandomId() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}