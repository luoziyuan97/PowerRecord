package com.luoziyuan.powerrecord.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.luoziyuan.powerrecord.BuildConfig;
import com.luoziyuan.powerrecord.data.PowerRecord;
import com.luoziyuan.powerrecord.R;
import com.luoziyuan.powerrecord.activity.MainActivity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by John on 2019/1/29.
 */

public class MyService extends Service {

    private static final String TAG = "MyService";

    private Thread thread = null;
    private SimpleDateFormat dateFormat = null;

    private FileOutputStream outputStream;

    private MyBinder myBinder = new MyBinder();
    private Notification notification;
    private static final int NOTIFICATION_ID = 100;

    private long startTime;             //服务开始时间，单位ms
    private long runningTime = 0;       //服务运行时间，单位s

    private int interval = 1;           //取样间隔，单位s

    private DecimalFormat fourDecimalPlaces = new DecimalFormat("0.0000");

    private PackageManager packageManager;
    private SparseArray<String> packageNames;           //过滤掉系统应用之后的uid与包名映射
    private SparseArray<String> systemApps;             //包名以android开头的系统应用的uid与包名映射
    private SparseArray<PowerRecord> powerRecords;      //过滤掉系统应用之后的uid与PowerRecord映射

    private BatteryStatsHelper batteryStatsHelper;
    private List<BatterySipper> usageList;
    private ArrayList<PowerRecord> powerRecordList;     //过滤掉未启动应用的PowerRecord列表，用于展示

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return myBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        batteryStatsHelper = new BatteryStatsHelper(this);
        packageManager = getPackageManager();
        packageNames = new SparseArray<>();
        systemApps = new SparseArray<>();
        powerRecords = new SparseArray<>();
        powerRecordList = new ArrayList<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");
        if (thread == null)
        {
            //从SharedPreferences中读取interval，如果没有默认为1s
            SharedPreferences settings = getSharedPreferences("settings", 0);
            interval = settings.getInt("interval", 1);

            //初始化BatteryStatsHelper类对象
            batteryStatsHelper.create(intent.getBundleExtra("icircle"));

            //创建临时日志文件（位于应用私有目录）
            String tempLogFilePath = this.getFileStreamPath("temp.log").getAbsolutePath();
            try {
                outputStream = new FileOutputStream(tempLogFilePath);
            } catch (Exception e) {
                e.printStackTrace();
            }

            //记录手机基本信息以及应用版本、记录采样间隔
            String info = "PhoneModel : " + Build.MODEL + "\n";
            info += "Manufacturer : " + Build.MANUFACTURER + "\n";
            info += "Android API level : " + Build.VERSION.SDK_INT + "\n";
            info += "PowerRecord version : " + BuildConfig.VERSION_NAME + "\n";
            info += "Interval : " + interval + "s\n";
            try {
                outputStream.write(info.getBytes());
            } catch (IOException e) {
                Log.d(TAG, "failed to write temp log.");
            }
            Log.d(TAG, info);

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

    class BatteryStatsThread extends Thread
    {
        @Override
        public void run() {
            while (!interrupted())    //正常状态通过标志位退出
            {
                //记录当前时间
                StringBuilder writeString = new StringBuilder();
                writeString.append("\nTime : ");
                String timePoint = dateFormat.format(startTime + runningTime * 1000);
                Log.d(TAG, timePoint);
                writeString.append(timePoint);
                writeString.append("\n");

                //刷新系统耗电记录信息
                batteryStatsHelper.clearStats();
                batteryStatsHelper.refreshStats(BatteryStats.STATS_SINCE_UNPLUGGED,
                        UserHandle.USER_ALL);
                usageList = batteryStatsHelper.getUsageList();

                ApplicationInfo applicationInfo = new ApplicationInfo();
                String packageName;

                for (BatterySipper usage : usageList)
                {
                    //忽略系统应用
                    if (usage.getUid() < 10000 || (systemApps.get(usage.getUid()) != null))
                        continue;
//                    if ((systemApps.get(usage.getUid()) != null))
//                        continue;

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
                                Log.d(TAG, packageName + ":application not found");
                            }

                            //包名以android开头的uid大于10000的系统应用，存入systemApps并忽略
//                            if (applicationInfo.isSystemApp())
//                            if (false)
                            if (packageName.startsWith("android"))
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

                        //如果当前获取到的该应用耗电值大于首次获取到的耗电量，视为应用启动
                        //添加到展示列表
                        if (!powerRecord.isRunning)
                        {
                            if (usage.totalPowerMah > powerRecord.startPower[PowerRecord.ALL])
                            {
                                powerRecord.isRunning = true;
                                powerRecordList.add(powerRecord);
                            }
                        }

                        if (powerRecord.isRunning)
                        {
                            //更新PowerRecord
                            powerRecord.updatePower(usage);

                            //记录此次采样该应用各个组件的耗电量
                            writeString.append("uid : ");
                            writeString.append(usage.getUid());
                            writeString.append("\n");

                            for (int i = 0; i < powerRecord.totalPower.length; i++)
                            {
                                //忽略耗电量低于0.0001的组件
                                if (powerRecord.totalPower[i] > 0.0001)
                                {
                                    writeString.append(PowerRecord.componentNames[i]);
                                    writeString.append(" : ");
                                    writeString.append(fourDecimalPlaces.
                                            format(powerRecord.totalPower[i]));
                                    writeString.append(", ");
                                    writeString.append(fourDecimalPlaces.
                                            format(powerRecord.lastIntervalPower[i]));
                                    writeString.append("\n");
                                }
                            }
                        }
                    }
                }

                //按照总耗电量的高低降序排列，便于展示
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
                long sleepTime = (startTime + runningTime * 1000) - System.currentTimeMillis();

                //记录所用时间超过一个间隔时间，跳到下一个记录点
                if (sleepTime < 0)
                {
                    while ((startTime + runningTime * 1000) < System.currentTimeMillis())
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
    public class MyBinder extends Binder
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

        public ArrayList<PowerRecord> getPowerRecordList()
        {
            return powerRecordList;
        }

        public int getInterval()
        {
            return interval;
        }

        public PowerRecord getPowerRecord(int uid)
        {
            return powerRecords.get(uid);
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

        //安卓8.0以上需要先创建通知频道，并设置所构建通知的频道Id
        if (Build.VERSION.SDK_INT>=26)
        {
            NotificationManager manager =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel Channel = new NotificationChannel(
                    "com.luoziyuan.powerrecord.channel","前台服务",
                    NotificationManager.IMPORTANCE_HIGH);
            Channel.setDescription("耗电记录仪前台服务通知");
            manager.createNotificationChannel(Channel);
            builder.setChannelId("com.luoziyuan.powerrecord.channel");
        }

        //设置点击通知之后的跳转页面
        Intent intent = new Intent(this, MainActivity.class);
        //设置跳转模式，使得跳转之后让任务栈中的耗电排行、耗电详情等Activity出栈，显示栈底的MainActivity
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        //至关重要的一句，加上之后setFlags才起作用！
        intent.setAction(Intent.ACTION_MAIN);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        //构建通知
        notification = builder.build();
    }
}
