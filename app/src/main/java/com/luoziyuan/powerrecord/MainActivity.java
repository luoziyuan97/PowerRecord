package com.luoziyuan.powerrecord;

import android.Manifest;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.BatteryStats;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.app.procstats.ProcessState;
import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.os.PowerProfile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private int PROC_SPACE_TERM = (int)' ';
    private int PROC_TAB_TERM = (int)'\t';
    private int PROC_LINE_TERM = (int)'\n';
    private int PROC_COMBINE = 0x100;
    private int PROC_OUT_STRING = 0x1000;
    private int PROC_OUT_LONG = 0x2000;
    private int READ_LONG_FORMAT = PROC_SPACE_TERM|PROC_OUT_LONG;
    private int READ_STRING_FORMAT = PROC_OUT_STRING|PROC_LINE_TERM;

    private int numOfCpus;

    private Button startServiceButton;
    private Button stopServiceButton;
    private Button saveLogButton;

    private TextView mainText;

    private MyService.MyBinder myBinder;

    private BatteryStatsHelper batteryStatsHelper;

    private SparseArray<String> packageNames;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryStatsHelper = new BatteryStatsHelper(this);
        batteryStatsHelper.create(savedInstanceState);

        //运行时申请存储权限
        EasyPermissions.requestPermissions(this, "申请权限", 0,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        //获取CPU个数
        numOfCpus = Runtime.getRuntime().availableProcessors();

        //开始记录按钮
        startServiceButton = findViewById(R.id.startServiceButton);
        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasUsageAccess())
                {
                    Toast.makeText(MainActivity.this, "未获取查看使用情况权限，" +
                            "请点击右上角菜单->打开使用情况设置进行授权", Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent(MainActivity.this, MyService.class);
                intent.putExtra("icircle", savedInstanceState);
                startService(intent);
                bindService(intent, connection, 0);
                startServiceButton.setEnabled(false);
                stopServiceButton.setEnabled(true);
                saveLogButton.setEnabled(false);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                String text = "正在记录...\n" + "开始时间:" +
                        dateFormat.format(System.currentTimeMillis());
                mainText.setText(text);
            }
        });

        //停止记录按钮
        stopServiceButton = findViewById(R.id.stopServiceButton);
        stopServiceButton.setEnabled(false);
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //保存本次记录涉及的uid和包名映射
                packageNames = myBinder.getPackageNames();

                //停止服务
                Intent intent = new Intent(MainActivity.this, MyService.class);
                stopService(intent);
                unbindService(connection);
                myBinder = null;

                startServiceButton.setEnabled(true);
                stopServiceButton.setEnabled(false);
                saveLogButton.setEnabled(true);

                String text = "记录完成\n请及时保存记录！\n一旦关闭应用记录将丢失！";
                mainText.setText(text);
            }
        });

        //保存记录按钮
        saveLogButton = findViewById(R.id.saveLogButton);
        saveLogButton.setEnabled(false);
        saveLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获取临时记录文件
                String tempLogFilePath = getFileStreamPath("temp.log").getAbsolutePath();
                FileInputStream inputStream;
                try {
                    inputStream = new FileInputStream(tempLogFilePath);
                } catch (FileNotFoundException e) {
                    Toast.makeText(MainActivity.this, "未找到记录", Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                //创建新记录文件
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyy-MM-dd_HH-mm-ss");
                long time = System.currentTimeMillis();
                File writeFile = new File(
                        Environment.getExternalStorageDirectory(), "PowerLog" +
                        dateFormat.format(time) + ".log");

                //写入记录
                try {
                    BufferedInputStream logIn = new BufferedInputStream(inputStream);
                    BufferedOutputStream logOut = new BufferedOutputStream(
                            new FileOutputStream(writeFile));

                    //在记录开始部分写入涉及的uid和包名映射
                    StringBuilder writeString = new StringBuilder();
                    for (int i = 0; i < packageNames.size(); i++)
                    {
                        writeString.append("uid : ");
                        writeString.append(packageNames.keyAt(i));
                        writeString.append(", package : ");
                        writeString.append(packageNames.valueAt(i));
                        writeString.append("\n");
                    }
                    writeString.append("\n");
                    logOut.write(writeString.toString().getBytes(), 0, writeString.length());

                    //先读临时记录文件，再写入要保存的文件
                    byte[] buffer = new byte[20480];
                    for(int len = logIn.read(buffer); len != -1; len = logIn.read(buffer))
                    {
                        logOut.write(buffer, 0, len);
                    }

                    logIn.close();
                    logOut.close();

                    String text = "成功保存日志到:" + writeFile.getAbsolutePath();
                    mainText.setText(text);
                    saveLogButton.setEnabled(false);

                    return;
                } catch(java.io.EOFException e) {
                    String text = "成功保存日志到:" + writeFile.getAbsolutePath();
                    mainText.setText(text);
                    saveLogButton.setEnabled(false);

                    return;
                } catch(IOException e) {
                }

                String text = "无法保存日志到SD卡，请检查应用是否拥有存储权限";
                mainText.setText(text);
                writeFile.delete();
            }
        });

        //提示框
        mainText = findViewById(R.id.mainText);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //绑定服务
        Intent intent = new Intent(MainActivity.this, MyService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        //解绑服务
        if (myBinder != null)
            unbindService(connection);
    }

    //菜单选项ID
    private static final int POWER_PROFILE = 0;
    private static final int CPU_FREQ = 1;
    private static final int CPU_STATS = 2;
    private static final int FOREGROUND_PACKAGE = 3;
    private static final int APP_CPU_STAT = 4;
    private static final int TEST = 5;
    private static final int USAGE_ACCESS_SETTING = 6;

    //创建选项菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, POWER_PROFILE, 0, "powerProfile");
        menu.add(0, CPU_FREQ, 0, "cpuFreq");
        menu.add(0, CPU_STATS, 0, "cpuStats");
        menu.add(0, FOREGROUND_PACKAGE, 0, "foreground");
        menu.add(0, APP_CPU_STAT, 0, "appCpuStat");
        menu.add(0, TEST, 0, "test");
        if (hasUsageAccessSettingOption())
            menu.add(0, USAGE_ACCESS_SETTING, 0, "打开使用情况设置");
        return true;
    }

    //菜单各个选项的点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case POWER_PROFILE:
            {
                String text = "numOfCpus : " + numOfCpus;

                PowerProfile powerProfile = new PowerProfile(this);

                //获取CPU可运行频率数，魅族M2Note Android5.1报NoSuchMethudError
//                int numOfFrequencies = powerProfile.getNumSpeedStepsInCpuCluster(0);

                //手动获取cpu可运行频率数
                int numOfFrequencies = 0;
                String itemName;
                double last = 0, temp = 0;

                for (int i = 0; true; i++)
                {
                    //依次读取cpu的各个频率
                    temp = powerProfile.getAveragePower("cpu.speeds", i);

                    //前后两次读取的值相同，说明已经将所有值读出
                    if (Math.abs(temp - last) < 0.1)
                    {
                        numOfFrequencies = i;
                        itemName = "cpu.speeds";
                        break;
                    }
                    last = temp;
                }

                //如果没读出任何频率，可能是power_profile.xml中的项目名不同
                if(numOfFrequencies == 0)
                {
                    for (int i = 0; true; i++)
                    {
                        //使用如下项目名再次尝试
                        temp = powerProfile.getAveragePower("cpu.speeds.cluster0", i);

                        if (Math.abs(temp - last) < 0.1)
                        {
                            numOfFrequencies = i;
                            itemName = "cpu.speeds.cluster0";
                            break;
                        }
                        last = temp;
                    }
                }

                //依然没读出任何频率
                if (numOfFrequencies == 0)
                {
                    Log.d(TAG, "failed to read power_profile.xml");
                    Toast.makeText(MainActivity.this,
                            "failed to read power_profile.xml",
                            Toast.LENGTH_LONG).show();
                    break;
                }

                Log.d(TAG, itemName);
                text += "\n" + "numOfFrequencies : " + numOfFrequencies;

                //获取CPU可运行各个频率值和相应的电流值，单位kHz,mA
                double[] cpuFrequencies = new double[numOfFrequencies];
                double[] cpuCurrents = new double[numOfFrequencies];
                text += "\n" + "cluster0 :";
                for (int i = 0; i < numOfFrequencies; i++)
                {
                    if (itemName.equals("cpu.speeds"))
                    {
                        cpuFrequencies[i] = powerProfile.getAveragePower("cpu.speeds", i);
                        cpuCurrents[i] = powerProfile.getAveragePower("cpu.active", i);
                    }
                    else
                    {
                        cpuFrequencies[i] = powerProfile.getAveragePower(
                                "cpu.speeds.cluster0", i);
                        cpuCurrents[i] = powerProfile.getAveragePower(
                                "cpu.active.cluster0", i);
                    }
                    text += "\n" + cpuFrequencies[i] + " : " + cpuCurrents[i];
                }

                Log.d(TAG, "numCpuClusters : " + powerProfile.getNumCpuClusters());
                text += "\n" + "numCpuClusters : " + powerProfile.getNumCpuClusters();

                Log.d(TAG, text);
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();

                break;
            }

            case CPU_FREQ :
            {
                long[] freq = new long[1];
                int[] format = new int[]{READ_LONG_FORMAT};

                String pathPart1 = "/sys/devices/system/cpu/cpu";
                String pathPart2 = "/cpufreq/scaling_cur_freq";

                String text = "currentCpuFrequencies : ";

                //获取每个cpu的实时频率
                for (int i = 0; i < numOfCpus; i++)
                {
                    Process.readProcFile(pathPart1 + i + pathPart2, format,
                            null, freq, null);
                    text += "\n" + "cpu" + i + ":" + freq[0];
                }

                Log.d(TAG, text);
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
                break;
            }

            case CPU_STATS :
            {
                String[] stats = new String[numOfCpus + 1];
                int[] format = new int[numOfCpus + 1];
                for (int i = 0; i < format.length; i++)
                    format[i] = PROC_OUT_STRING;

                //获取cpu的状态信息
                Process.readProcFile("proc/stat", format,
                        stats, null, null);
                for (int i = 0; i <= numOfCpus; i++)
                {
                    Log.d(TAG, "i:" + i);
                    if (stats[i] != null)
                        Log.d(TAG, stats[i]);
                }

                if (stats[0] != null)
                    Toast.makeText(MainActivity.this, stats[0], Toast.LENGTH_LONG).show();
                break;
            }

            case FOREGROUND_PACKAGE :
            {
                //获取当前运行的所有进程的包名
                UsageStatsManager usageStatsManager =
                        (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                long time = System.currentTimeMillis();
                List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        time - 43200000, time);     //beginTime设置为12小时前

                //按lastTimeUsed降序排列
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
                Toast.makeText(this, "foregroundPackage : " + foregroundPackage,
                        Toast.LENGTH_LONG).show();

                //根据包名获取ApplicationInfo
                PackageManager packageManager = getPackageManager();
                ApplicationInfo applicationInfo;

                for (UsageStats usageStats : usageStatsList)
                {
                    applicationInfo = null;
                    try{
                        applicationInfo = packageManager.getApplicationInfo(
                                usageStats.getPackageName(), 0);
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                    SimpleDateFormat dateFormat = new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss");

                    String info = usageStats.getPackageName();
                    if (applicationInfo != null)
                    {
                        info += " : " + applicationInfo.uid;
                        info += "\nlastTimeUsed : " + dateFormat.format(
                                usageStats.getLastTimeUsed());
                        info += "\nlastTimeStamp : " + dateFormat.format(
                                usageStats.getLastTimeStamp());
                    }


//                    if (!applicationInfo.isSystemApp())
                        Log.d(TAG, info);
                }
                break;
            }

            case APP_CPU_STAT :
            {
                try {
                    //执行top命令并取结果，获取应用pid
                    java.lang.Process process = Runtime.getRuntime().
                            exec("ls proc");
                    InputStream inputStream = process.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                    String info = null;
                    int pid = 0;

                    while ((info = reader.readLine()) != null)
                        Log.d(TAG, info);

                    //关闭对象
                    reader.close();
                    inputStream.close();
                    process.destroy();

                } catch (IOException e) {
                    Log.d(TAG, "error reading proc/stat");
                    Toast.makeText(this, "error reading proc/[pid]/stat",
                            Toast.LENGTH_LONG).show();
                }

                break;
            }

            case TEST :
            {
                Log.d(TAG, "option item test");

                if (EasyPermissions.hasPermissions(MainActivity.this,
                        Manifest.permission.BATTERY_STATS))
                    Log.d(TAG, "BATTERY_STATS permission granted!");

                batteryStatsHelper.clearStats();
                batteryStatsHelper.refreshStats(BatteryStats.STATS_SINCE_UNPLUGGED,
                        UserHandle.USER_ALL);
                List<BatterySipper> usageList = batteryStatsHelper.getUsageList();
                for (BatterySipper usage : usageList)
                {
                    Log.d(TAG, usage.getUid() + " : " + usage.totalPowerMah);
//                    if (usage.getUid() == 10153)
//                        Toast.makeText(this, "" + usage.totalPowerMah,
//                                Toast.LENGTH_SHORT).show();
                }
                String info = "ComputedPower : " + batteryStatsHelper.getComputedPower();
                Log.d(TAG, info);
                Toast.makeText(this, info, Toast.LENGTH_SHORT).show();

                break;
            }

            case USAGE_ACCESS_SETTING :
            {
                Log.d(TAG, "usage access setting");
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
                break;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult()");
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults,
                this);
    }

    //判断手机是否具有查看使用情况设置
    private boolean hasUsageAccessSettingOption()
    {
        PackageManager packageManager = getApplicationContext()
                .getPackageManager();
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    //判断是否获取了查看使用情况权限
    private boolean hasUsageAccess()
    {
        long time = System.currentTimeMillis();
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(
                Context.USAGE_STATS_SERVICE);
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, 0, time);
        if (usageStatsList == null || usageStatsList.isEmpty())
            return false;
        return true;
    }

    //匿名内部类，服务连接对象
    private ServiceConnection connection = new ServiceConnection() {

        //和服务绑定成功后，服务会回调该方法
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected()");

            myBinder = (MyService.MyBinder) service;

            //根据工作线程的状态更改按钮状态和提示框信息
            if (myBinder.isRunning())
            {
                startServiceButton.setEnabled(false);
                stopServiceButton.setEnabled(true);

                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss");
                String text = "正在记录...\n" + "开始时间:" +
                        dateFormat.format(myBinder.getStartTime());
                mainText.setText(text);
            }
        }

        //当服务异常终止时会调用，解除绑定服务时不会调用
        @Override
        public void onServiceDisconnected(ComponentName name) {

            Log.d(TAG, "onServiceDisconnected() called");

            //在临时日志文件中追加服务终止信息
            String tempLogFilePath = MainActivity.this.
                    getFileStreamPath("temp.log").getAbsolutePath();
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(tempLogFilePath, true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (outputStream != null)
            {
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss");
                long time = System.currentTimeMillis();

                String text = "\nService disconnected at " + dateFormat.format(time) + ".\n";
                try {
                    outputStream.write(text.getBytes());
                } catch (IOException e) {
                    Log.d(TAG, "failed to write temp log");
                }
            }

        }
    };
}
