package project.status_service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import javax.annotation.PostConstruct;

@Controller
public class WebSocketStatusController {

    private final SimpMessagingTemplate messagingTemplate;
    private final StatusService statusService;

    private final StatusProperties statusProperties;

    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenMap = new ConcurrentHashMap<>();
    private final Map<String, List<Status>> replicationErrorQueue = new ConcurrentHashMap<>();

    @Value("${server.port}")
    private String currentPort;

    public WebSocketStatusController(
            SimpMessagingTemplate messagingTemplate,
            StatusService statusService,
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
            System.out.printf("[BLOCKED] Username '%s' already connected on this server from another session%n",
                    username);
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", "Username already in use.");
            return;
        }

        // Check all peer servers (excluding current) if the username is already active,
        // block if found
        for (String peer : statusProperties.getPeers()) {
            if (peer.contains(currentPort))
                continue;

            try {
                String url = peer + "/status/username-active?username=" + username;
                Boolean isActive = new RestTemplate().getForObject(url, Boolean.class);
                if (Boolean.TRUE.equals(isActive)) {
                    System.out.printf("[BLOCKED] Username '%s' already active on peer %s%n", username, peer);
                    messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors",
                            "Username already in use on another server.");
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
                    replicationErrorQueue.putIfAbsent(peer, new java.util.ArrayList<>());
                    System.err.println("Storing status in error queue for " + peer);
                    List<Status> peerQueue = replicationErrorQueue.get(peer);
                    if (!peerQueue.contains(status)) {
                        peerQueue.add(status);
                    }
                    replicationErrorQueue.put(peer, peerQueue);
                    // Print the list of statuses that failed to replicate
                    for (Status s : peerQueue) {
                        System.err.println("Replication error queue for " + peer + ": " + s.getUsername().toString());
                    }
                }
            }
        });

        messagingTemplate.convertAndSend("/topic/status", status);
    }

    // Checks if client with that username already existsAdd commentMore actions
    @GetMapping("/status/username-active")
    public ResponseEntity<Boolean> isUsernameActive(@RequestParam String username) {
        return ResponseEntity.ok(sessionUserMap.containsValue(username));
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

    @PostConstruct
    public void init() {
        System.out.println("[INIT] Inactivity cleaner scheduled");
    }

    @Scheduled(fixedRate = 5) // 5 seconds
    public void cleanupInactiveStatuses() {
        long now = System.currentTimeMillis();
        long timeoutInactive = 120 * 1000; // 2min
        long timeoutRemove = 300 * 1000; // 5min

        lastSeenMap.forEach((username, lastSeen) -> {
            // Check if the user is inactive
            if (now - lastSeen > timeoutInactive) {
                Status status = statusService.findByUsername(username);
                if (status != null && !status.getStatustext().equals("Inactive")) {
                    System.out.printf("[CLEANUP] Change status of inactive user: %s%n", username);
                    StatusRequest req = new StatusRequest(username, "Inactive");
                    Status newStatus = statusService.update(status.getId(), req);

                    broadcastStatus(newStatus);
                    statusProperties.getPeers().forEach(peer -> {
                        if (!peer.contains(currentPort)) {
                            try {
                                new RestTemplate().postForObject(peer + "/status/replicate", newStatus, Void.class);
                            } catch (Exception e) {
                                System.err.println("Failed to replicate inactive change: " + e.getMessage());
                            }
                        }
                    });
                }
            }
            // Check if the user needs to be removed
            if (now - lastSeen > timeoutRemove) {
                handleUserDisconnect(username);
                lastSeenMap.remove(username);
            }
        });
    }

    @Scheduled(fixedRate = 30000) // 30 seconds
    public void CheckForReplicationErrors() {
        replicationErrorQueue.forEach((peer, statuses) -> {
            boolean success = true;
            for (Status status : statuses) {
                try {
                    new RestTemplate().postForObject(peer + "/status/replicate", status, Void.class);
                    System.out.println("[RETRY] Successfully replicated to " + peer + ": " + status.getUsername());
                    // Clear the queue after successful replication
                } catch (Exception e) {
                    System.err.println("[RETRY FAILED] Could not replicate to " + peer + ": " + e.getMessage());
                    success = false;
                }
            }
            if (success) {
                replicationErrorQueue.remove(peer);
            }
        });
    }

    @MessageMapping("/disconnect")
    public void handleManualDisconnect(StatusRequest req, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String username = req.getUsername();

        System.out.printf("[MANUAL DISCONNECT] User %s manually disconnected%n", username);
        sessionUserMap.remove(sessionId);
        lastSeenMap.remove(username);
        handleUserDisconnect(username);
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

    // Returns the session ID for the given username if connected, otherwise null
    private String getSessionIdForUsername(String username) {
        return sessionUserMap.entrySet().stream()
                .filter(entry -> username.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
