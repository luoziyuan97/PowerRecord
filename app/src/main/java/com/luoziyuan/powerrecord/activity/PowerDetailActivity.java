package com.luoziyuan.powerrecord.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.luoziyuan.powerrecord.service.MyService;
import com.luoziyuan.powerrecord.adaptor.PowerDetailListAdaptor;
import com.luoziyuan.powerrecord.data.PowerRecord;
import com.luoziyuan.powerrecord.R;

import java.text.DecimalFormat;
import java.util.Locale;

public class PowerDetailActivity extends AppCompatActivity {

    private static final String TAG = "PowerDetailActivity";

    private int uid;
    private PowerRecord powerRecord;

    private MyService.MyBinder myBinder;
    private int interval;

    private Handler handler;
    private Runnable updateDetailRunnable;

    private ImageView iconImage;
    private TextView appNameText;
    private TextView packageNameText;

    private TextView totalPowerText;
    private TextView lastIntervalPowerText;
    private ListView powerDetailList;
    private PowerDetailListAdaptor listAdaptor;

    private DecimalFormat fourDecimalPlaces = new DecimalFormat("0.0000");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_detail);

        //上方标题栏添加返回按钮
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        iconImage = findViewById(R.id.iconImage_PowerDetail);
        appNameText = findViewById(R.id.appNameText_PowerDetail);
        packageNameText = findViewById(R.id.packageNameText_PowerDetail);

        totalPowerText = findViewById(R.id.totalPowerText_PowerDetail);
        lastIntervalPowerText = findViewById(R.id.lastIntervalPowerText_PowerDetail);
        powerDetailList = findViewById(R.id.powerDetails);

        //获取点选中应用的uid
        uid = getIntent().getIntExtra("uid", 0);

        updateDetailRunnable = new Runnable() {
            @Override
            public void run() {
                //更新数据
                try {
                    powerRecord = (PowerRecord) myBinder.getPowerRecord(uid).clone();
                } catch (CloneNotSupportedException e) {
                    Log.e(TAG, "can't clone PowerRecord");
                }

                //更新UI
                totalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                        powerRecord.totalPower[PowerRecord.ALL]));
                lastIntervalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                        powerRecord.lastIntervalPower[PowerRecord.ALL]));

                listAdaptor.updateData(powerRecord);
                listAdaptor.notifyDataSetChanged();

                //再将Runnable对象自身放入队列，延迟interval秒取出执行
                handler.postDelayed(this, interval * 1000);
            }
        };
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //标题栏返回按钮点击事件
        if (item.getItemId() == android.R.id.home)
        {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
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
        handler.removeCallbacks(updateDetailRunnable);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myBinder = (MyService.MyBinder) service;

            //获取数据
            try {
                powerRecord = (PowerRecord) myBinder.getPowerRecord(uid).clone();
            } catch (CloneNotSupportedException e) {
                Log.e(TAG, "can't clone powerRecord");
            }

            //更新UI
            if (powerRecord.icon != null)
                iconImage.setImageDrawable(powerRecord.icon);
            appNameText.setText(powerRecord.label);
            packageNameText.setText(powerRecord.packageName);

            totalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                    powerRecord.totalPower[PowerRecord.ALL]));
            lastIntervalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                    powerRecord.lastIntervalPower[PowerRecord.ALL]));

            listAdaptor = new PowerDetailListAdaptor(PowerDetailActivity.this);
            listAdaptor.updateData(powerRecord);
            powerDetailList.setAdapter(listAdaptor);

            //获取interval
            interval = myBinder.getInterval();

            //创建Handler对象
            handler = new Handler();
            //将更新ListView的Runnable对象放入消息队列，延迟interval秒取出执行
            handler.postDelayed(updateDetailRunnable, interval * 1000);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
}
