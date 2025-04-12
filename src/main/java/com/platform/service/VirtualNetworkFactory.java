package com.platform.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VirtualNetworkFactory {

    @Value("${virtual.network.default:N2N}")
    private String networkType;

    private final Map<String, VirtualNetworkService> serviceMap = new ConcurrentHashMap<>();

    @Autowired
    public VirtualNetworkFactory(Map<String, VirtualNetworkService> serviceImpls) {
        serviceImpls.forEach((name, service) ->
                serviceMap.put(service.getTechnologyName().toUpperCase(), service));
    }

    public VirtualNetworkService getService() {
        return getService(networkType);
    }

    public VirtualNetworkService getService(String type) {
        if (type == null || type.isEmpty()) {
            type = networkType;
        }
        VirtualNetworkService service = serviceMap.get(type.toUpperCase());
        if (service == null) {
            throw new IllegalArgumentException("Unsupported virtual network type: " + type);
        }
        return service;
    }

    public Map<String, VirtualNetworkService> getAllServices() {
        return serviceMap;
    }
}