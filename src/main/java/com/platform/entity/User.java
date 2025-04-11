package com.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "client_address")
    private String clientAddress;

    @Column(name = "session_id", unique = true)
    private String sessionId;

    @Column(name = "last_active_time")
    private Instant lastActiveTime;

    @Column(name = "virtual_ip")
    private String virtualIp; // n2n虚拟IP地址，创建或加入房间时分配

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "room_id")
    private long roomId;

    public User() {
    }

    public User(String username, String password, String clientAddress, String sessionId) {
        this.username = username;
        this.password = password;
        this.clientAddress = clientAddress;
        this.sessionId = sessionId;
        this.lastActiveTime = Instant.now();
        this.active = true;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Instant getLastActiveTime() {
        return lastActiveTime;
    }

    public void updateLastActiveTime() {
        this.lastActiveTime = Instant.now();
    }

    public String getVirtualIp() {
        return virtualIp;
    }

    public void setVirtualIp(String virtualIp) {
        this.virtualIp = virtualIp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setLastActiveTime(long l) {this.lastActiveTime = Instant.ofEpochMilli(l);
    }

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }
}