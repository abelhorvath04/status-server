package project.status_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;

@Controller
public class WebSocketStatusController {

    private final SimpMessagingTemplate messagingTemplate;
    private final StatusService statusService;

    private final StatusProperties statusProperties;

    @Value("${server.port}")
    private String currentPort;

    public WebSocketStatusController(SimpMessagingTemplate messagingTemplate, StatusService statusService, StatusProperties statusProperties) {
        this.messagingTemplate = messagingTemplate;
        this.statusService = statusService;
        this.statusProperties = statusProperties;
    }

    @MessageMapping("/status")
    public void handleStatus(StatusRequest req) {
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
        statusService.all().forEach(status ->
                messagingTemplate.convertAndSend("/topic/init-status", status)
        );
    }

    public void broadcastDelete(Long id) {
        messagingTemplate.convertAndSend("/topic/status-delete", id);
    }

    public void broadcastStatus(Status status) {
        messagingTemplate.convertAndSend("/topic/status", status);
    }
}