package com.aslam.p2pdemo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.aslam.p2pdemo.databinding.ActivityMainBinding;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements DeviceAdapter.EventListener {

    ActivityMainBinding binding;
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    List<WifiP2pDevice> peers = new ArrayList<>();
    DeviceAdapter deviceAdapter;
    // SocketServer socketServer;
    // SocketClient socketClient;
    WifiP2pDevice currentDevice;
    SocketThread socketThread;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    updateLog("Wifi P2P is enabled");
                    buttonEnabled(true);
                } else {
                    updateLog("Wi-Fi P2P is not enabled");
                    buttonEnabled(false);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (manager != null) {
                    manager.requestPeers(channel, peerListListener);
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (manager != null) {
                    NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    if (networkInfo.isConnected()) {
                        updateLog("Connected to p2p network. Requesting network details");
                        requestConnectionInfo();
                    } else {
                        updateLog("Disconnected from p2p network");
                    }
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                currentDevice = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            }
        }
    };

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            if (!wifiP2pDeviceList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());
                deviceAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        deviceAdapter = new DeviceAdapter(this, peers, this);
        binding.listView.setAdapter(deviceAdapter);

        buttonEnabled(false);

        final String correctName = "Device-" + getDeviceSerial(getApplicationContext());
        binding.btnSetName.setText("Set Name: " + correctName);
        binding.btnSetName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDeviceName(correctName);
            }
        });

        binding.btnServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startSocketServer();

                manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if (group != null) {
                            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    updateLog("removeGroup: onSuccess");
                                }

                                @Override
                                public void onFailure(int reason) {
                                    updateLog("removeGroup: onFailure");
                                }
                            });
                        }
                        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                updateLog("createGroup: onSuccess");
                            }

                            @Override
                            public void onFailure(int reason) {
                                updateLog("createGroup: onFailure");
                            }
                        });
                    }
                });
            }
        });

        binding.btnClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        updateLog("requestConnectionInfo: onConnectionInfoAvailable groupFormed " + info.groupFormed);
                        if (info.groupFormed) {
                            startSocketClient(info.groupOwnerAddress.getHostAddress());
                        }
                    }
                });
            }
        });

        binding.btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discoverPeers();
            }
        });

        binding.btnConnectionInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestConnectionInfo();
            }
        });

        binding.btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        if (info.groupFormed) {
                            if (info.isGroupOwner) {
                                if (socketThread != null) {
                                    socketThread.sendData("Something from server " + currentDevice.deviceName + " data " + new Random().nextInt(10000));
                                    updateLog("socketServer: sendData to clients : true");
                                }
                            } else {
                                if (socketThread != null) {
                                    socketThread.sendData("Something from client " + currentDevice.deviceName + " data " + new Random().nextInt(20000));
                                    updateLog("socketServer: sendData sendData to server: true");
                                }
                            }
                        }
                    }
                });
                // sendLongSMS();
            }
        });
    }

    public void buttonEnabled(boolean enabled) {
        binding.btnServer.setEnabled(enabled);
        binding.btnClient.setEnabled(enabled);
        binding.btnScan.setEnabled(enabled);
        binding.btnConnectionInfo.setEnabled(enabled);
        binding.btnSend.setEnabled(enabled);
    }

    public void setDeviceName(String devName) {
        try {
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = manager.getClass().getMethod("setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);
            Object arglist[] = new Object[3];
            arglist[0] = channel;
            arglist[1] = devName;
            arglist[2] = new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    updateLog("setDeviceName: onSuccess");
                }

                @Override
                public void onFailure(int reason) {
                    updateLog("setDeviceName: onSuccess");
                }
            };
            setDeviceName.invoke(manager, arglist);
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

    private void startSocketClient(String host) {

        if (socketThread != null) {
            socketThread.interrupt();
        }

        socketThread = new SocketThread(SocketThread.SocketType.CLIENT, 45454, host, new CommunicationThread.SocketListener() {
            @Override
            public void onConnected(String message) {
                updateLog("SocketClient: onConnected " + message);
            }

            @Override
            public void onDisconnected(EOFException exception) {
                updateLog("SocketClient: onDisconnected " + exception.getMessage());
            }

            @Override
            public void onFailed(Exception exception) {
                updateLog("SocketClient: onFailed " + exception.getMessage());
            }

            @Override
            public void onDataReceived(String data) {
                updateLog("SocketClient: onDataReceived " + data);
            }
        });

        socketThread.start();
    }

    private void startSocketServer() {

        if (socketThread != null) {
            socketThread.interrupt();
        }

        socketThread = new SocketThread(SocketThread.SocketType.SERVER, 45454, null, new CommunicationThread.SocketListener() {
            @Override
            public void onConnected(String message) {
                updateLog("SocketServer: onConnected " + message);
            }

            @Override
            public void onDisconnected(EOFException exception) {
                updateLog("SocketServer: onDisconnected");
            }

            @Override
            public void onFailed(Exception exception) {
                updateLog("SocketServer: onFailed " + exception.getMessage());
            }

            @Override
            public void onDataReceived(String data) {
                updateLog("SocketServer: onDataReceived " + data);
            }
        });

        socketThread.start();
    }

    private void discoverPeers() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                updateLog("discoverPeers: onSuccess");
            }

            @Override
            public void onFailure(int reason) {
                updateLog("discoverPeers: onFailure");
            }
        });
    }

    private void requestConnectionInfo() {
        manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                updateLog("requestConnectionInfo: onConnectionInfoAvailable groupFormed " + info.groupFormed);
                if (info.groupFormed && info.isGroupOwner) {
                    updateLog("SERVER " + info.groupOwnerAddress);
                } else if (info.groupFormed) {
                    updateLog("CLIENT " + info.groupOwnerAddress);
                }
            }
        });
    }

    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askPermission();
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askPermission();
            return;
        }

        unregisterReceiver(broadcastReceiver);
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
            }, 600);
        }
    }

    private void updateLog(final String message) {
        Log.e("P2PDemo", message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.txtStatus.setText(message);
            }
        });
    }

    @Override
    public void onClickEvent(int position, final WifiP2pDevice device) {

        if (device.status == WifiP2pDevice.INVITED || device.status == WifiP2pDevice.CONNECTED) {
            manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    updateLog("cancelConnect: onSuccess " + device.deviceName);
                }

                @Override
                public void onFailure(int reason) {
                    updateLog("cancelConnect: onSuccess " + device.deviceName);
                }
            });
            return;
        }

        WifiP2pConfig wifiP2pConfig = new WifiP2pConfig();
        wifiP2pConfig.deviceAddress = device.deviceAddress;
        manager.connect(channel, wifiP2pConfig, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                updateLog("connect: onSuccess " + device.deviceName);
            }

            @Override
            public void onFailure(int reason) {
                updateLog("connect: onFailure " + device.deviceName);
            }
        });
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

    public static String getDeviceSerial(Context context) {
        String serial = null;
        try {
            serial = Build.SERIAL.equals(Build.UNKNOWN) ? Build.getSerial() : Build.SERIAL;
        } catch (Exception ex) {
            ex.printStackTrace();
            serial = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return serial;
    }
}