package com.luoziyuan.powerrecord;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class PowerListActivity extends AppCompatActivity {

    private static String TAG = "PowerListActivity";

    private ListView powerList;
    private PowerListAdapter powerListAdapter;

    private MyService.MyBinder myBinder;
    private int interval;                   //刷新间隔，单位s

    private Handler handler;
    private Runnable updateListRunnable;    //用于更新ListView的Runnable对象

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_list);

        //获取ListView并设置点击事件
        powerList = findViewById(R.id.powerList);
        powerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(PowerListActivity.this,
                        PowerDetailActivity.class);
                intent.putExtra("uid", powerListAdapter.getPowerRecordUid(position));
                startActivity(intent);
            }
        });

        updateListRunnable = new Runnable() {
            @Override
            public void run() {
                //更新数据
                powerListAdapter.setData(myBinder.getPowerRecordList());
                //更新ListView
                powerListAdapter.notifyDataSetChanged();
                //再将Runnable对象自身放入队列，延迟interval秒取出执行
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

            //使用获取到的列表数据实例化适配器
            powerListAdapter = new PowerListAdapter(PowerListActivity.this,
                    myBinder.getPowerRecordList());
            //刷新ListView
            powerList.setAdapter(powerListAdapter);

            //获取interval
            interval = myBinder.getInterval();

            //创建Handler对象
            handler = new Handler();
            //将更新ListView的Runnable对象放入消息队列，延迟interval秒取出执行
            handler.postDelayed(updateListRunnable, interval * 1000);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
}