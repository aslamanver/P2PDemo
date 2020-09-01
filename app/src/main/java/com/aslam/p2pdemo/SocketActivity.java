package com.aslam.p2pdemo;

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.FileUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.aslam.p2pdemo.databinding.ActivitySocketBinding;
import com.aslam.p2pdemo.tcpsocket.CommunicationThread;
import com.aslam.p2pdemo.tcpsocket.SocketThread;
import com.aslam.p2pdemo.tcpsocket.SocketWebServer;
import com.aslam.p2pdemo.websocket.MyWebSocketClient;
import com.aslam.p2pdemo.websocket.MyWebSocketServer;
import com.aslam.p2pdemo.websocket.MyWebSocketListener;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

public class SocketActivity extends AppCompatActivity {

    int port = 45454;
    ActivitySocketBinding binding;
    SocketThread socketThread;
    SocketWebServer socketWebServer;

    String deviceIP, host;

    MyWebSocketServer webSocketServer;
    MyWebSocketClient webSocketClient;

    int socketType = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_socket);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        deviceIP = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        host = getIntent().hasExtra("HOST") ? getIntent().getStringExtra("HOST") : "192.168.10.237";
        binding.edtAddress.setText(host);
        consoleLog("Device IP: " + deviceIP);


        binding.btnServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (socketType == 1) {
                    if (socketThread == null) {
                        startSocketServer();
                    } else {
                        stopSocket();
                        consoleLog("Socket Server stopped");
                    }
                } else {
                    if (webSocketServer == null) {
                        startWebSocketServer();
                    } else {
                        stopWebSocketServer();
                    }
                }
            }
        });

        binding.btnClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (socketType == 1) {
                    if (socketThread == null) {
                        startSocketClient(binding.edtAddress.getText().toString());
                    } else {
                        stopSocket();
                        consoleLog("Client stopped.");
                    }
                } else {
                    if (webSocketClient == null) {
                        startWebSocketClient(binding.edtAddress.getText().toString());
                    } else {
                        stopWebSocketClient();
                    }
                }
            }
        });

        binding.btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String data = "Some data: " + new Random().nextInt(1000);
                consoleLog(data);
                if (socketType == 1) {
                    socketThread.sendData(data);
                } else {
                    if (webSocketServer != null) {
                        webSocketServer.send(data);
                    } else if (webSocketClient != null) {
                        webSocketClient.send(data);
                    }
                }
            }
        });

        binding.btnWebServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (socketWebServer == null) {
                    startSocketWebServer();
                } else {
                    stopSocketWebServer();
                }
            }
        });
    }

    private void startSocketWebServer() {
        consoleLog("Socket web server started");
        socketWebServer = new SocketWebServer(getApplicationContext());
        socketWebServer.start();
    }

    private void startWebSocketServer() {

        stopWebSocketServer();

        webSocketServer = new MyWebSocketServer(port, new MyWebSocketListener.Server() {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {
                consoleLog("WebSocket: new connection to " + conn.getRemoteSocketAddress());
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                consoleLog("WebSocket: closed " + (conn != null ? conn.getRemoteSocketAddress() : "NULL") + " with exit code " + code + " additional info: " + reason);
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                consoleLog("WebSocket: received message from " + conn.getRemoteSocketAddress() + ": " + message);
            }

            @Override
            public void onMessage(WebSocket conn, ByteBuffer message) {
                consoleLog("WebSocket: received ByteBuffer from " + conn.getRemoteSocketAddress());
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                consoleLog("WebSocket: an error occurred on connection " + (conn != null ? conn.getRemoteSocketAddress() : "NULL") + ":" + ex);
            }

            @Override
            public void onStart() {
                consoleLog("WebSocket: server started successfully");
            }
        });

        webSocketServer.start();
    }

    private void stopWebSocketServer() {
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
                consoleLog("WebSocket Server stopped");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            webSocketServer = null;
        }
    }

    private void startWebSocketClient(String host) {

        stopWebSocketClient();

        try {

            URI uri = new URI("ws://" + host + ":" + port);

            webSocketClient = new MyWebSocketClient(uri, new MyWebSocketListener.Client() {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    consoleLog("WebSocket: new connection opened");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    consoleLog("WebSocket: closed with exit code " + code + " additional info: " + reason);
                }

                @Override
                public void onMessage(String message) {
                    consoleLog("WebSocket: received message: " + message);
                }

                @Override
                public void onMessage(ByteBuffer message) {
                    consoleLog("WebSocket: received ByteBuffer");
                }

                @Override
                public void onError(Exception ex) {
                    consoleLog("WebSocket: an error occurred:" + ex);
                }
            });

            webSocketClient.connect();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void stopWebSocketClient() {
        if (webSocketClient != null) {
            webSocketClient.close();
            consoleLog("WebSocket Client stopped");
        }
    }

    private void startSocketServer() {

        stopSocket();

        socketThread = new SocketThread(SocketThread.SocketType.SERVER, port, null, new CommunicationThread.SocketListener() {
            @Override
            public void onConnected(String message) {
                consoleLog("SocketServer: onConnected " + message);
            }

            @Override
            public void onDisconnected(EOFException exception) {
                consoleLog("SocketServer: onDisconnected");
            }

            @Override
            public void onFailed(Exception exception) {
                consoleLog("SocketServer: onFailed " + exception.getMessage());
            }

            @Override
            public void onDataReceived(String data) {
                consoleLog("SocketServer: onDataReceived " + data);
            }
        });

        socketThread.start();
        consoleLog("Server started.");
        binding.txtStatus.setText("SERVER: " + deviceIP);
    }

    private void startSocketClient(String host) {

        stopSocket();

        socketThread = new SocketThread(SocketThread.SocketType.CLIENT, port, host, new CommunicationThread.SocketListener() {
            @Override
            public void onConnected(String message) {
                consoleLog("SocketClient: onConnected " + message);
            }

            @Override
            public void onDisconnected(EOFException exception) {
                consoleLog("SocketClient: onDisconnected " + exception.getMessage());
            }

            @Override
            public void onFailed(Exception exception) {
                consoleLog("SocketClient: onFailed " + exception.getMessage());
            }

            @Override
            public void onDataReceived(String data) {
                consoleLog("SocketClient: onDataReceived " + data);
            }
        });

        socketThread.start();
        consoleLog("Client staring to connect " + host);
        binding.txtStatus.setText("CLIENT OF: " + host);
    }

    private void consoleLog(final String message) {
        Log.e("P2PDemo", message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.txtConsole.setText(binding.txtConsole.getText() + "\n" + message);
                binding.scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void stopSocket() {
        if (socketThread != null) {
            socketThread.interrupt();
            socketThread = null;
        }
    }

    private void stopSocketWebServer() {
        consoleLog("Socket web server stopped");
        if (socketWebServer != null) {
            socketWebServer.interrupt();
            socketWebServer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSocket();
        stopWebSocketServer();
        stopWebSocketClient();
        stopSocketWebServer();
    }
}