package com.platform.config;

/**
 * N2N网络配置
 */
public class N2nConfig {
    private String supernode = "127.0.0.1:9527";  // 默认指向本地9527端口
    private String subnet = "10.0.0.0/24";
    private int maxUsersPerNetwork = 250;
    private boolean autoReconnect = true;
    private String n2nPath = "";  // N2N可执行文件路径，如果添加到PATH中可以为空

    public String getSupernode() {
        return supernode;
    }

    public void setSupernode(String supernode) {
        this.supernode = supernode;
    }

    public String getSubnet() {
        return subnet;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }

    public int getMaxUsersPerNetwork() {
        return maxUsersPerNetwork;
    }

    public void setMaxUsersPerNetwork(int maxUsersPerNetwork) {
        this.maxUsersPerNetwork = maxUsersPerNetwork;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public String getN2nPath() {
        return n2nPath;
    }

    public void setN2nPath(String n2nPath) {
        this.n2nPath = n2nPath;
    }
}