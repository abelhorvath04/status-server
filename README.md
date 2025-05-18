# Status Server - Spring Boot, WebSockets & API Gateway

A distributed system for real-time monitoring of service statuses using Spring Boot microservices, Eureka for service discovery, WebSocket (STOMP) messaging, and a frontend client.

## Architecture

- **Client Nodes** (`client`) – Send status updates via WebSocket
- **Eureka Sserver** - (`eureka-server`) - Locating services for the purpose of load balancing.
- **Status Service** (`status-service`) – Handles WebSocket messaging and broadcasting via STOMP
- **API Gateway** (`api-gateway`) – Routes traffic to services using Spring Cloud Gateway
- **Frontend** – JavaScript client connecting via WebSocket to gateway

## Tech Stack

- Spring Boot
- Spring WebSocket (STOMP)
- Spring Cloud Gateway
- Eureka Discovery Service
- Docker & Docker Compose
- JavaScript + STOMP.js (Frontend)
- Basic HTML & CSS

## Key Endpoints

| Route            | Service          | Purpose                  |
|------------------|------------------|--------------------------|
| `/status/**`     | `client`         | Handles status updates   |
| `/status-websocket/**` | `status-service` | WebSocket STOMP endpoint |

## Gateway Configuration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: status-service
          uri: lb://STATUS-SERVICE
          predicates:
            - Path=/status-websocket/**
```
## WebSocket Setup

### Backend (`status-service`)

```java
registry.addEndpoint("/status-websocket")
        .setAllowedOriginPatterns("*")
        .setAllowedOrigins("*");
```

## Frontend

```javascript
const stompClient = new StompJs.Client({
    brokerURL: 'ws://api-gateway:9000/status-websocket',
    reconnectDelay: 5000
});
```

## Building & Running

```bash
# Build services
mvn clean package

# Run with Docker
docker compose up --build
```
