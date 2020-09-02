package com.aslam.p2pdemo.tcpsocket;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class CommunicationThread extends Thread {

    public interface SocketListener {

        void onConnected(String message);

        void onDisconnected(EOFException exception);

        void onFailed(Exception exception);

        void onDataReceived(String data);
    }

    private Socket clientSocket;
    private SocketListener socketListener;
    private InputStream inputStream;
    private OutputStream outputStream;
    private PrintWriter printWriter;

    public CommunicationThread(Socket socket, SocketListener socketListener) {
        try {
            this.clientSocket = socket;
            this.socketListener = socketListener;
            this.inputStream = clientSocket.getInputStream();
            this.outputStream = clientSocket.getOutputStream();
            this.printWriter = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                String data = waitForData();
                socketListener.onDataReceived(data);
            }
        } catch (EOFException e) {
            socketListener.onDisconnected(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String waitForData() throws IOException {
        String data = "";
        do {
            int c = inputStream.read();
            if (c > -1) data += (char) c;
            else throw new EOFException();
        } while (inputStream.available() > 0);
        return data;
    }

    @Override
    public void interrupt() {
        try {
            inputStream.close();
            outputStream.close();
            printWriter.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.interrupt();
    }

    public void sendData(final String data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                printWriter.write(data);
                printWriter.flush();
            }
        }).start();
    }
}
