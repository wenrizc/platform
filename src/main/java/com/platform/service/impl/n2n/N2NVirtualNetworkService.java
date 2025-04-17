package com.platform.service.impl.n2n;

import com.platform.config.VirtualNetworkProperties;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * N2N虚拟网络服务实现
 * <p>
 * 通过命令行调用N2N工具创建和管理虚拟网络
 * </p>
 */
@Service("N2N")
public class N2NVirtualNetworkService extends AbstractVirtualNetworkService {

    private static final Logger logger = LoggerFactory.getLogger(N2NVirtualNetworkService.class);

    private final VirtualNetworkProperties networkProperties;

    private final Map<String, NetworkInfo> networksMap = new ConcurrentHashMap<>();
    private final Map<String, String> ipAssignments = new ConcurrentHashMap<>();

    @Autowired
    public N2NVirtualNetworkService(VirtualNetworkProperties networkProperties) {
        this.networkProperties = networkProperties;
    }

    @Override
    public String createNetwork() {
        // 获取N2N配置
        VirtualNetworkProperties.N2nConfig config = networkProperties.getN2n();
        String supernode = config.getSupernode();

        // 检查超级节点连接
        boolean supernodeAvailable = checkSupernodeConnection(supernode);
        if (!supernodeAvailable) {
            logger.warn("N2N超级节点连接检查失败，但仍将继续创建网络");
        }

        // 生成唯一网络ID
        String networkId = generateRandomId();

        // 创建网络信息对象
        NetworkInfo networkInfo = new NetworkInfo();
        networkInfo.setNetworkId(networkId);
        networkInfo.setCreationTime(Instant.now());
        networkInfo.setLastActiveTime(Instant.now());
        networkInfo.setSubnet(networkProperties.getN2n().getSubnet());
        networkInfo.setSupernode(networkProperties.getN2n().getSupernode());

        // 存储网络信息
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

        // 基于用户名生成固定IP地址
        String ip = generateIpFromUsername(username, networkInfo.getSubnet());
        ipAssignments.put(key, ip);

        // 更新网络活动时间
        networkInfo.setLastActiveTime(Instant.now());

        logger.info("为用户 {} 在网络 {} 中分配IP: {}", username, networkId, ip);
        return ip;
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

        } else {
            // 返回指定网络的详细信息
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
        }

        return result;
    }

    @Override
    public String getConnectionCommand(String networkName, String networkSecret) {
        VirtualNetworkProperties.N2nConfig config = networkProperties.getN2n();

        // 构建edge命令行
        StringBuilder command = new StringBuilder();
        command.append("edge -c ").append(networkName);
        command.append(" -k ").append(networkSecret);
        command.append(" -a dhcp:0.0.0.0");  // 使用DHCP自动获取IP
        command.append(" -l ").append(config.getSupernode());

        if (config.isAutoReconnect()) {
            command.append(" -r");
        }

        return command.toString();
    }

    @Override
    public String getTechnologyName() {
        return "N2N";
    }

    /**
     * 基于用户名和子网生成固定IP地址
     *
     * @param username 用户名
     * @param subnet 子网CIDR表示法
     * @return 生成的IP地址
     */
    private String generateIpFromUsername(String username, String subnet) {
        try {
            // 解析子网
            String[] parts = subnet.split("/");
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
            byte[] digest = md.digest(username.getBytes(StandardCharsets.UTF_8));

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

        } catch (UnknownHostException | NoSuchAlgorithmException e) {
            logger.error("生成IP地址时出错", e);
            // 回退方案，生成10.0.0.x格式的地址
            int fallbackIP = Math.abs(username.hashCode() % 250) + 1;
            return "10.0.0." + fallbackIP;
        }
    }

    /**
     * 检查端口是否开放
     *
     * @param host 主机名或IP地址
     * @param port 端口号
     * @return 端口是否开放
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
     * 检查N2N超级节点是否可连接
     *
     * @param supernode 超级节点地址 (host:port格式)
     * @return 超级节点是否可连接
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

    /**
     * 内部网络信息存储类
     */
    private static class NetworkInfo {
        private String networkId;
        private Instant creationTime;
        private Instant lastActiveTime;
        private String subnet;
        private String supernode;

        public String getNetworkId() {
            return networkId;
        }

        public void setNetworkId(String networkId) {
            this.networkId = networkId;
        }

        public Instant getCreationTime() {
            return creationTime;
        }

        public void setCreationTime(Instant creationTime) {
            this.creationTime = creationTime;
        }

        public Instant getLastActiveTime() {
            return lastActiveTime;
        }

        public void setLastActiveTime(Instant lastActiveTime) {
            this.lastActiveTime = lastActiveTime;
        }

        public String getSubnet() {
            return subnet;
        }

        public void setSubnet(String subnet) {
            this.subnet = subnet;
        }

        public String getSupernode() {
            return supernode;
        }

        public void setSupernode(String supernode) {
            this.supernode = supernode;
        }
    }
}