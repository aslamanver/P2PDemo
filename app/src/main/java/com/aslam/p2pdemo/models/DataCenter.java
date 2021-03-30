package com.aslam.p2pdemo.models;

import android.content.Context;

import com.aslam.p2pdemo.utils.StorageHelper;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class DataCenter {

    @Expose
    @SerializedName("connected_device_address")
    public String connectedDeviceAddress;
}
