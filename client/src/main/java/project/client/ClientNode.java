package project.client;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.random.RandomGenerator;

@Component
public class ClientNode {

    @Bean
    public ApplicationRunner run() {
        return args -> {
            Scanner scanner = new Scanner(System.in);
            RestTemplate rest = new RestTemplate();
            String server = "http://server1:8080";

            WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
            client.setMessageConverter(new MappingJackson2MessageConverter());

            var sessionFuture = client.connectAsync("ws://server1:8080/status-websocket", new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    session.subscribe("/topic/status", new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return Status.class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            Status status = (Status) payload;
                            System.out.println("[PUSH] " + status.getUsername() + ": " + status.getStatustext() + " @ " + status.getUhrzeit());
                        }
                    });
                }
            });

            sessionFuture.get();

            while (true) {
                System.out.println("[1] New status [2] Query [3] Change [4] Delete [0] Exit");
                switch (scanner.nextLine()) {
                    case "1":
                        System.out.print("Name: ");
                        String user = scanner.nextLine();
                        System.out.print("Status: ");
                        String status = scanner.nextLine();
                        Status s = new Status(user, status, LocalDateTime.now());
                        rest.postForObject(server + "/status", s, Void.class);
                        break;

                    case "2":
                        System.out.print("Name: ");
                        String n = scanner.nextLine();
                        Status fetched = rest.getForObject(server + "/status/" + n, Status.class);
                        System.out.println(fetched != null ? fetched.getStatustext() : "Not Found.");
                        break;

                    case "3":
                        System.out.print("Name: ");
                        String u = scanner.nextLine();
                        System.out.print("New status: ");
                        String newStatus = scanner.nextLine();
                        Status update = new Status(u, newStatus, LocalDateTime.now());
                        rest.put(server + "/status/" + u, update);
                        break;

                    case "4":
                        System.out.print("Name: ");
                        String del = scanner.nextLine();
                        rest.delete(server + "/status/" + del);
                        break;

                    case "0":
                        return;
                }
            }
        };
    }
}