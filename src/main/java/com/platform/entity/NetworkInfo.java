package com.platform.entity;

import java.time.Instant;

public class NetworkInfo {
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
