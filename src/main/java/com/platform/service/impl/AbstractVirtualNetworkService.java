package com.platform.service.impl;

import com.platform.service.VirtualNetworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 虚拟网络服务抽象基类
 * 实现了一些通用功能
 */
public abstract class AbstractVirtualNetworkService implements VirtualNetworkService {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

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
    protected String generateRandomId() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}