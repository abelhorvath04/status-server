package project.status_service;

import java.time.LocalDateTime;

public class Status {
    private Long id;
    private String username;
    private String statustext;
    private LocalDateTime timestamp;

    public Status() {}

    public Status(Long id, String username, String statustext, LocalDateTime timestamp) {
        this.id = id;
        this.username = username;
        this.statustext = statustext;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatustext() {
        return statustext;
    }

    public void setStatustext(String statustext) {
        this.statustext = statustext;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
