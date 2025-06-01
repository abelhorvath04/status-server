const ports = [9001, 9002];
const stompClients = [];

function createStompClient(port) {
    const client = new StompJs.Client({
        brokerURL: `ws://localhost:${port}/status-websocket`,
        reconnectDelay: 5000,
        onConnect: (frame) => {
            console.log(`Connected to port ${port}:`, frame);
            client.subscribe('/topic/status', (message) => {
                const status = JSON.parse(message.body);
                showStatus(status);
            });

            client.publish({
                destination: "/app/request-statuses",
                body: ""
            });

            client.subscribe('/topic/init-status', (message) => {
                const status = JSON.parse(message.body);
                showStatus(status);
            });

            client.subscribe('/topic/status-delete', (message) => {
                const id = Number(message.body);
                $(`#status-${id}`).remove();
            });
            setConnected(true);
        },
        onStompError: (frame) => {
            console.error(`Broker error on port ${port}: ${frame.headers['message']}`);
            console.error('Details:', frame.body);
        },
        onWebSocketError: (error) => {
            console.error(`WebSocket error on port ${port}:`, error);
        }
    });

    stompClients.push(client);
    return client;
}

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#send-up").prop("disabled", !connected);
    $("#disconnect").prop("disabled", !connected);
    $("#send").prop("disabled", !connected);
    $("#conversation").toggle(connected);
    if (!connected) {
        $("#status-list").html("");
    }
}

function connect() {
    ports.forEach(port => {
        const client = createStompClient(port);
        client.activate();
    });
}

function disconnect() {
    stompClients.forEach(client => client.deactivate());
    stompClients.length = 0;
    setConnected(false);
    console.log("disconnected from all ports");
}

function sendMsg() {
    const username = $("#name").val();
    const statustext = "UP";
    
    const request = {
        username: username,
        statusText: statustext
    };

    stompClients.forEach(client => {
        if (client.connected) {
            client.publish({
                destination: "/app/status",
                body: JSON.stringify(request)
            });
        }
    });

    if (statustext === "UP") {
        $("#send-up").prop("disabled", true);
        $("#send-down").prop("disabled", false);
    } else if (statustext === "DOWN") {
        $("#send-up").prop("disabled", false);
        $("#send-down").prop("disabled", true);
    }
}

function showStatus(status) {
    const { id, username, statustext, uhrzeit } = status;
    const formattedTime = new Date(uhrzeit).toLocaleString();
    const entryId = `status-${id}`;

    let existing = $(`#${entryId}`);

    if (existing.length === 0) {
        const item = $("<li>")
            .attr("id", entryId)
            .text(`${username} (ID: ${id}) is ${statustext} at ${formattedTime}`);

        if (statustext === 'DOWN') item.addClass("down");

        $("#status-list").append(item);
    } else {
        existing
            .text(`${username} (ID: ${id}) is ${statustext} at ${formattedTime}`)
            .removeClass("down");

        if (statustext === 'DOWN') existing.addClass("down");
    }
}

function sendStatus(statustext) {
    const username = $("#name").val();
    const request = { username, statusText: statustext };
    
    const primaryClient = stompClients[0];

    if (primaryClient?.connected) {
        primaryClient.publish({
            destination: "/app/status",
            body: JSON.stringify(request)
        });
    }

    if (statustext  === "UP") {
        $("#send-up").prop("disabled", true);
        $("#send-down").prop("disabled", false);
    } else if (statustext  === "DOWN") {
        $("#send-up").prop("disabled", false);
        $("#send-down").prop("disabled", true);
    }
}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    $("#connect").click(connect);
    $("#disconnect").click(disconnect);
    $("#send-up").click(() => sendStatus("UP"));
    $("#send-down").click(() => sendStatus("DOWN"));
});