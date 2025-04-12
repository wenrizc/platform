package com.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "virtual.network")
public class VirtualNetworkProperties {

    private String defaultNetwork = "N2N";
    private N2nConfig n2n = new N2nConfig();

    public static class N2nConfig {
        private String supernode = "supernodes.n2n.example.com:7654";
        private String subnet = "10.0.0.0/24";

        // getters and setters
        public String getSupernode() { return supernode; }
        public void setSupernode(String supernode) { this.supernode = supernode; }
        public String getSubnet() { return subnet; }
        public void setSubnet(String subnet) { this.subnet = subnet; }
    }



    // getters and setters
    public String getdefaultNetwor() { return defaultNetwork; }
    public void setdefaultNetwor(String defaultNetwork) { this.defaultNetwork = defaultNetwork; }
    public N2nConfig getN2n() { return n2n; }
    public void setN2n(N2nConfig n2n) { this.n2n = n2n; }
}