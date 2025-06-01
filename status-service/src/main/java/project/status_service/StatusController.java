package project.status_service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/status")
public class StatusController {

    private WebSocketStatusController ws;
    private StatusProperties statusProperties;
    private StatusService statusService;

    @Value("${server.port}")
    private String currentPort;

    public StatusController(WebSocketStatusController ws, StatusProperties statusProperties, StatusService statusService) {
        this.ws = ws;
        this.statusProperties = statusProperties;
        this.statusService = statusService;
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody StatusRequest statusRequest) {
        Status status = statusService.saveOrUpdate(statusRequest);
        replicateToPeers(status);
        ws.broadcastStatus(status);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody StatusRequest statusRequest) {
        if (!statusService.getStore().containsKey(id)) {
            return ResponseEntity.notFound().build();
        }

        Status status = statusService.update(id, statusRequest);
        replicateToPeers(status);
        ws.broadcastStatus(status);
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
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/replicate/{id}")
    public ResponseEntity<Void> replicateDelete(@PathVariable Long id) {
        statusService.replicateDelete(id);
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
            }
        }
    }

    private void sendDeleteToPeers(Long id) {
        for (String peer : statusProperties.getPeers()) {
            try {
                if (peer.contains(currentPort)) continue;
                System.out.println("Replicating DELETE to " + peer);
                new RestTemplate().delete(peer + "/status/replicate/" + id);
            } catch (Exception e) {
                System.err.println("Failed to replicate DELETE to " + peer + ": " + e.getMessage());
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
            } catch (Exception ignored) {}
        }
    }
}