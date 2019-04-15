package com.luoziyuan.powerrecord.data;

import android.graphics.drawable.Drawable;
import android.os.Build;

import com.android.internal.os.BatterySipper;

/**
 * Created by John on 2019/3/19.
 */

public class PowerRecord implements Cloneable
{
    //表示耗电组成部分的常量
    public static final int ALL = 0;
    public static final int CPU = 1;
    public static final int WAKELOCK = 2;
    public static final int MOBILE_RADIO = 3;
    public static final int WIFI = 4;
    public static final int BLUETOOTH = 5;
    public static final int SENSOR = 6;
    public static final int CAMERA = 7;
    public static final int FLASHLIGHT = 8;
    public static final String[] componentNames = new String[]{"All", "CPU", "Wakelock", "3G/4G",
                    "Wifi", "Bluetooth", "Sensor", "Camera", "Flashlight"};

    public int uid;
    public String packageName;
    public String label;
    public Drawable icon;

    public boolean isRunning;
    public double[] startPower;
    public double[] totalPower;
    public double[] lastIntervalPower;

    public PowerRecord(int uid, String packageName, Drawable icon, BatterySipper usage)
    {
        this.uid = uid;
        this.packageName = packageName;
        this.icon = icon;

        isRunning = false;

        startPower = new double[9];
        startPower[ALL] = usage.totalPowerMah;
        startPower[CPU] = usage.cpuPowerMah;
        startPower[WAKELOCK] = usage.wakeLockPowerMah;
        startPower[MOBILE_RADIO] = usage.mobileRadioPowerMah;
        startPower[WIFI] = usage.wifiPowerMah;
        //Android 7.0及以上才有蓝牙耗电数据
        if (Build.VERSION.SDK_INT >= 24)
            startPower[BLUETOOTH] = usage.bluetoothPowerMah;
        startPower[SENSOR] = usage.sensorPowerMah;
        startPower[CAMERA] = usage.cameraPowerMah;
        startPower[FLASHLIGHT] = usage.flashlightPowerMah;

        totalPower = new double[9];
        lastIntervalPower = new double[9];
    }

    //使用最近的统计耗电量更新记录
    public void updatePower(BatterySipper usage)
    {
        lastIntervalPower[ALL] = usage.totalPowerMah - totalPower[ALL] - startPower[ALL];
        lastIntervalPower[CPU] = usage.cpuPowerMah - totalPower[CPU] - startPower[CPU];
        lastIntervalPower[WAKELOCK] = usage.wakeLockPowerMah - totalPower[WAKELOCK]
                - startPower[WAKELOCK];
        lastIntervalPower[MOBILE_RADIO] = usage.mobileRadioPowerMah - totalPower[MOBILE_RADIO]
                - startPower[MOBILE_RADIO];
        lastIntervalPower[WIFI] = usage.wifiPowerMah - totalPower[WIFI] - startPower[WIFI];
        //Android 7.0及以上才有蓝牙耗电数据
        if (Build.VERSION.SDK_INT >= 24)
            lastIntervalPower[BLUETOOTH] = usage.bluetoothPowerMah - totalPower[BLUETOOTH]
                    - startPower[BLUETOOTH];
        lastIntervalPower[SENSOR] = usage.sensorPowerMah - totalPower[SENSOR] - startPower[SENSOR];
        lastIntervalPower[CAMERA] = usage.cameraPowerMah - totalPower[CAMERA] - startPower[CAMERA];
        lastIntervalPower[FLASHLIGHT] = usage.flashlightPowerMah - totalPower[FLASHLIGHT]
                - startPower[FLASHLIGHT];

        for (int i = 0; i < 9; i++)
            if (lastIntervalPower[i] < 0)
                lastIntervalPower[i] = 0;

        totalPower[ALL] = usage.totalPowerMah - startPower[ALL];
        totalPower[CPU] = usage.cpuPowerMah - startPower[CPU];
        totalPower[WAKELOCK] = usage.wakeLockPowerMah - startPower[WAKELOCK];
        totalPower[MOBILE_RADIO] = usage.mobileRadioPowerMah - startPower[MOBILE_RADIO];
        totalPower[WIFI] = usage.wifiPowerMah - startPower[WIFI];
        //Android 7.0及以上才有蓝牙耗电数据
        if (Build.VERSION.SDK_INT >= 24)
            totalPower[BLUETOOTH] = usage.bluetoothPowerMah - startPower[BLUETOOTH];
        totalPower[SENSOR] = usage.sensorPowerMah - startPower[SENSOR];
        totalPower[CAMERA] = usage.cameraPowerMah - startPower[CAMERA];
        totalPower[FLASHLIGHT] = usage.flashlightPowerMah - startPower[FLASHLIGHT];
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
