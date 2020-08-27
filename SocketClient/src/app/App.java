package app;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import app.CommunicationThread.SocketListener;

public class App {

    static SocketListener socketListener;
    static SocketThread socketThread;

    public static void main(String args[]) {

        JFrame frame = new JFrame("Socket Android");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 300);
        frame.setResizable(false);

        JPanel panel = new JPanel();

        JButton btnConnect = new JButton("Connect");
        JButton btnSend = new JButton("Send");
        btnSend.setEnabled(false);

        JTextArea textArea = new JTextArea(15, 40);
        textArea.setText("Not connected.");
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);

        panel.add(btnConnect);
        panel.add(btnSend);
        panel.add(scrollPane);

        frame.getContentPane().add(BorderLayout.CENTER, panel);
        frame.setVisible(true);

        socketListener = new CommunicationThread.SocketListener() {

            @Override
            public void onConnected(String message) {
                System.out.println("onConnected: " + message);
                textArea.setText("onConnected: " + message + "\n" + textArea.getText());
                btnConnect.setEnabled(false);
                btnSend.setEnabled(true);
            }

            @Override
            public void onFailed(Exception exception) {
                System.out.println("onFailed: " + exception.getMessage());
                textArea.setText(exception.getMessage() + "\n" + textArea.getText());
            }

            @Override
            public void onDataReceived(String data) {
                System.out.println("onDataReceived: " + data);
                textArea.setText(data + "\n" + textArea.getText());
            }

            @Override
            public void onDisconnected(EOFException exception) {
                System.out.println("onDisconnected");
                textArea.setText("Disconnected.\n" + textArea.getText());
                btnConnect.setEnabled(true);
                btnSend.setEnabled(false);
            }
        };

        btnConnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent args) {
                if (socketThread != null) {
                    socketThread.interrupt();
                }
                socketThread = new SocketThread(SocketThread.SocketType.CLIENT, 45454, "192.168.10.224", socketListener);
                socketThread.start();
            }
        });

        btnSend.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent args) {
                socketThread.sendData("From Java");
            }
        });
    }
}