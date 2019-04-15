package com.luoziyuan.powerrecord.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.luoziyuan.powerrecord.MyAppWidget;
import com.luoziyuan.powerrecord.service.MyService;
import com.luoziyuan.powerrecord.data.PowerRecord;
import com.luoziyuan.powerrecord.R;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button startServiceButton;
    private Button powerListButton;
    private Button stopServiceButton;
    private Button saveLogButton;

    private TextView mainText;

    private MyService.MyBinder myBinder;

    private ArrayList<PowerRecord> powerRecords;        //本次记录包含的应用信息
    private int[] uidsToSave;                           //要保存的应用uid

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //检测是否是首次运行
        SharedPreferences settings = getSharedPreferences("settings", 0);
        boolean firstRun = settings.getBoolean("firstRun", true);
        if (firstRun)
        {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("firstRun", false);
            editor.apply();

            //首次运行时提示用户阅读使用引导
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setTitle("欢迎使用");
            alertBuilder.setMessage("正式使用之前，请先详细阅读右上角菜单的使用引导");
            alertBuilder.setPositiveButton("确定",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertBuilder.setNegativeButton("直接打开引导页面",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();

                            Intent intent = new Intent(
                                    MainActivity.this, GuideActivity.class);
                            startActivity(intent);
                        }
                    });
            alertBuilder.show();
        }

        //运行时申请存储权限
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);

        //开始记录按钮
        startServiceButton = findViewById(R.id.startServiceButton);
        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //检测是否具有耗电统计权限
                if (!hasBatteryStatsPermission())
                {
                    Toast.makeText(MainActivity.this, "未获取耗电统计权限",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                //检测手机是否处于充电状态
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = MainActivity.this.registerReceiver(null, ifilter);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = ((status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                        (status == BatteryManager.BATTERY_STATUS_FULL));
                if (isCharging)
                {
                    //首次运行时提示用户阅读使用引导
                    AlertDialog.Builder alertBuilder =
                            new AlertDialog.Builder(MainActivity.this);
                    alertBuilder.setTitle("提示");
                    alertBuilder.
                            setMessage("检测到手机正在充电，充电状态下无法获取到耗电信息，请拔出USB");
                    alertBuilder.setPositiveButton("确定",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    startRecord(savedInstanceState);
                                }
                            });
                    alertBuilder.setNegativeButton("取消",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertBuilder.show();
                }
                else
                    startRecord(savedInstanceState);
            }
        });

        //耗电排行按钮
        powerListButton = findViewById(R.id.powerListButton);
        powerListButton.setEnabled(false);
        powerListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,
                        PowerListActivity.class);
                startActivity(intent);
            }
        });

        //停止记录按钮
        stopServiceButton = findViewById(R.id.stopServiceButton);
        stopServiceButton.setEnabled(false);
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //获取本次记录涉及的应用信息
                powerRecords = myBinder.getPowerRecordList();

                //停止服务
                Intent intent = new Intent(MainActivity.this, MyService.class);
                stopService(intent);
                unbindService(connection);
                myBinder = null;

                //更新按钮状态
                startServiceButton.setEnabled(true);
                startServiceButton.setText("重新开始");
                powerListButton.setEnabled(false);
                stopServiceButton.setEnabled(false);
                saveLogButton.setEnabled(true);

                //更新提示信息
                String text = "记录完成\n请及时保存记录！\n一旦关闭应用记录将丢失！";
                mainText.setText(text);

                //更新AppWidget
                ComponentName componentName = new ComponentName(MainActivity.this,
                        MyAppWidget.class);
                RemoteViews remoteViews = new RemoteViews(MainActivity.this.getPackageName(),
                        R.layout.my_app_widget);
                remoteViews.setTextViewText(R.id.appwidget_stateText, "未运行");
                remoteViews.setTextColor(R.id.appwidget_stateText, Color.GRAY);

                AppWidgetManager appWidgetManager = AppWidgetManager.
                        getInstance(getApplicationContext());
                appWidgetManager.updateAppWidget(componentName, remoteViews);
            }
        });

        //保存记录按钮
        saveLogButton = findViewById(R.id.saveLogButton);
        saveLogButton.setEnabled(false);
        saveLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //创建Dialog让用户选择需要记录的应用耗电数据
                AlertDialog.Builder dialogBuilder =
                        new AlertDialog.Builder(MainActivity.this);

                //包含本次记录所有应用名的列表
                final String[] apps = new String[powerRecords.size()];
                for (int i = 0; i < apps.length; i++)
                    apps[i] = powerRecords.get(i).label;

                dialogBuilder.setTitle("选择需要保存的应用记录");
                final boolean[] checkedItems = new boolean[apps.length];

                //设置多选框
                dialogBuilder.setMultiChoiceItems(apps, checkedItems,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                Log.d(TAG, apps[which] + " : " + isChecked);
                            }
                        });

                //设置确定和取消按钮
                dialogBuilder.setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                mainText.setText("正在保存记录...");

                                //统计一共有多少应用被选中
                                int count = 0;
                                for (Boolean checked : checkedItems)
                                    if (checked)
                                        count++;

                                //保存需要记录的应用的uid
                                uidsToSave = new int[count];
                                for (int i = 0, j = 0; i < checkedItems.length; i++)
                                    if (checkedItems[i])
                                        uidsToSave[j++] = powerRecords.get(i).uid;

                                //创建新的线程保存记录文件
                                new SaveLogThread().start();
                            }
                        });
                dialogBuilder.setNegativeButton("取消",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });

                dialogBuilder.show();
            }
        });

        //提示框
        mainText = findViewById(R.id.mainText);
    }

    //启动服务
    private void startRecord(final Bundle savedInstanceState)
    {
        //启动服务并绑定
        Intent intent = new Intent(MainActivity.this, MyService.class);
        intent.putExtra("icircle", savedInstanceState);
        //Android8.0以上启动前台服务必须这样写
        if (Build.VERSION.SDK_INT>=26)
            startForegroundService(intent);
        else
            startService(intent);
        bindService(intent, connection, 0);

        //更新按钮状态
        startServiceButton.setEnabled(false);
        powerListButton.setEnabled(true);
        stopServiceButton.setEnabled(true);
        saveLogButton.setEnabled(false);

        //更新提示信息
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String text = "正在记录...\n" + "开始时间:" +
                dateFormat.format(System.currentTimeMillis());
        mainText.setText(text);

        //更新AppWidget
        ComponentName componentName = new ComponentName(MainActivity.this,
                MyAppWidget.class);
        RemoteViews remoteViews = new RemoteViews(MainActivity.this.getPackageName(),
                R.layout.my_app_widget);
        remoteViews.setTextViewText(R.id.appwidget_stateText, "正在记录...");
        remoteViews.setTextColor(R.id.appwidget_stateText, Color.BLACK);

        AppWidgetManager appWidgetManager = AppWidgetManager.
                getInstance(getApplicationContext());
        appWidgetManager.updateAppWidget(componentName, remoteViews);
    }

    //保存记录使用的线程
    class SaveLogThread extends Thread
    {
        String resultInfo = "无法保存日志到SD卡，请检查应用是否拥有存储权限";
        boolean save = true;

        @Override
        public void run() {
            //获取临时记录文件
            String tempLogFilePath = getFileStreamPath("temp.log").
                    getAbsolutePath();
            FileReader fileReader;
            try {
                fileReader = new FileReader(tempLogFilePath);
            } catch (FileNotFoundException e) {
                Toast.makeText(MainActivity.this,
                        "未找到记录", Toast.LENGTH_LONG).show();
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
                BufferedReader reader = new BufferedReader(fileReader);
                BufferedOutputStream logOut = new BufferedOutputStream(
                        new FileOutputStream(writeFile));
                String line;

                //在记录开始部分写入涉及的uid和包名、应用名映射
                StringBuilder writeString = new StringBuilder();
                for (PowerRecord powerRecord : powerRecords)
                {
                    if (isChosenToSave(powerRecord.uid))
                    {
                        writeString.append("uid : ");
                        writeString.append(powerRecord.uid);
                        writeString.append(", package : ");
                        writeString.append(powerRecord.packageName);
                        writeString.append(", app : ");
                        writeString.append(powerRecord.label);
                        writeString.append("\n");
                    }
                }
                writeString.append("\n");
                byte[] writeBytes = writeString.toString().getBytes();
                logOut.write(writeBytes, 0, writeBytes.length);

                int uid;

                //按行读临时记录文件，再写入要保存的文件
                while ((line = reader.readLine()) != null)
                {
                    //判断uid对应的应用记录是否需要保存
                    if (line.startsWith("uid"))
                    {
                        uid = Integer.decode(line.substring(6));
                        if (isChosenToSave(uid))
                            save = true;
                        else
                            save = false;
                    }

                    //读到空行表明一次采样记录已结束
                    else if (line.equals(""))
                        save = true;

                    //readLine()返回值不包含换行符，需手动加上
                    line += "\n";

                    //写入新记录文件
                    if (save)
                    {
                        writeBytes = line.getBytes();
                        logOut.write(writeBytes, 0, writeBytes.length);
                    }
                }

                logOut.close();
                resultInfo = "成功保存日志到:" + writeFile.getAbsolutePath();
            } catch(EOFException e) {
                resultInfo = "成功保存日志到:" + writeFile.getAbsolutePath();
            } catch(IOException e) {
                Log.d(TAG, "failed to write log");
            }

            //刷新提示信息
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainText.setText(resultInfo);
                    saveLogButton.setEnabled(false);
                }
            });
        }
    }

    //判断用户是否选择了保存指定uid对应应用的记录
    private boolean isChosenToSave(int uid)
    {
        for (int uidToSave : uidsToSave)
            if (uid == uidToSave)
                return true;
        return false;
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
    private static final int SETTING = 1;
    private static final int GUIDANCE = 2;

    //创建选项菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, SETTING, 0, "设置");
        menu.add(0, GUIDANCE, 0, "使用引导");
        return true;
    }

    //菜单各个选项的点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case SETTING :
            {
                //构建自定义Dialog
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                final View dialogView = View.inflate(this,
                        R.layout.dialog_setting, null);
                dialogBuilder.setTitle("设置");
                dialogBuilder.setView(dialogView);

                final TextView intervalText = dialogView.
                        findViewById(R.id.intervalText_settingDialog);
                final EditText inputText = dialogView.findViewById(R.id.inputText_settingDialog);

                //设置显示内容
                final SharedPreferences settings =
                        getSharedPreferences("settings", 0);
                String message = "当前采样间隔为"
                        + settings.getInt("interval", 1) + "秒";
                intervalText.setText(message);

                dialogBuilder.setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if (inputText.getText().toString().equals(""))
                                {
                                    dialog.dismiss();
                                    return;
                                }

                                String input = inputText.getText().toString();

                                //将输入存入SharedPreferences
                                try {
                                    int newInterval = Integer.decode(input);
                                    SharedPreferences.Editor editor = settings.edit();
                                    editor.putInt("interval", newInterval);
                                    editor.apply();
                                    Toast.makeText(MainActivity.this,
                                            "设置成功，重新开始记录以生效",
                                            Toast.LENGTH_LONG).show();
                                } catch (NumberFormatException e) {
                                    Toast.makeText(MainActivity.this,
                                            "输入只能是正整数", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                dialogBuilder.setNegativeButton("取消",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                dialogBuilder.show();

                break;
            }

            case GUIDANCE :
            {
                Intent intent = new Intent(this, GuideActivity.class);
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

        //用户未授予存储权限时给出Dialog提示
        if (requestCode == 0)
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                dialogBuilder.setTitle("提示");
                dialogBuilder.setMessage("未授予存储权限，将无法保存记录文件");
                dialogBuilder.setPositiveButton("去设置授予",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            }
                        });
                dialogBuilder.setNegativeButton("忽略",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                dialogBuilder.show();
            }
    }

    //检查是否获取了BATTERY_STATS权限
    private boolean hasBatteryStatsPermission()
    {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED;
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
                powerListButton.setEnabled(true);
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
