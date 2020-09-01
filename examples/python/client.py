import websocket

ws = websocket.WebSocket()
ws.connect('ws://192.168.10.237:45454')
print('connected')
while(True):
    result = ws.recv()
    print(result)
    ws.send('from Python: ' + result)
