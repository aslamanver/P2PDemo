package com.aslam.p2pdemo.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.aslam.p2pdemo.MainActivity;
import com.aslam.p2pdemo.R;
import com.aslam.p2pdemo.models.DataCenter;
import com.aslam.p2pdemo.utils.Const;
import com.aslam.p2pdemo.utils.StorageHelper;
import com.aslam.p2pdemo.websocket.MyWebSocketClient;
import com.aslam.p2pdemo.websocket.MyWebSocketListener;
import com.aslam.p2pdemo.websocket.MyWebSocketServer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressLint("MissingPermission")
public class P2PService extends BaseForegroundService {

    public enum ConnectionType {SERVER, CLIENT}

    public abstract static class ServiceListener {

        protected abstract void onConsoleLog(String message);

        protected void onPeersAvailable(List<WifiP2pDevice> peers) {
        }

        protected void onWebSocketOpen() {
        }

        protected void onWebSocketClose() {
        }
    }

    private int logLine = 1;
    private StringBuilder logs = new StringBuilder();
    private ServiceListener serviceListener;
    private ToneGenerator toneGenerator;

    private Handler discoverHandler = new Handler();
    private Runnable discoverRunnable = getDiscoverRunnable();

    private static TextToSpeech textToSpeech;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private PowerManager powerManager;
    private WifiManager wifiManager;

    private WifiP2pManager p2pManager;
    private WifiP2pManager.Channel p2pChannel;
    public List<WifiP2pDevice> peers = new ArrayList<>();

    private WifiP2pDevice currentP2PDevice;
    private DataCenter dataCenter;
    private boolean isP2PEnabled;

    public MyWebSocketServer webSocketServer;
    public MyWebSocketClient webSocketClient;

    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            consoleLog("onReceive: " + action);

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

                // if (isWebSocketConnected()) return;

                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {

                    consoleLog("Wifi P2P is enabled");
                    isP2PEnabled = true;
                    initiateConnection();

                } else {

                    consoleLog("Wi-Fi P2P is not enabled");
                    isP2PEnabled = false;
                    // if (!wifiManager.isWifiEnabled()) wifiManager.setWifiEnabled(true);
                }

            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {

                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);

                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    consoleLog("Wifi P2P discovery started");
                } else {
                    consoleLog("Wifi P2P discovery stopped");
                    p2pManager.requestConnectionInfo(p2pChannel, new WifiP2pManager.ConnectionInfoListener() {
                        @Override
                        public void onConnectionInfoAvailable(WifiP2pInfo info) {
                            if (!info.groupFormed) {
                                discoverPeers(1);
                            }
                        }
                    });
                }

            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

                WifiP2pDeviceList deviceList = intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                consoleLog("peerDevices: " + deviceList.getDeviceList().size());

                peers.clear();
                peers.addAll(deviceList.getDeviceList());
                if (serviceListener != null) serviceListener.onPeersAvailable(peers);

                p2pManager.requestConnectionInfo(p2pChannel, info -> {

                    consoleLog("requestConnectionInfo: requestPeers " + " groupFormed: " + info.groupFormed);

                    for (WifiP2pDevice device : peers) {

                        consoleLog("peerDevice ---> " + device.deviceName + " " + getStatusText(device.status));

                        if (!info.groupFormed
                                && device.status != WifiP2pDevice.CONNECTED
                                && getConnectionType() == ConnectionType.CLIENT
                                && dataCenter.connectedDeviceAddress != null
                                && device.deviceAddress.equals(dataCenter.connectedDeviceAddress)) {

                            connectDevice(device);

                        } else if (getConnectionType() == ConnectionType.CLIENT && device.status == WifiP2pDevice.CONNECTED && info.groupOwnerAddress != null) {

                            consoleLog("peerDevice ---> " + device.deviceName + " already connected");
                            startWebSocketClient(info.groupOwnerAddress.getHostAddress());

                        } else if (getConnectionType() == ConnectionType.CLIENT && !info.groupFormed) {
                            // discoverPeers(1000);
                        }
                    }
                });

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

                // if (isWebSocketConnected) return;

                // NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                // WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                //
                // if (networkInfo.isConnected()) {
                //
                //     consoleLog("Connected to p2p network. Requesting network details");
                //
                //     p2pManager.requestConnectionInfo(p2pChannel, info -> {
                //
                //         consoleLog("requestConnectionInfo: CONNECTION_CHANGED onConnectionInfoAvailable groupFormed " + info.groupFormed);
                //         if (info.groupFormed && info.isGroupOwner) {
                //
                //             consoleLog("SERVER " + info.groupOwnerAddress);
                //
                //         } else if (info.groupFormed) {
                //
                //             consoleLog("CLIENT OF " + info.groupOwnerAddress);
                //             startWebSocketClient(info.groupOwnerAddress.getHostAddress());
                //             p2pManager.stopPeerDiscovery(p2pChannel, null);
                //         }
                //
                //     });
                // } else {
                //
                //     consoleLog("Disconnected from p2p network");
                //     if (getConnectionType() == ConnectionType.CLIENT) {
                //         discoverPeers(1000);
                //         stopWebSocketClient();
                //     }
                // }

            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                currentP2PDevice = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            }
        }
    };

    @Override
    public void onCreate() {

        REQUEST_CODE = NOTIFICATION_ID = 3005;
        CHANNEL_ID = "P2PService_Service_ID";
        CHANNEL_NAME = "P2PService Service Channel";
        super.onCreate();
        consoleLog("onCreate");

        dataCenter = StorageHelper.getDataCenter(this);
        powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        p2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        p2pChannel = p2pManager.initialize(this, getMainLooper(), null);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        registerReceiver(mBroadcastReceiver, intentFilter);

        if (getConnectionType() == ConnectionType.SERVER) {
            startWebSocketServer();
        }

        acquireCPU();
    }

    private void acquireCPU() {
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "myApp:MyWifiLock");
        wifiLock.acquire();
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myApp:MyWakeLock");
        wakeLock.acquire();
    }

    private void initiateConnection() {
        setDeviceName(null);
        if (getConnectionType() == ConnectionType.SERVER) {
            createGroup();
        } else {
            discoverPeers(100);
        }
    }

    public void requestConnectionInfo() {
        p2pManager.requestConnectionInfo(p2pChannel, info -> {

            consoleLog("requestConnectionInfo: onConnectionInfoAvailable groupFormed " + info.groupFormed);

            if (info.groupFormed && info.isGroupOwner) {

                consoleLog("SERVER " + info.groupOwnerAddress);

            } else if (info.groupFormed) {

                consoleLog("CLIENT OF " + info.groupOwnerAddress);
            }
        });
    }

    public void setDeviceName(String deviceName) {
        try {
            if (deviceName == null) {
                deviceName = "P2P-" + getDeviceSerial(this);
            }
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = p2pManager.getClass().getMethod("setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);
            Object[] argList = new Object[3];
            argList[0] = p2pChannel;
            argList[1] = deviceName;
            String fDeviceName = deviceName;
            argList[2] = new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    consoleLog("setDeviceName: onSuccess " + fDeviceName);
                }

                @Override
                public void onFailure(int reason) {
                    consoleLog("setDeviceName: onFailure " + fDeviceName);
                }
            };
            setDeviceName.invoke(p2pManager, argList);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void createGroup() {

        p2pManager.requestGroupInfo(p2pChannel, group -> {

            if (group == null) {

                p2pManager.createGroup(p2pChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        consoleLog("createGroup: onSuccess");
                    }

                    @Override
                    public void onFailure(int reason) {
                        consoleLog("createGroup: onFailure");
                    }
                });

            } else {
                consoleLog("group is already created " + group.getNetworkName());
            }
        });
    }

    public void removeGroup() {

        p2pManager.requestGroupInfo(p2pChannel, group -> {

            if (group != null) {

                p2pManager.removeGroup(p2pChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        consoleLog("removeGroup: onSuccess");
                    }

                    @Override
                    public void onFailure(int reason) {
                        consoleLog("removeGroup: onFailure");
                    }
                });

            } else {
                consoleLog("group is already removed");
            }
        });
    }

    private Runnable getDiscoverRunnable() {

        return new Runnable() {

            @Override
            public void run() {

                peers.clear();
                if (serviceListener != null) serviceListener.onPeersAvailable(peers);

                if (isP2PEnabled) {

                    p2pManager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                            consoleLog("discoverPeers: onSuccess");
                        }

                        @Override
                        public void onFailure(int reason) {
                            consoleLog("discoverPeers: onFailure");
                            discoverPeers(1000 * 10);
                        }
                    });
                }
            }
        };
    }

    public void discoverPeers(int delay) {
        consoleLog("discoverPeers: delay: " + delay);
        discoverHandler.removeCallbacks(discoverRunnable);
        discoverHandler.postDelayed(discoverRunnable, delay);
    }

    public void connectDevice(WifiP2pDevice device) {

        if (device.status == WifiP2pDevice.INVITED || device.status == WifiP2pDevice.CONNECTED) {

            consoleLog("connectDevice: already invited | connected " + device.status + " : " + device.deviceName);

        } else {

            WifiP2pConfig wifiP2pConfig = new WifiP2pConfig();
            wifiP2pConfig.deviceAddress = device.deviceAddress;

            p2pManager.connect(p2pChannel, wifiP2pConfig, new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    consoleLog("connectDevice: onSuccess " + device.deviceName);
                    dataCenter.connectedDeviceAddress = device.deviceAddress;
                    StorageHelper.storeDataCenter(getApplicationContext(), dataCenter);
                }

                @Override
                public void onFailure(int reason) {
                    consoleLog("connectDevice: onFailure " + device.deviceName);
                }
            });
        }
    }

    public void disconnectDevice(WifiP2pDevice device) {

        if (device.status == WifiP2pDevice.INVITED || device.status == WifiP2pDevice.CONNECTED) {

            p2pManager.cancelConnect(p2pChannel, new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    consoleLog("cancelConnect: onSuccess " + device.deviceName);
                }

                @Override
                public void onFailure(int reason) {
                    consoleLog("cancelConnect: onSuccess " + device.deviceName);
                }
            });

        } else {
            consoleLog("cancelConnect: already disconnected " + device.status + " : " + device.deviceName);
        }
    }

    public static String getStatusText(int statusCode) {
        String status = "UNKNOWN";
        switch (statusCode) {
            case WifiP2pDevice.AVAILABLE:
                status = "AVAILABLE";
                break;
            case WifiP2pDevice.CONNECTED:
                status = "CONNECTED";
                break;
            case WifiP2pDevice.FAILED:
                status = "FAILED";
                break;
            case WifiP2pDevice.INVITED:
                status = "INVITED";
                break;
            case WifiP2pDevice.UNAVAILABLE:
                status = "UNAVAILABLE";
                break;
        }
        return status;
    }

    @SuppressLint("NewApi")
    public static String getDeviceSerial(Context context) {
        String serial;
        try {
            serial = Build.SERIAL.equals(Build.UNKNOWN) ? Build.getSerial() : Build.SERIAL;
        } catch (Exception ex) {
            serial = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID).toUpperCase();
        }
        return serial;
    }

    public void startWebSocketServer() {

        stopWebSocketServer();

        webSocketServer = new MyWebSocketServer(Const.P2P_PORT, new MyWebSocketListener.Server() {

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
                consoleLog("WebSocket: received message from " + conn.getRemoteSocketAddress() + " : " + message);
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

        webSocketServer.setReuseAddr(true);
        webSocketServer.start();
    }

    public void stopWebSocketServer() {
        if (webSocketServer != null) {
            try {
                webSocketServer.stop(1000);
                consoleLog("WebSocket Server stopped");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            webSocketServer = null;
        }
    }

    public void startWebSocketClient(String host) {

        consoleLog("startWebSocketClient: " + host);

        if (webSocketClient != null && webSocketClient.isConnecting()) {
            return;
        }

        if (webSocketClient != null && webSocketClient.isOpen() && webSocketClient.host.equals(host)) {
            return;
        }

        stopWebSocketClient();

        try {

            URI uri = new URI("ws://" + host + ":" + Const.P2P_PORT);

            webSocketClient = new MyWebSocketClient(host, uri, new MyWebSocketListener.Client() {

                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    if (serviceListener != null && webSocketClient.isOpen()) {
                        consoleLog("WebSocket: new connection opened");
                        new Handler(Looper.getMainLooper()).post(() -> serviceListener.onWebSocketOpen());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (serviceListener != null && !webSocketClient.isOpen()) {
                        consoleLog("WebSocket: closed with exit code " + code + " additional info: " + reason);
                        new Handler(Looper.getMainLooper()).post(() -> serviceListener.onWebSocketClose());
                        p2pManager.requestConnectionInfo(p2pChannel, new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                if (!info.groupFormed) {
                                    discoverPeers(1);
                                }
                            }
                        });
                    }
                }

                @Override
                public void onMessage(String message) {
                    consoleLog("WebSocket: received message from " + webSocketClient.getRemoteSocketAddress() + " : " + message);
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

    public void stopWebSocketClient() {
        if (webSocketClient != null) {
            webSocketClient.forceClose();
            webSocketClient = null;
            consoleLog("WebSocket Client stopped");
        }
    }

    public ConnectionType getConnectionType() {
        return getCurrentConnectionType(this);
    }

    public static ConnectionType getCurrentConnectionType(Context context) {
        return getDeviceSerial(context).startsWith("PP") ? ConnectionType.SERVER : ConnectionType.CLIENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        releaseCPU();
    }

    private void releaseCPU() {
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
        if (wifiLock != null) {
            if (wifiLock.isHeld()) {
                wifiLock.release();
            }
        }
    }

    public boolean isWebSocketConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }

    public void setServiceListener(ServiceListener serviceListener) {
        this.serviceListener = serviceListener;
        // consoleLog("setServiceListener: success");
    }

    public void consoleLog(String message) {

        message = (logLine++) + " " + (new SimpleDateFormat("H:mm:ss").format(new Date(System.currentTimeMillis()))) + " => " + message;

        Log.e("P2PService", message);

        // new Handler(Looper.getMainLooper()).post(() -> {
        //     Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        // });

        logs.insert(0, message + "\n");

        if (serviceListener != null) {
            this.serviceListener.onConsoleLog(message);
        }

        // speech(this, message);
        buzz(100);
    }

    public void printLogs() {
        Log.e("P2PService", "------------------------<LOGS>------------------------");
        Log.e("P2PService", logs.toString());
        Log.e("P2PService", "------------------------</LOGS>------------------------");
    }

    public String getLogs() {

        StringBuilder output = new StringBuilder();
        String[] lines = logs.toString().split(System.getProperty("line.separator"));
        for (int i = 0; i < lines.length; i++) {
            if (i > 500) break;
            output.append(lines[i] + "\n");
        }

        return output.toString();
    }

    @SuppressLint("NewApi")
    public static void speech(Context context, String words) {

        try {

            if (words.contains("Offline voices for English")) return;
            // if (!words.contains("Socket")) return;

            String wordsToSay = words.replace("_", " ");

            if (textToSpeech != null) {
                textToSpeech.speak(wordsToSay, TextToSpeech.QUEUE_ADD, null, null);
                return;
            }

            textToSpeech = new TextToSpeech(context, status -> {
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.UK);
                    speech(context, wordsToSay);
                }
            });

        } catch (Exception ex) {
            // Speech error
        }
    }

    public void buzz(int duration) {
        buzz(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL, duration);
    }

    public void buzz(int tone, int duration) {
        Handler toneHandler = new Handler(Looper.getMainLooper());
        toneHandler.post(new Runnable() {
            @Override
            public void run() {
                if (toneGenerator == null) {
                    toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    toneGenerator.startTone(tone, duration);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        toneGenerator.stopTone();
                        toneGenerator.release();
                        toneGenerator = null;
                    }, duration + Const.TONE_INTERVAL);
                } else {
                    toneHandler.postDelayed(this, Const.TONE_INTERVAL);
                }
            }
        });
    }

    @Override
    protected Notification serviceNotification() {
        return createNotification("P2PService", "P2PService", R.drawable.ic_stat_settings_remote, R.drawable.ic_launcher, MainActivity.class);
    }
}
