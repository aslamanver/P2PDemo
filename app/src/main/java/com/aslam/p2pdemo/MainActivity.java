package com.aslam.p2pdemo;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.aslam.p2pdemo.databinding.ActivityMainBinding;
import com.aslam.p2pdemo.services.BaseForegroundService;
import com.aslam.p2pdemo.services.P2PService;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;
    DeviceAdapter deviceAdapter;

    P2PService p2pService;
    boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            P2PService.LocalBinder binder = (P2PService.LocalBinder) service;
            p2pService = (P2PService) binder.getService();
            mBound = true;
            consoleLog("onServiceConnected");

            setTitle(p2pService.getConnectionType().toString());
            if (p2pService.getConnectionType() == P2PService.ConnectionType.CLIENT) {
                getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor(p2pService.isWebSocketConnected() ? "#008000" : "#FF0000")));
            }

            p2pService.setServiceListener(new P2PService.ServiceListener() {

                @Override
                protected void onConsoleLog(String message) {
                    consoleLog(message);
                }

                @Override
                protected void onPeersAvailable(List<WifiP2pDevice> peers) {
                    deviceAdapter.notifyDataSetChanged();
                }

                @Override
                protected void onWebSocketOpen() {
                    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#008000")));
                }

                @Override
                protected void onWebSocketClose() {
                    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FF0000")));
                }
            });

            deviceAdapter = new DeviceAdapter(getApplicationContext(), p2pService.peers, (position, device) -> {
                if (device.isGroupOwner()) {
                    if (device.status == WifiP2pDevice.CONNECTED) {
                        p2pService.disconnectDevice(device);
                    } else {
                        p2pService.connectDevice(device);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "It's not an owner", Toast.LENGTH_SHORT).show();
                }
            });

            binding.listView.setAdapter(deviceAdapter);
            p2pService.printLogs();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBound = false;
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        BaseForegroundService.start(this, P2PService.class);
        bindService(new Intent(this, P2PService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        if (P2PService.getCurrentConnectionType(this) == P2PService.ConnectionType.CLIENT) {
            binding.btnCreateGroup.setVisibility(View.GONE);
            binding.btnRemoveGroup.setVisibility(View.GONE);
        }

        binding.btnScan.setOnClickListener(v -> p2pService.discoverPeers(1));
        binding.btnConnectionInfo.setOnClickListener(v -> p2pService.requestConnectionInfo());
        binding.btnCreateGroup.setOnClickListener(v -> p2pService.createGroup());
        binding.btnRemoveGroup.setOnClickListener(v -> p2pService.removeGroup());

        binding.btnSocket.setOnClickListener(v -> {
            String message = String.valueOf(new Random().nextInt(1000));
            if (p2pService.getConnectionType() == P2PService.ConnectionType.CLIENT && p2pService.webSocketClient != null) {
                p2pService.webSocketClient.send(message);
                p2pService.consoleLog("WebSocket: sent " + message);
            } else if (p2pService.webSocketServer != null) {
                p2pService.webSocketServer.send(message);
                p2pService.consoleLog("WebSocket: sent " + message);
            }
        });
    }

    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askPermission();
        }
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

    private void consoleLog(String message) {
        runOnUiThread(() -> {
            binding.txtLogs.setText(p2pService.getLogs().toString());
        });
    }
}