(function () {

    var ws = new WebSocket("ws://192.168.8.103:45454");

    ws.onopen = () => {
        console.log("onopen")
    }

    ws.onmessage = (e) => {
        console.log('Message from server ', e.data);
        ws.send('from Browser: ' + e.data)
    }

    ws.onerror = err => console.log(err)

})()