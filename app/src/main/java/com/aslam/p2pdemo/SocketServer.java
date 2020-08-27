package com.aslam.p2pdemo;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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

    public static final int PORT = 85856;

    private ServerSocket serverSocket;
    private Socket socket;
    private SocketServerListener socketClientListener;
    private Thread socketThread;
    private SocketServerRunnable socketServerRunnable;
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

                    // int c;
                    // String raw = "";
                    // do {
                    //     c = socket.getInputStream().read();
                    //     raw += (char) c;
                    // } while (socket.getInputStream().available() > 0);
                    // System.out.println(raw);
                    //
                    // PrintWriter printWriter = new PrintWriter(socket.getOutputStream());
                    // printWriter.write("aslam");
                    // printWriter.flush();

                    socketClientListener.onConnected(String.format("Client connected from: %s", socket.getRemoteSocketAddress().toString()));

                    // DataInputStream in = new DataInputStream(socket.getInputStream());
                    // socketClientListener.onDataReceived(in.readUTF());

                    // DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    // out.writeUTF("Thank you for connecting to " + socket.getLocalSocketAddress());

                    for (Thread communicationThread : communicationThreadList) {
                        if (communicationThread != null && communicationThread.isAlive()) {
                            communicationThread.interrupt();
                        }
                    }

                    CommunicationThreadRunnable communicationThreadRunnable = new CommunicationThreadRunnable(socket);
                    Thread communicationThread = new Thread(communicationThreadRunnable);
                    communicationThread.start();

                    communicationRunnableList.add(communicationThreadRunnable);
                    communicationThreadList.add(communicationThread);
                }

            } catch (IOException e) {
                if (e != null && e.getMessage() != null && e.getMessage().contains("Socket closed")) {
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
        private InputStream inputStream;
        private OutputStream outputStream;
        private PrintWriter printWriter;

        public CommunicationThreadRunnable(Socket socket) {
            try {
                clientSocket = socket;
                inputStream = clientSocket.getInputStream();
                outputStream = clientSocket.getOutputStream();
                printWriter = new PrintWriter(outputStream);
                sendData("Thank you for connecting to " + socket.getLocalSocketAddress());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // in = new DataInputStream(clientSocket.getInputStream());
                    // socketClientListener.onDataReceived(in.readUTF());
                    int c;
                    String fromClient = "";
                    do {
                        c = inputStream.read();
                        fromClient += (char) c;
                    } while (inputStream.available() > 0);
                    if (c == -1) {
                        throw new EOFException();
                    }
                    socketClientListener.onDataReceived(fromClient);
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
                    // out.writeUTF(data);
                    printWriter.write(data);
                    printWriter.flush();
                }
            }).start();
        }
    }
}