package com.aslam.p2pdemo;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.aslam.p2pdemo.databinding.DeviceLayoutRowBinding;

import java.util.List;

public class DeviceAdapter extends BaseAdapter {

    interface EventListener {
        void onClickEvent(int position, WifiP2pDevice device);
    }

    Context context;
    List<WifiP2pDevice> deviceList;
    EventListener eventListener;

    DeviceAdapter(Context context, List<WifiP2pDevice> deviceList, EventListener eventListener) {
        this.context = context;
        this.deviceList = deviceList;
        this.eventListener = eventListener;
    }

    @Override
    public int getCount() {
        return deviceList.size();
    }

    @Override
    public Object getItem(int position) {
        return deviceList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        DeviceLayoutRowBinding binding = DeviceLayoutRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);

        final WifiP2pDevice device = deviceList.get(position);

        binding.txtName.setText(device.deviceName + " " + MainActivity.getStatusText(device.status) + " isOwner " + device.isGroupOwner());
        binding.txtAddress.setText(device.deviceAddress);

        binding.mainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eventListener.onClickEvent(position, device);
            }
        });

        return binding.getRoot();
    }
}
