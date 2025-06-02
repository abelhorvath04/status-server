package project.status_service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatusService {

    private final Map<Long, Status> store = new ConcurrentHashMap<>();

    public Status saveOrUpdate(StatusRequest req) {
        for (Map.Entry<Long, Status> entry : store.entrySet()) {
            if (entry.getValue().getUsername().equalsIgnoreCase(req.getUsername())) {
                Long id = entry.getKey();
                Status updated = new Status(id, req.getUsername(), req.getStatusText(), LocalDateTime.now());
                store.put(id, updated);
                return updated;
            }
        }

        Long id = (long) Math.abs(new Random().nextInt() % 10000);
        Status status = new Status(id, req.getUsername(), req.getStatusText(), LocalDateTime.now());
        store.put(id, status);
        return status;
    }

    public Status update(Long id, StatusRequest req) {
        Status status = new Status(id, req.getUsername(), req.getStatusText(), LocalDateTime.now());
        store.put(id, status);
        return status;
    }

    public void delete(Long id) {
        store.remove(id);
    }

    public Status get(Long id) {
        return store.get(id);
    }

    public void replicate(Status status) {
        store.put(status.getId(), status);
    }

    public void replicateDelete(Long id) {
        store.remove(id);
    }

    public List<Status> all() {
        return new ArrayList<>(store.values());
    }

    public Map<Long, Status> getStore() {
        return store;
    }

    public Status findByUsername(String username) {
        return store.values().stream()
                .filter(s -> s.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }
}