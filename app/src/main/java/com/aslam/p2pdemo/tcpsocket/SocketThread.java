package com.aslam.p2pdemo.tcpsocket;

import android.media.AudioManager;
import android.media.ToneGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SocketThread extends Thread {

    public enum SocketType {SERVER, CLIENT}

    String host = "127.0.0.1";
    int port = 45454;
    SocketType socketType;
    ServerSocket serverSocket;
    CommunicationThread.SocketListener socketListener;
    List<CommunicationThread> communicationThreadPool;

    public SocketThread(SocketType socketType, int port, String host, CommunicationThread.SocketListener socketListener) {
        this.socketType = socketType;
        this.port = port > 0 ? port : this.port;
        this.host = host != null ? host : this.host;
        this.socketListener = socketListener;
        this.communicationThreadPool = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            if (socketType == SocketType.SERVER) {
                serverSocket = new ServerSocket(port);
                while (true) {
                    Socket socket = serverSocket.accept();
                    socketListener.onConnected("Client connected from: " + socket.getRemoteSocketAddress());
                    CommunicationThread communicationThread = new CommunicationThread(socket, socketListener);
                    communicationThread.start();
                    communicationThreadPool.add(communicationThread);
                }
            } else if (socketType == SocketType.CLIENT) {
                Socket socket = new Socket(host, port);
                socketListener.onConnected("Server connected from: " + socket.getRemoteSocketAddress());
                CommunicationThread communicationThread = new CommunicationThread(socket, socketListener);
                communicationThread.start();
                communicationThreadPool.add(communicationThread);
            }
        } catch (IOException e) {
            socketListener.onFailed(e);
        }
    }

    private void interruptCommunications() {
        for (CommunicationThread communicationThread : communicationThreadPool) {
            communicationThread.interrupt();
        }
    }

    @Override
    public void interrupt() {
        try {
            interruptCommunications();
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        super.interrupt();
    }

    public void sendData(String data) {
        for (CommunicationThread communicationThread : communicationThreadPool) {
            communicationThread.sendData(data);
        }
    }
}
