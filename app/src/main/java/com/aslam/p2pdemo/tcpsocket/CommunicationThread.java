package com.aslam.p2pdemo.tcpsocket;

import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    String key;

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
                int c;
                String fromClient = "";
                do {
                    c = inputStream.read();
                    fromClient += (char) c;
                } while (inputStream.available() > 0);
                if (c == -1) {
                    throw new EOFException();
                }

                try {
                    String data = fromClient;
                    Matcher get = Pattern.compile("^GET").matcher(data);
                    if (get.find()) {
                        Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                        match.find();
                        key = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                                .digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")));
                        byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                + "Connection: Upgrade\r\n"
                                + "Upgrade: websocket\r\n"
                                + "Sec-WebSocket-Accept: "
                                + key
                                + "\r\n\r\n").getBytes("UTF-8");
                        outputStream.write(response, 0, response.length);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                socketListener.onDataReceived(fromClient);
            }
        } catch (EOFException e) {
            socketListener.onDisconnected(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // private String decodeMessage(String message){
    //     try {
    //         byte[] decoded = new byte[1024];
    //         byte[] encoded = new byte[] { (byte) 198, (byte) 131, (byte) 130, (byte) 182, (byte) 194, (byte) 135 };
    //         byte[] key = new byte[] { (byte) 167, (byte) 225, (byte) 225, (byte) 210 };
    //         for (int i = 0; i < encoded.length; i++) {
    //             decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
    //         }
    //         return new String(decoded, "UTF-8");
    //     }catch (IOException ex){
    //         ex.printStackTrace();
    //     }
    //     return "ping";
    // }

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

                // String ss = null;
                // try {
                //     ss = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                //             .digest(("ASLAM" + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")));
                // } catch (NoSuchAlgorithmException e) {
                //     e.printStackTrace();
                // } catch (UnsupportedEncodingException e) {
                //     e.printStackTrace();
                // }

                printWriter.write("ASLAM");
                printWriter.flush();
                // try {
                //     byte[] res = data.getBytes("UTF-8");
                //     outputStream.write(res, 0, res.length);
                // } catch (IOException ex) {
                //     ex.printStackTrace();
                // }
            }
        }).start();
    }
}
