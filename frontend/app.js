const stompClient = new StompJs.Client({
    brokerURL: 'ws://api-gateway:9000/status-websocket',
    reconnectDelay: 5000
});

stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);
    stompClient.subscribe('/topic/status', (message) => {
        const status = JSON.parse(message.body);
        showStatus(status.nodeId, status.status);
    });
};

stompClient.onWebSocketError = (error) => {
    console.error('Error with websocket: ', error);
};

stompClient.onStompError = (frame) => {
    console.error('Broker error: ' + frame.headers['message']);
    console.error('Additional deatils: ' + frame.body);
};

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#send-up").prop("disabled", !connected);
    $("#disconnect").prop("disabled", !connected);
    $("#send").prop("disabled", !connected);
    $("#conversation").toggle(connected);
    $("#status-list").html("");
}

function connect() {
    stompClient.activate();
}

function disconnect() {
    stompClient.deactivate();
    setConnected(false);
    console.log("disconnected");
}

function sendMsg() {
    const nodeId = $("#name").val();
    const status = $("input[name='status']:checked").val();
    stompClient.publish({
        destination: "/app/status",
        body: JSON.stringify({ nodeId, status })
    });
}

function showStatus(nodeId, status) {
    let existing = $(`#status-${nodeId}`);
    
    if (existing.length === 0) {
        const item = $("<li>")
            .attr("id", `status-${nodeId}`)
            .text(`${nodeId} is ${status}`);
        
        if (status === 'DOWN') item.addClass("down");
        $("#status-list").append(item);
    } else {
        existing
            .text(`${nodeId} is ${status}`)
            .removeClass("down");
        if (status === 'DOWN') existing.addClass("down");
    }
}


function sendStatus(status) {
    const nodeId = $("#name").val();
    stompClient.publish({
        destination: "/app/status",
        body: JSON.stringify({ nodeId, status })
    });

    if (status === "UP") {
        $("#send-up").prop("disabled", true);
        $("#send-down").prop("disabled", false);
    } else if (status === "DOWN") {
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