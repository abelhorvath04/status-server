package project.status_service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/status")
public class StatusController {

    private WebSocketStatusController ws;
    private StatusProperties statusProperties;
    private StatusService statusService;
    private final Map<String, List<Status>> replicationErrorQueue = new ConcurrentHashMap<>();

    @Value("${server.port}")
    private String currentPort;

    public StatusController(WebSocketStatusController ws, StatusProperties statusProperties,
            StatusService statusService) {
        this.ws = ws;
        this.statusProperties = statusProperties;
        this.statusService = statusService;
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody StatusRequest statusRequest) {
        Status status = statusService.saveOrUpdate(statusRequest);
        System.out.println("[CREATE] Received via REST: " + status.getUsername() + ", " + status.getStatustext() + ", "
                + status.getTimestamp());
        replicateToPeers(status);
        ws.broadcastStatus(status);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody StatusRequest statusRequest) {
        Status oldStatus = statusService.get(id);
        if (oldStatus == null) {
            return ResponseEntity.notFound().build();
        }

        Status newStatus = statusService.update(id, statusRequest);
        System.out.printf("[UPDATE] ID: %d, %s: '%s' -> '%s'%n",
                id,
                oldStatus.getUsername(),
                oldStatus.getStatustext(),
                newStatus.getStatustext());

        replicateToPeers(newStatus);
        ws.broadcastStatus(newStatus);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Status> get(@PathVariable Long id) {
        Status s = statusService.get(id);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        statusService.delete(id);
        System.out.println("[DELETE] ID: " + id + " deleted");
        sendDeleteToPeers(id);
        ws.broadcastDelete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/all")
    public List<Status> all() {
        return statusService.all();
    }

    @PostMapping("/replicate")
    public ResponseEntity<Void> replicate(@RequestBody Status status) {
        statusService.replicate(status);
        System.out.println("[REPLICATE] Received from peer: " + status.getUsername() + ", " + status.getStatustext()
                + ", " + status.getTimestamp());
        ws.broadcastStatus(status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/replicate/{id}")
    public ResponseEntity<Void> replicateDelete(@PathVariable Long id) {
        statusService.replicateDelete(id);
        System.out.println("[REPLICATE DELETE] ID: " + id + " removed via peer");
        ws.broadcastDelete(id);
        return ResponseEntity.ok().build();
    }

    private void replicateToPeers(Status status) {
        for (String peer : statusProperties.getPeers()) {
            try {
                System.out.println("Replicating to " + peer);
                if (peer.contains(currentPort)) {
                    continue;
                }

                new RestTemplate().postForObject(peer + "/status/replicate", status, Void.class);
            } catch (Exception e) {
                System.err.println("Failed to replicate to " + peer + ": " + e.getMessage());
                // Store the status in the error queue for later retry
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
    }

    private void sendDeleteToPeers(Long id) {
        Status status = statusService.get(id);
        for (String peer : statusProperties.getPeers()) {
            try {
                if (peer.contains(currentPort))
                    continue;
                System.out.println("Replicating DELETE to " + peer);
                new RestTemplate().delete(peer + "/status/replicate/" + id);
            } catch (Exception e) {
                System.err.println("Failed to replicate DELETE to " + peer + ": " + e.getMessage());
                // Store the status in the error queue for later retry if it doesn't exist
                // already
                replicationErrorQueue.putIfAbsent(peer, new java.util.ArrayList<>());
                List<Status> peerQueue = replicationErrorQueue.get(peer);
                if (!peerQueue.contains(status)) {
                    peerQueue.add(status);
                }
                replicationErrorQueue.put(peer, peerQueue);
                // Print the list of statuses that failed to replicate
                System.err.println(
                        "Replication error queue for " + peer + ": " + replicationErrorQueue.get(peer).toString());
            }
        }
    }

    @PostConstruct
    public void syncFromPeers() {
        for (String peer : statusProperties.getPeers()) {
            try {
                if (peer.contains(currentPort)) {
                    continue;
                }

                Status[] statuses = new RestTemplate()
                        .getForObject(peer + "/status/all", Status[].class);
                if (statuses != null) {
                    for (Status s : statuses) {
                        statusService.replicate(s);
                    }
                    break;
                }
            } catch (Exception ignored) {
            }
        }
    }

    @PostConstruct
    public void init() {
        System.out.println("[INIT] Replication refresher scheduled");
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
}
