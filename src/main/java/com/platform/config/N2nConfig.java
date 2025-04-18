package com.platform.config;

public class N2nConfig {
    private static final String supernode = "supernodes.n2n.example.com:7654";
    private static final String subnet = "10.0.0.0/24";
    private static final int maxUsersPerNetwork = 100;
    private static final boolean autoReconnect = true;

    public static String getSupernode() { return supernode; }
    public static String getSubnet() { return subnet; }
    public static int getMaxUsersPerNetwork() { return maxUsersPerNetwork; }
    public static boolean isAutoReconnect() { return autoReconnect; }
}