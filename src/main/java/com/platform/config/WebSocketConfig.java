package com.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket配置类
 * <p>
 * 配置WebSocket端点、消息代理和传输参数，启用STOMP协议支持，
 * 并处理会话ID的传递
 * </p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置STOMP端点
     * 客户端通过这些端点连接到WebSocket服务器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setInterceptors(new SessionIdHandshakeInterceptor());
    }

    /**
     * 配置消息代理
     * 定义消息路由规则和目的地前缀
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端订阅前缀
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 用户专属消息前缀 (如 /user/queue/messages)
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 配置WebSocket传输参数
     * 设置消息大小限制和超时时间
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(128 * 1024)     // 消息大小限制为128KB
                .setSendTimeLimit(15 * 1000)         // 发送超时15秒
                .setSendBufferSizeLimit(512 * 1024); // 发送缓冲区大小512KB
    }

    /**
     * 会话ID处理拦截器
     * 从URL参数中提取sessionId并添加到WebSocket会话属性中
     */
    private static class SessionIdHandshakeInterceptor extends HttpSessionHandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest) {
                ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                String sessionId = servletRequest.getServletRequest().getParameter("sessionId");
                if (sessionId != null && !sessionId.isEmpty()) {
                    attributes.put("sessionId", sessionId);
                }
            }
            return super.beforeHandshake(request, response, wsHandler, attributes);
        }
    }
}