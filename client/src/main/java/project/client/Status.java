package project.client;

import java.time.LocalDateTime;

public class Status {
    private String username;
    private String statustext;
    private LocalDateTime uhrzeit;

    public Status() {}

    public Status(String username, String statustext, LocalDateTime uhrzeit) {
        this.username = username;
        this.statustext = statustext;
        this.uhrzeit = uhrzeit;
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

    public LocalDateTime getUhrzeit() {
        return uhrzeit;
    }

    public void setUhrzeit(LocalDateTime uhrzeit) {
        this.uhrzeit = uhrzeit;
    }
}
