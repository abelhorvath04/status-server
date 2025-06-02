const gatewayPort = 9000;
const gatewayWsUrl = `ws://${window.location.hostname}:${gatewayPort}/status-websocket`;

let stompClient = null;

function createStompClient() {
    stompClient = new StompJs.Client({
        brokerURL: gatewayWsUrl,
        reconnectDelay: 5000,
        onConnect: (frame) => {
            console.log(`Connected to API Gateway:`, frame);

            stompClient.subscribe('/topic/status', (message) => {
                const status = JSON.parse(message.body);
                showStatus(status);
            });

            stompClient.publish({
                destination: "/app/request-statuses",
                body: ""
            });

            stompClient.subscribe('/topic/init-status', (message) => {
                const status = JSON.parse(message.body);
                showStatus(status);
            });

            stompClient.subscribe('/topic/status-delete', (message) => {
                const id = Number(message.body);
                $(`#status-${id}`).remove();
            });
            setConnected(true);
        },
        onStompError: (frame) => {
            console.error(`Broker error: ${frame.headers['message']}`);
            console.error('Details:', frame.body);
        },
        onWebSocketError: (error) => {
            console.error(`WebSocket error:`, error);
        }
    });

    stompClient.activate();
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
    if (!stompClient || !stompClient.connected) {
        createStompClient();
    }
}

function disconnect() {
    if (stompClient && stompClient.active) {
        stompClient.deactivate();
        stompClient = null;
        setConnected(false);
        console.log("Disconnected");
    }
}

function sendMsg() {
    const username = $("#name").val();
    const statustext = "UP";
    
    const request = {
        username: username,
        statusText: statustext
    };

    if (stompClient?.connected) {
        stompClient.publish({
            destination: "/app/status",
            body: JSON.stringify(request)
        });

        console.log("[SEND] Sent status:", request);
    }

    if (statustext === "UP") {
        $("#send-up").prop("disabled", true);
        $("#send-down").prop("disabled", false);
    } else if (statustext === "DOWN") {
        $("#send-up").prop("disabled", false);
        $("#send-down").prop("disabled", true);
    }
}

function showStatus(status) {
    const { id, username, statustext, timestamp } = status;
    const formattedTime = new Date(timestamp).toLocaleString();
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

    if (stompClient?.connected) {
        stompClient.publish({
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