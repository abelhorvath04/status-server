package project.status_service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.scheduling.annotation.Scheduled;
import javax.annotation.PostConstruct;

@Controller
public class WebSocketStatusController {

    private final SimpMessagingTemplate messagingTemplate;
    private final StatusService statusService;
    private final StatusProperties statusProperties;
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenMap = new ConcurrentHashMap<>();

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
        String username = req.getUsername();

        String existingSession = getSessionIdForUsername(username);
        // Block if the username is already connected from a different session
        if (existingSession != null && !existingSession.equals(sessionId)) {
            System.out.printf("[BLOCKED] Username '%s' already connected on this server from another session%n", username);
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", "Username already in use.");
            return;
        }

        // Check all peer servers (excluding current) if the username is already active, block if found
        for (String peer : statusProperties.getPeers()) {
            if (peer.contains(currentPort)) continue;

            try {
                String url = peer + "/status/username-active?username=" + username;
                Boolean isActive = new RestTemplate().getForObject(url, Boolean.class);
                if (Boolean.TRUE.equals(isActive)) {
                    System.out.printf("[BLOCKED] Username '%s' already active on peer %s%n", username, peer);
                    messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", "Username already in use on another server.");
                    return;
                }
            } catch (Exception e) {
                System.err.println("Failed to check peer " + peer + ": " + e.getMessage());
            }
        }

        sessionUserMap.putIfAbsent(sessionId, username);
        lastSeenMap.put(username, System.currentTimeMillis());

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
            System.out.printf("[WS DISCONNECT] User '%s' disconnected — keeping status%n", username);
        }
    }

    // Checks if client with that username already exists
    @GetMapping("/status/username-active")
    public ResponseEntity<Boolean> isUsernameActive(@RequestParam String username) {
        return ResponseEntity.ok(sessionUserMap.containsValue(username));
    }

    // Returns the session ID for the given username if connected, otherwise null
    private String getSessionIdForUsername(String username) {
        return sessionUserMap.entrySet().stream()
                .filter(entry -> username.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
    @MessageMapping("/delete-status")
    public void handleStatusDelete(String username, SimpMessageHeaderAccessor accessor) {
        System.out.printf("[WS DELETE] User '%s' requested status deletion%n", username);

        Status status = statusService.findByUsername(username);
        if (status != null) {
            statusService.delete(status.getId());
            broadcastDelete(status.getId());

            statusProperties.getPeers().forEach(peer -> {
                if (!peer.contains(currentPort)) {
                    try {
                        new RestTemplate().delete(peer + "/status/replicate/" + status.getId());
                    } catch (Exception e) {
                        System.err.println("Failed to replicate delete: " + e.getMessage());
                    }
                }
            });
        }
    }

    @PostConstruct
    public void init() {
        System.out.println("[INIT] Inactivity cleaner scheduled");
    }

    @Scheduled(fixedRate = 5000) // 5 sec
    public void cleanupInactiveStatuses() {
        long now = System.currentTimeMillis();
        long timeout = 300 * 1000; // 5 min

        lastSeenMap.forEach((username, lastSeen) -> {
            if (now - lastSeen > timeout) {
                Status status = statusService.findByUsername(username);
                if (status != null) {
                    System.out.printf("[CLEANUP] Removing inactive user: %s%n", username);
                    statusService.delete(status.getId());
                    broadcastDelete(status.getId());

                    statusProperties.getPeers().forEach(peer -> {
                        if (!peer.contains(currentPort)) {
                            try {
                                new RestTemplate().delete(peer + "/status/replicate/" + status.getId());
                            } catch (Exception e) {
                                System.err.println("Failed to replicate delete: " + e.getMessage());
                            }
                        }
                    });

                    lastSeenMap.remove(username);
                }
            }
        });
    }
}
