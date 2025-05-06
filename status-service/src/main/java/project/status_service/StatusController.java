package project.status_service;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class StatusController {
    @MessageMapping("/status")
    @SendTo("/topic/status")
    public StatusMessage statusMessage(StatusMessage msg) throws Exception {

        System.out.println("Node: " + msg.getNodeId() + " +++ Status: " + msg.getStatus());

    return msg;
    }
}
