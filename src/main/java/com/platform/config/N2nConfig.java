package com.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class N2nConfig {

    @Value("${virtual.network.n2n.supernode}")
    private String supernode;

    @Value("${virtual.network.n2n.subnet}")
    private String subnet;

    @Value("${virtual.network.n2n.max-users-per-network}")
    private int maxUsersPerNetwork;

    @Value("${virtual.network.n2n.auto-reconnect}")
    private boolean autoReconnect;

    public String getSupernode() {
        return supernode;
    }

    public String getSubnet() {
        return subnet;
    }

    public int getMaxUsersPerNetwork() {
        return maxUsersPerNetwork;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }
}