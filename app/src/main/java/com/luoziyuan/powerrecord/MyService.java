package com.luoziyuan.powerrecord;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.BatteryStats;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatteryStatsHelper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by John on 2019/1/29.
 */

public class MyService extends Service {

    private static final String TAG = "MyService";

    private Thread thread = null;
    private UsageStatsManager usageStatsManager = null;
    private List<UsageStats> usageStatsList = null;
    private SimpleDateFormat dateFormat = null;

    private CpuStats cpuStats;
    private HashMap<String, Double> appsPowerConsumption;
    private HashMap<String, Long> appsUseTime;
    private FileOutputStream outputStream;

    private MyBinder myBinder = new MyBinder();
    private Notification notification;
    private static final int NOTIFICATION_ID = 100;

    private long startTime;             //服务开始时间，单位ms
    private long runningTime = 0;       //服务运行时间，单位s

    private int interval = 1;           //取样间隔，单位s

    private DecimalFormat twoDecimalPlaces = new DecimalFormat("0.00");
    private DecimalFormat fourDecimalPlaces = new DecimalFormat("0.0000");

    private PackageManager packageManager;
    private SparseArray<String> packageNames;           //过滤掉系统应用(两类)之后的uid与包名映射
    private SparseArray<String> systemApps;             //uid大于10000的系统应用的uid与包名映射
    private SparseArray<PowerRecord> powerRecords;      //过滤掉系统应用之后的uid与PowerRecord映射

    private BatteryStatsHelper batteryStatsHelper;
    private List<BatterySipper> usageList;
    private List<PowerRecord> powerRecordList;      //过滤掉未启动应用的PowerRecord列表，用于展示

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return myBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        appsPowerConsumption = new HashMap<>();
        appsUseTime = new HashMap<>();

        batteryStatsHelper = new BatteryStatsHelper(this);
        packageManager = getPackageManager();
        packageNames = new SparseArray<>();
        systemApps = new SparseArray<>();
        powerRecords = new SparseArray<>();
        powerRecordList = new LinkedList<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");
        if (thread == null)
        {
            batteryStatsHelper.create(intent.getBundleExtra("icircle"));

            //创建临时日志文件（位于应用私有目录）
            String tempLogFilePath = this.getFileStreamPath("temp.log").getAbsolutePath();
            try {
                outputStream = new FileOutputStream(tempLogFilePath);
            } catch (Exception e) {
                e.printStackTrace();
            }

            //记录手机基本信息
            String info = "PhoneModel : " + Build.MODEL + "\n";
            info += "Manufacturer : " + Build.MANUFACTURER + "\n";
            info += "Android SDK level : " + Build.VERSION.SDK_INT + "\n";
            info += "PowerRecord version : " + BuildConfig.VERSION_NAME + "\n";
            try {
                outputStream.write(info.getBytes());
            } catch (IOException e) {
                Log.d(TAG, "failed to write temp log.");
            }
            Log.d(TAG, info);

            //实例化CpuStats对象
            cpuStats = new CpuStats(this, outputStream);

            //创建工作线程
            thread = new BatteryStatsThread();

            //记录开始时间
            dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            startTime = System.currentTimeMillis();

            //启动工作线程
            thread.start();
            Log.d(TAG, "start thread");

            //创建通知栏
            makeNotification();

            //将服务设置为前台
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (thread != null)
            thread.interrupt();

        stopForeground(true);
    }

    //工作线程
    class MyThread extends Thread
    {
        @Override
        public void run() {
            double sum;
            long useTime;
            while (!interrupted())    //正常状态通过标志位退出
            {
                //获取前台应用
                long time = System.currentTimeMillis();
                List<UsageStats> newList = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        time - 43200000, time);     //beginTime设置为12小时前
                if (newList != null && newList.size() > 0)
                    usageStatsList = newList;

                String writeString = "\nTime : ";
                Log.d(TAG, dateFormat.format(startTime + runningTime * 1000));
                writeString += dateFormat.format(startTime + runningTime * 1000) + "\n";

                if (usageStatsList != null)
                {
                    Collections.sort(usageStatsList, new Comparator<UsageStats>() {
                        @Override
                        public int compare(UsageStats o1, UsageStats o2) {
                            if (o1.getLastTimeUsed() > o2.getLastTimeUsed())
                                return -1;
                            else if (o1.getLastTimeUsed() == o2.getLastTimeUsed())
                                return 0;

                            return 1;
                        }
                    });

                    String foregroundPackage = usageStatsList.get(0).getPackageName();

                    Log.d(TAG, "Foreground package : " + foregroundPackage);
                    writeString += "Foreground package : " + foregroundPackage + "\n";

                    try {
                        outputStream.write(writeString.getBytes());
                        writeString = "";
                    } catch (Exception e) {
                        Log.d("WriteLog", "failed to write temp log.");
                    }

                    //获取记录的应用总耗电和使用时间
                    if (appsPowerConsumption.containsKey(foregroundPackage))
                        sum = appsPowerConsumption.get(foregroundPackage);
                    else
                        sum = 0;

                    if (appsUseTime.containsKey(foregroundPackage))
                        useTime = appsUseTime.get(foregroundPackage);
                    else
                        useTime = 0;

                    double cpuUseRate = cpuStats.getTotalCpuUseRate();
                    Log.d(TAG, "CpuUseRate : " + fourDecimalPlaces.format(cpuUseRate));
                    writeString += "CpuUseRate : " + fourDecimalPlaces.format(cpuUseRate) + "\n";

                    double powerConsumption = cpuStats.getTotalCpuPower();
                    Log.d(TAG, "LastSecondPowerConsumption : "
                            + twoDecimalPlaces.format(powerConsumption));
                    writeString += "LastSecondPowerConsumption : "
                            + twoDecimalPlaces.format(powerConsumption) + "\n";

                    sum += powerConsumption;
                    Log.d(TAG, "TotalPowerConsumption : " + twoDecimalPlaces.format(sum));
                    writeString += "TotalPowerConsumption : "
                            + twoDecimalPlaces.format(sum) + "\n";

                    useTime++;
                    Log.d(TAG, "TotalUseTime : " + useTime);
                    writeString += "TotalUseTime : " + useTime + "\n\n";

                    appsPowerConsumption.put(foregroundPackage, sum);
                    appsUseTime.put(foregroundPackage, useTime);

                    try {
                        outputStream.write(writeString.getBytes());
                    } catch (Exception e) {
                        Log.d("WriteLog", "failed to write temp log.");
                    }
                }

                //运行时间加1s
                runningTime++;

                //计算到下一次记录应该等待的时间
                long sleepTime = (startTime + runningTime * 1000) - System.currentTimeMillis();

                //记录所用时间超过一秒，跳到下一个记录点
                if (sleepTime < 0)
                {
                    while ((startTime + runningTime * 1000) < System.currentTimeMillis())
                    {
                        runningTime++;
                        Log.w(TAG, "skip 1 second");
                        try {
                            outputStream.write("skip 1 second\n".getBytes());
                        } catch (IOException e) {
                            Log.d("WriteLog", "failed to write temp log.");
                        }
                    }
                }

                //正常等待至下一个记录点
                else
                {
                    try{
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e){           //阻塞状态通过中断 + break退出
                        Log.d(TAG, "InterruptedException");
                        break;
                    }
                }
            }

            Log.d(TAG, "Working thread exits.");
            try {
                outputStream.write("Working thread exits.\n".getBytes());
            } catch (IOException e) {
                Log.d("WriteLog", "failed to write temp log.");
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.d("WriteLog", "failed to close the outputStream.");
            }
        }
    }

    class BatteryStatsThread extends Thread
    {
        @Override
        public void run() {
            while (!interrupted())    //正常状态通过标志位退出
            {
                //记录当前时间
                StringBuilder writeString = new StringBuilder();
                writeString.append("\nTime : ");
                String timePoint = dateFormat.format(
                        startTime + runningTime * interval * 1000);
                Log.d(TAG, timePoint);
                writeString.append(timePoint);
                writeString.append("\n");

                //刷新系统耗电记录信息
                batteryStatsHelper.clearStats();
                batteryStatsHelper.refreshStats(BatteryStats.STATS_CURRENT,
                        UserHandle.USER_ALL);
                usageList = batteryStatsHelper.getUsageList();

                ApplicationInfo applicationInfo = new ApplicationInfo();
                String packageName;

                for (BatterySipper usage : usageList)
                {
                    //忽略系统应用
                    if (usage.getUid() < 10000 || (systemApps.get(usage.getUid()) != null))
                        continue;

                    //尝试从已有映射中获取包名
                    packageName = packageNames.get(usage.getUid());

                    //如果失败则通过packageManager获取该uid对应的包名，并添加到映射
                    if (packageName == null)
                    {
                        packageName = packageManager.getNameForUid(usage.getUid());
                        if (packageName != null)
                        {
                            try {
                                applicationInfo = packageManager.getApplicationInfo(packageName,
                                        0);
                            } catch (PackageManager.NameNotFoundException e) {
                                Log.d(TAG, "application not found");
                            }
                            //uid大于10000的系统应用，存入systemApps并忽略
                            if (applicationInfo.isSystemApp())
                            {
                                systemApps.put(usage.getUid(), packageName);
                                continue;

                            }
                            //普通应用
                            else
                            {
                                //存入uid和包名映射
                                packageNames.put(usage.getUid(), packageName);
                                Drawable icon = null;
                                try {
                                    icon = packageManager.getApplicationIcon(packageName);
                                } catch (PackageManager.NameNotFoundException e) {
                                    Log.d(TAG, "icon not found");
                                }
                                //创建PowerRecord并添加到powerRecord映射
                                PowerRecord powerRecord = new PowerRecord(usage.getUid(),
                                        packageName, icon, usage);
                                CharSequence label;
                                label = packageManager.getApplicationLabel(applicationInfo);
                                if (label != null)
                                    powerRecord.label = packageManager.getApplicationLabel(
                                            applicationInfo).toString();
                                else
                                    powerRecord.label = "UNKNOWN";
                                powerRecords.put(usage.getUid(), powerRecord);
                            }
                        }
                        //忽略uid大于10000且获取不到包名的普通应用（虽然不大可能出现）
                        else
                            continue;
                    }

                    //获取应用耗电信息，忽略耗电量过低的应用
                    if (usage.totalPowerMah > 0.01)
                    {
                        PowerRecord powerRecord = powerRecords.get(usage.getUid());
                        if (powerRecord.isRunning)
                        {
                            //更新PowerRecord
                            powerRecord.updatePower(usage);

                            writeString.append("uid : ");
                            writeString.append(usage.getUid());
                            writeString.append(", power : ");
                            writeString.append(fourDecimalPlaces.
                                    format(powerRecord.totalPower[PowerRecord.ALL]));
                            writeString.append(", lastIntervalPower : ");
                            writeString.append(fourDecimalPlaces.
                                    format(powerRecord.lastIntervalPower[PowerRecord.ALL]));
                            writeString.append("\n");
                        }
                        else
                        {
                            //如果当前获取到的该应用耗电值大于首次获取到的耗电量，视为应用启动
                            //添加到展示列表
                            if (usage.totalPowerMah > powerRecord.startPower[PowerRecord.ALL])
                            {
                                powerRecord.isRunning = true;
                                powerRecordList.add(powerRecord);
                            }
                        }
                    }
                }

                //按照总耗电量的高低降序排列
                Collections.sort(powerRecordList, new Comparator<PowerRecord>() {
                    @Override
                    public int compare(PowerRecord o1, PowerRecord o2) {
                        if (o1.totalPower[PowerRecord.ALL] > o2.totalPower[PowerRecord.ALL])
                            return -1;
                        else if (o1.totalPower[PowerRecord.ALL] < o2.totalPower[PowerRecord.ALL])
                            return 1;
                        return 0;
                    }
                });

                Log.d(TAG, writeString.toString());
                try {
                    outputStream.write(writeString.toString().getBytes());
                } catch (Exception e) {
                    Log.d("WriteLog", "failed to write temp log.");
                }

                //应用运行时间加上一个间隔时间
                runningTime += interval;

                //计算到下一次记录应该等待的时间
                long sleepTime = (startTime + runningTime * interval * 1000)
                        - System.currentTimeMillis();

                //记录所用时间超过一个间隔时间，跳到下一个记录点
                if (sleepTime < 0)
                {
                    while ((startTime + runningTime * interval * 1000) < System.currentTimeMillis())
                    {
                        runningTime += interval;
                        String message = "skip " + interval + " second\n";
                        Log.w(TAG, message);
                        try {
                            outputStream.write(message.getBytes());
                        } catch (IOException e) {
                            Log.d("WriteLog", "failed to write temp log.");
                        }
                    }
                }

                //正常等待至下一个记录点
                else
                {
                    try{
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e){           //阻塞状态通过中断 + break退出
                        Log.d(TAG, "InterruptedException");
                        break;
                    }
                }
            }

            Log.d(TAG, "Working thread exits.");
            try {
                outputStream.write("\nWorking thread exits.\n".getBytes());
            } catch (IOException e) {
                Log.d("WriteLog", "failed to write temp log.");
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.d("WriteLog", "failed to close the outputStream.");
            }
        }
    }

    //服务类使用的自定义Binder类
    class MyBinder extends Binder
    {
        public boolean isRunning()
        {
            if (thread != null)
                if (!thread.isInterrupted())
                    return true;
            return false;
        }

        public boolean isFinished()
        {
            if (thread != null)
                if (thread.isInterrupted())
                    return true;
            return false;
        }

        public long getStartTime()
        {
            return startTime;
        }

        public SparseArray<String> getPackageNames()
        {
            return packageNames;
        }

        public List<PowerRecord> getPowerRecordList()
        {
            return powerRecordList;
        }

        public int getInterval()
        {
            return interval;
        }
    }

    //创建通知栏
    private void makeNotification()
    {
        //实例化通知栏构建器
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext());

        //设置构建器，包括标题、内容、大小图标、通知时间
        builder.setContentTitle("正在记录...");
        builder.setContentText("开始时间:" + dateFormat.format(startTime));
        builder.setSmallIcon(R.mipmap.ic_battery_round);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_battery));

        //设置点击通知之后的跳转页面
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        //构建通知
        notification = builder.build();
    }
}
