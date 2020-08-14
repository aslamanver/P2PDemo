package com.aslam.p2pdemo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SocketServer {

    public interface SocketServerListener {

        void onConnected(String message);

        void onDisconnected(EOFException exception);

        void onFailed(Exception exception);

        void onDataReceived(String data);
    }

    public static final int PORT = 46568;

    private Thread socketThread;
    // private Thread communicationThread;
    private SocketServerRunnable socketServerRunnable;
    // private CommunicationThreadRunnable communicationThreadRunnable;
    private ServerSocket serverSocket;
    private Socket socket;
    private SocketServerListener socketClientListener;
    private List<CommunicationThreadRunnable> communicationRunnableList = new ArrayList<>();
    private List<Thread> communicationThreadList = new ArrayList<>();

    public SocketServer(SocketServerListener socketClientListener) {
        this.socketClientListener = socketClientListener;
        socketServerRunnable = new SocketServerRunnable();
    }

    public void start() {

        if (socketThread != null && socketThread.isAlive()) {
            socketThread.interrupt();
        }

        for (Thread communicationThread : communicationThreadList) {
            if (communicationThread != null && communicationThread.isAlive()) {
                communicationThread.interrupt();
            }
        }

        socketThread = new Thread(socketServerRunnable);
        socketThread.start();
    }

    public void stop() {

        for (CommunicationThreadRunnable communicationThreadRunnable : communicationRunnableList) {
            if (communicationThreadRunnable != null) {
                communicationThreadRunnable.stop();
            }
        }

        socketServerRunnable.stop();
        socketThread.interrupt();

        for (Thread communicationThread : communicationThreadList) {
            if (communicationThread != null) {
                communicationThread.interrupt();
            }
        }
    }

    public void sendData(String data) {
        socketServerRunnable.sendData(data);
    }

    class SocketServerRunnable implements Runnable {

        @Override
        public void run() {

            try {

                serverSocket = new ServerSocket(PORT);

                while (true) {

                    socket = serverSocket.accept();

                    socketClientListener.onConnected(String.format("Client connected from: %s", socket.getRemoteSocketAddress().toString()));

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    socketClientListener.onDataReceived(in.readUTF());

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF("Thank you for connecting to " + socket.getLocalSocketAddress());

                    for (Thread communicationThread : communicationThreadList) {
                        if (communicationThread != null && communicationThread.isAlive()) {
                            communicationThread.interrupt();
                        }
                    }

                    CommunicationThreadRunnable communicationThreadRunnable = new CommunicationThreadRunnable(socket, in, out);
                    Thread communicationThread = new Thread(communicationThreadRunnable);
                    communicationThread.start();

                    communicationRunnableList.add(communicationThreadRunnable);
                    communicationThreadList.add(communicationThread);
                }

            } catch (IOException e) {
                if (e != null && e.getMessage().contains("Socket closed")) {
                    return;
                }
                socketClientListener.onFailed(e);
            } finally {
                socketThread.interrupt();
            }

        }

        public void sendData(final String data) {

            for (CommunicationThreadRunnable communicationThreadRunnable : communicationRunnableList) {
                communicationThreadRunnable.sendData(data);
            }
        }

        public void stop() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class CommunicationThreadRunnable implements Runnable {

        private Socket clientSocket;
        private DataInputStream in;
        private DataOutputStream out;

        public CommunicationThreadRunnable(Socket clientSocket, DataInputStream in, DataOutputStream out) {
            this.clientSocket = clientSocket;
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    in = new DataInputStream(this.clientSocket.getInputStream());
                    socketClientListener.onDataReceived(in.readUTF());
                }
            } catch (EOFException e) {
                socketClientListener.onDisconnected(e);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                for (Thread communicationThread : communicationThreadList) {
                    communicationThread.interrupt();
                }
            }
        }

        public void stop() {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendData(final String data) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        out.writeUTF(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        }
    }
}