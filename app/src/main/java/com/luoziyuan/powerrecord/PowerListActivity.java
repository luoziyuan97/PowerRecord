package com.luoziyuan.powerrecord;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;

public class PowerListActivity extends AppCompatActivity {

    private static String TAG = "PowerListActivity";

    private ListView powerList;
    private PowerListAdapter powerListAdapter;

    private List<PowerRecord> powerRecords;

    private MyService.MyBinder myBinder;
    private int interval;                   //刷新间隔，单位s

    private Handler handler;
    private Runnable updateListRunnable;    //用于更新ListView的Runnable对象

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_list);

        powerList = findViewById(R.id.powerList);

        updateListRunnable = new Runnable() {
            @Override
            public void run() {
                //更新ListView
                powerList.setAdapter(powerListAdapter);
                //再将Runnable对象自身放入对象，延迟interval秒取出执行
                handler.postDelayed(this, interval * 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        //绑定服务
        Intent intent = new Intent(this, MyService.class);
        bindService(intent, connection, BIND_AUTO_CREATE);

        //创建Handler对象
        handler = new Handler();
        //将更新ListView的Runnable对象放入消息队列，延迟interval秒取出执行
        handler.postDelayed(updateListRunnable, interval * 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();

        //解绑服务
        if (myBinder != null)
            unbindService(connection);

        //移除Runnable对象，防止消息队列出现多个
        handler.removeCallbacks(updateListRunnable);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myBinder = (MyService.MyBinder) service;

            //获取PowerRecordList
            powerRecords = myBinder.getPowerRecordList();
            //使用获取到的列表数据实例化适配器
            powerListAdapter = new PowerListAdapter(PowerListActivity.this,
                    R.layout.listview, powerRecords);
            //刷新ListView
            powerList.setAdapter(powerListAdapter);

            //获取interval
            interval = myBinder.getInterval();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
}
