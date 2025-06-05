package project.status_service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
public class WebSocketStatusController {

    private final SimpMessagingTemplate messagingTemplate;
    private final StatusService statusService;

    private final StatusProperties statusProperties;

    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    @Value("${server.port}")
    private String currentPort;

    public WebSocketStatusController(SimpMessagingTemplate messagingTemplate, StatusService statusService,
            StatusProperties statusProperties) {
        this.messagingTemplate = messagingTemplate;
        this.statusService = statusService;
        this.statusProperties = statusProperties;
    }

    @MessageMapping("/status")
    public void handleStatus(StatusRequest req, SimpMessageHeaderAccessor accessor) {
        // Store the session ID and username in a map for later use
        String sessionId = accessor.getSessionId();
        sessionUserMap.put(sessionId, req.getUsername());
        System.out.printf("[WS RECEIVE] %s -> '%s' from session %s%n",
                req.getUsername(), req.getStatusText(), sessionId);

        Status old = statusService.findByUsername(req.getUsername());
        Status status = statusService.saveOrUpdate(req);

        if (old == null) {
            System.out.printf("[WS CREATE] %s -> '%s'%n", status.getUsername(), status.getStatustext());
        } else {
            System.out.printf("[WS UPDATE] %s: '%s' -> '%s'%n",
                    old.getUsername(), old.getStatustext(), status.getStatustext());
        }

        statusProperties.getPeers().forEach(peer -> {
            if (!peer.contains(currentPort)) {
                try {
                    new RestTemplate().postForObject(peer + "/status/replicate", status, Void.class);
                    System.out.println("[WS -> PEER] Replicated to " + peer);
                } catch (Exception e) {
                    System.err.println("Failed to replicate to peer " + peer + ": " + e.getMessage());
                }
            }
        });

        messagingTemplate.convertAndSend("/topic/status", status);
    }

    @MessageMapping("/request-statuses")
    public void sendStatusesToClient(SimpMessageHeaderAccessor accessor) {
        System.out.println("[WS] Client requested all statuses");
        statusService.all().forEach(status -> messagingTemplate.convertAndSend("/topic/init-status", status));
    }

    public void broadcastDelete(Long id) {
        messagingTemplate.convertAndSend("/topic/status-delete", id);
    }

    public void broadcastStatus(Status status) {
        messagingTemplate.convertAndSend("/topic/status", status);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        System.out.println("[WS] Client connected with session ID: " + sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        System.out.println("[WS] Client disconnected with session ID: " + sessionId);

        String username = sessionUserMap.remove(sessionId);
        if (username != null) {
            handleUserDisconnect(username);
        }
    }

    private void handleUserDisconnect(String username) {
        Status status = statusService.findByUsername(username);
        if (status != null) {
            System.out.printf("[WS DISCONNECT] Removing status for user: %s%n", username);

            // Delete the status
            statusService.delete(status.getId());

            // Notify peers
            statusProperties.getPeers().forEach(peer -> {
                if (!peer.contains(currentPort)) {
                    try {
                        new RestTemplate().delete(peer + "/status/replicate/" + status.getId());
                        System.out.println("[WS DISCONNECT -> PEER] Replicated delete to " + peer);
                    } catch (Exception e) {
                        System.err.println("Failed to replicate delete to peer " + peer + ": " + e.getMessage());
                    }
                }
            });

            // Broadcast deletion to other clients
            broadcastDelete(status.getId());
        }
    }
}
