package project.client;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.random.RandomGenerator;

@Component
public class ClientNode {
    String id = String.format("%04d", RandomGenerator.getDefault().nextInt(10000));
    private final String nodeId = "node-" + id;

    @Bean
    public ApplicationRunner run() {
        return args -> {
            WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
            client.setMessageConverter(new MappingJackson2MessageConverter());

            var sessionFuture = client.connectAsync("ws://localhost:9000/status-websocket", new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    session.subscribe("/topic/status", new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return StatusMessage.class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            StatusMessage status = (StatusMessage) payload;
                            System.out.println("[" + nodeId + "] RECEIVED: " + status.getNodeId() + " is " + status.getStatus());
                        }
                    });

                    session.send("/app/status", new StatusMessage(nodeId, "UP"));

                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        session.send("/app/status", new StatusMessage(nodeId, "DOWN"));
                    }));
                }
            });

            StompSession session = sessionFuture.get();

            new java.util.concurrent.CountDownLatch(1).await();

        };
    }
}
