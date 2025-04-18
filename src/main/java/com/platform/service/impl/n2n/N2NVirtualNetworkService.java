package com.platform.service.impl.n2n;

import com.platform.config.N2nConfig;
import com.platform.config.VirtualNetworkProperties;
import com.platform.entity.NetworkInfo;
import com.platform.service.impl.AbstractVirtualNetworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * N2N虚拟网络服务实现
 * 通过N2N技术创建和管理虚拟网络连接，支持P2P通信
 */
@Service("N2N")
public class N2NVirtualNetworkService extends AbstractVirtualNetworkService {

    private static final Logger logger = LoggerFactory.getLogger(N2NVirtualNetworkService.class);

    private final Map<String, NetworkInfo> networksMap = new ConcurrentHashMap<>();
    private final Map<String, String> ipAssignments = new ConcurrentHashMap<>();

    @Override
    public String createNetwork() {
        String supernode = N2nConfig.getSupernode();

        // 检查超级节点连接
        if (!checkSupernodeConnection(supernode)) {
            logger.warn("N2N超级节点连接检查失败，但仍将继续创建网络");
        }

        // 使用基类方法生成唯一网络ID
        String networkId = generateRandomId();

        // 创建并保存网络信息
        NetworkInfo networkInfo = new NetworkInfo();
        networkInfo.setNetworkId(networkId);
        networkInfo.setCreationTime(Instant.now());
        networkInfo.setLastActiveTime(Instant.now());
        networkInfo.setSubnet(N2nConfig.getSubnet());
        networkInfo.setSupernode(supernode);

        networksMap.put(networkId, networkInfo);

        logger.info("创建N2N虚拟网络: {}, 子网: {}", networkId, networkInfo.getSubnet());
        return networkId;
    }

    @Override
    public boolean deleteNetwork(String networkId) {
        if (networkId == null || !networksMap.containsKey(networkId)) {
            logger.warn("尝试删除不存在的网络: {}", networkId);
            return false;
        }

        // 移除网络信息
        networksMap.remove(networkId);

        // 清理该网络的所有IP分配
        ipAssignments.entrySet().removeIf(entry -> entry.getKey().startsWith(networkId + "_"));

        logger.info("删除N2N虚拟网络: {}", networkId);
        return true;
    }

    @Override
    public String assignIpAddress(String username, String networkId) {
        if (networkId == null || !networksMap.containsKey(networkId)) {
            throw new IllegalArgumentException("无效的网络ID: " + networkId);
        }

        NetworkInfo networkInfo = networksMap.get(networkId);
        String key = networkId + "_" + username;

        // 检查是否已分配IP
        if (ipAssignments.containsKey(key)) {
            return ipAssignments.get(key);
        }

        // 使用同步块确保线程安全
        synchronized (this) {
            // 再次检查，防止在获取锁期间其他线程已分配IP
            if (ipAssignments.containsKey(key)) {
                return ipAssignments.get(key);
            }

            // 生成初始IP
            String ip = generateIpFromUsername(username, networkInfo.getSubnet());

            // 检查IP是否已被其他用户占用
            Set<String> usedIps = new HashSet<>(ipAssignments.values());

            // 如果初始IP已被占用，使用确定性算法尝试后续IP
            if (usedIps.contains(ip)) {
                ip = resolveIpConflict(ip, usedIps);
            }

            // 存储IP分配
            ipAssignments.put(key, ip);

            // 更新网络活动时间
            networkInfo.setLastActiveTime(Instant.now());

            logger.info("为用户 {} 在网络 {} 中分配IP: {}", username, networkId, ip);
            return ip;
        }
    }

    @Override
    public boolean removeIpAddress(String username, String networkId) {
        if (networkId == null) {
            return false;
        }

        String key = networkId + "_" + username;
        String ip = ipAssignments.remove(key);

        if (ip != null) {
            logger.info("从网络 {} 中移除用户 {} 的IP: {}", networkId, username, ip);
            return true;
        }

        logger.warn("尝试移除不存在的IP分配: 用户={}, 网络={}", username, networkId);
        return false;
    }

    @Override
    public Map<String, Object> getNetworkInfo(String networkId) {
        Map<String, Object> result = new HashMap<>();

        if (networkId == null) {
            // 返回所有网络的概览
            return getAllNetworksInfo(result);
        } else {
            // 返回指定网络的详细信息
            return getSingleNetworkInfo(networkId, result);
        }
    }

    @Override
    public String getConnectionCommand(String networkName, String networkSecret) {

        // 构建edge命令行
        StringBuilder command = new StringBuilder();
        command.append("edge -c ").append(networkName);
        command.append(" -k ").append(networkSecret);
        command.append(" -a dhcp:0.0.0.0");  // 使用DHCP自动获取IP
        command.append(" -l ").append(N2nConfig.getSupernode());

        if (N2nConfig.isAutoReconnect()) {
            command.append(" -r");
        }

        return command.toString();
    }

    @Override
    public String getTechnologyName() {
        return "N2N";
    }

    /**
     * 获取当前配置的N2N超级节点地址
     * @return 超级节点地址，格式为host:port
     */
    public String getSuperNodeAddress() {
        return N2nConfig.getSupernode();
    }

    /**
     * 获取所有网络的概览信息
     */
    private Map<String, Object> getAllNetworksInfo(Map<String, Object> result) {
        List<Map<String, Object>> networks = new ArrayList<>();

        for (Map.Entry<String, NetworkInfo> entry : networksMap.entrySet()) {
            NetworkInfo info = entry.getValue();
            Map<String, Object> networkData = new HashMap<>();
            networkData.put("networkId", info.getNetworkId());
            networkData.put("creationTime", info.getCreationTime());
            networkData.put("lastActiveTime", info.getLastActiveTime());
            networkData.put("subnet", info.getSubnet());
            networkData.put("supernode", info.getSupernode());

            // 计算活跃用户数
            int activeUsers = (int) ipAssignments.keySet().stream()
                    .filter(key -> key.startsWith(info.getNetworkId() + "_"))
                    .count();
            networkData.put("activeUsers", activeUsers);

            networks.add(networkData);
        }

        result.put("status", "healthy");
        result.put("networks", networks);
        result.put("totalNetworks", networks.size());
        return result;
    }

    /**
     * 获取单个网络的详细信息
     */
    private Map<String, Object> getSingleNetworkInfo(String networkId, Map<String, Object> result) {
        NetworkInfo info = networksMap.get(networkId);
        if (info == null) {
            result.put("status", "error");
            result.put("message", "网络不存在: " + networkId);
            return result;
        }

        result.put("status", "healthy");
        result.put("networkId", info.getNetworkId());
        result.put("creationTime", info.getCreationTime());
        result.put("lastActiveTime", info.getLastActiveTime());
        result.put("subnet", info.getSubnet());
        result.put("supernode", info.getSupernode());

        // 收集该网络的所有IP分配
        Map<String, String> users = new HashMap<>();
        ipAssignments.forEach((key, ip) -> {
            if (key.startsWith(networkId + "_")) {
                String user = key.substring(networkId.length() + 1);
                users.put(user, ip);
            }
        });

        result.put("users", users);
        result.put("activeUsers", users.size());
        return result;
    }

    /**
     * 基于用户名和子网生成固定IP地址
     * 确保相同用户名在相同子网中总是生成相同的IP
     */
    private String generateIpFromUsername(String username, String subnet) {
        try {
            String saltedUsername = username;

            // 解析子网
            String[] parts = subnet.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("无效的子网格式: " + subnet);
            }

            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            // 计算子网地址范围
            InetAddress inetAddress = InetAddress.getByName(networkAddress);
            byte[] networkBytes = inetAddress.getAddress();

            // 计算可用主机位数
            int hostBits = 32 - prefixLength;
            int maxHosts = (1 << hostBits) - 2;  // 减去网络地址和广播地址

            // 使用MD5哈希用户名生成唯一数字
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(saltedUsername.getBytes(StandardCharsets.UTF_8));

            // 取哈希的前4字节作为整数
            int hash = ((digest[0] & 0xFF) << 24) |
                    ((digest[1] & 0xFF) << 16) |
                    ((digest[2] & 0xFF) << 8) |
                    (digest[3] & 0xFF);

            // 映射到有效范围 (1 到 maxHosts)
            int hostPart = Math.abs(hash % maxHosts) + 1;

            // 计算网络部分掩码
            int mask = 0xFFFFFFFF << hostBits;

            // 将网络地址部分与主机部分合并
            int networkPart = byteArrayToInt(networkBytes) & mask;
            int ipInt = networkPart | hostPart;

            // 转换回IP地址字符串
            return intToIpString(ipInt);

        } catch (UnknownHostException | NoSuchAlgorithmException | IllegalArgumentException e) {
            logger.error("生成IP地址时出错", e);
            // 回退方案，生成10.0.0.x格式的地址
            int fallbackIP = Math.abs(username.hashCode() % 250) + 1;
            return "10.0.0." + fallbackIP;
        }
    }

    /**
     * 确定性解决IP冲突
     * 当初始IP冲突时，按顺序递增IP直到找到未被使用的IP
     */
    private String resolveIpConflict(String initialIp, Set<String> usedIps) {
        try {
            // 解析IP地址
            String[] parts = initialIp.split("\\.");
            if (parts.length != 4) {
                throw new IllegalArgumentException("无效的IP格式: " + initialIp);
            }

            int[] octets = new int[4];
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
            }

            // 限制尝试次数，避免无限循环
            int maxAttempts = 254;
            String candidateIp = initialIp;

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                // 递增最后一个字节
                octets[3] = (octets[3] + 1) % 256;

                // 如果最后一个字节溢出了，递增倒数第二个字节
                if (octets[3] == 0) {
                    octets[2] = (octets[2] + 1) % 256;

                    // 如果倒数第二个字节溢出了，递增倒数第三个字节
                    if (octets[2] == 0) {
                        octets[1] = (octets[1] + 1) % 256;

                        // 如果倒数第三个字节溢出了，递增第一个字节
                        if (octets[1] == 0) {
                            octets[0] = (octets[0] + 1) % 256;
                        }
                    }
                }

                // 避免保留地址（0.0.0.0和255.255.255.255）
                if ((octets[0] == 0 && octets[1] == 0 && octets[2] == 0 && octets[3] == 0) ||
                        (octets[0] == 255 && octets[1] == 255 && octets[2] == 255 && octets[3] == 255)) {
                    continue;
                }

                candidateIp = octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];

                // 检查生成的IP是否未被使用
                if (!usedIps.contains(candidateIp)) {
                    logger.debug("IP冲突已解决，原始IP: {}，分配IP: {}", initialIp, candidateIp);
                    return candidateIp;
                }
            }

            logger.warn("无法解决IP冲突，原始IP: {}, 已尝试次数: {}", initialIp, maxAttempts);
            return initialIp; // 返回原始IP，作为最后的回退选项
        } catch (Exception e) {
            logger.error("解决IP冲突时发生错误", e);
            return initialIp;
        }
    }

    /**
     * 检查N2N超级节点是否可连接
     */
    private boolean checkSupernodeConnection(String supernode) {
        try {
            String[] parts = supernode.split(":");
            if (parts.length != 2) {
                logger.error("无效的超级节点地址格式 (应为host:port): {}", supernode);
                return false;
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            boolean isOpen = isPortOpen(host, port);
            if (!isOpen) {
                logger.error("无法连接到N2N超级节点 {}:{}，请确保n2n supernode已启动", host, port);
            }

            return isOpen;
        } catch (NumberFormatException e) {
            logger.error("解析N2N超级节点端口时出错: {}", supernode, e);
            return false;
        }
    }

    /**
     * 检查端口是否开放
     */
    private boolean isPortOpen(String host, int port) {
        boolean isPortOpen = false;
        int timeout = ("localhost".equals(host) || "127.0.0.1".equals(host)) ? 1000 : 3000;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            isPortOpen = true;
            logger.debug("成功连接到端口 {}:{}", host, port);
        } catch (IOException e) {
            logger.warn("端口未开放: {}:{}", host, port);
        }

        return isPortOpen;
    }

    /**
     * 将字节数组转换为整数
     */
    private int byteArrayToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    /**
     * 将整数转换为IP地址字符串
     */
    private String intToIpString(int ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
    }
}