package com.aslam.p2pdemo.tcpsocket;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.Log;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class SocketWebServer extends Thread {

    Context context;
    ServerSocket serverSocket;

    public SocketWebServer(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(8080);
            while (true) {
                final Socket socket = serverSocket.accept();
                new ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL, 50);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            BufferedReader is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            PrintWriter os = new PrintWriter(socket.getOutputStream(), true);
                            String request = is.readLine();
                            Scanner scanner = new Scanner(context.getAssets().open("server.html"));
                            String response = scanner.useDelimiter("\\Z").next();
                            os.print("HTTP/1.0 200" + "\r\n");
                            os.print("Content type: text/html" + "\r\n");
                            os.print("Content length: " + response.length() + "\r\n");
                            os.print("\r\n");
                            os.print(response + "\r\n");
                            os.flush();
                            socket.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void interrupt() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.interrupt();
    }
}
