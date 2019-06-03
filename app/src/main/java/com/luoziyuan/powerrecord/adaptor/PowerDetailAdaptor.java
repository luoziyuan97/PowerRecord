package com.luoziyuan.powerrecord.adaptor;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.luoziyuan.powerrecord.data.PowerRecord;
import com.luoziyuan.powerrecord.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/**
 * Created by John on 2019/3/22.
 */

public class PowerDetailAdaptor extends BaseAdapter {

    private Context context;
    private double totalPowerAll;
    private ArrayList<PowerDetail> data;

    public PowerDetailAdaptor(Context context)
    {
        this.context = context;
        data = new ArrayList<>(3);
    }

    public void updateData(PowerRecord powerRecord)
    {
        totalPowerAll = powerRecord.totalPower[PowerRecord.ALL];
        data.clear();

        if (powerRecord.totalPower[PowerRecord.CPU] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("CPU",
                    powerRecord.totalPower[PowerRecord.CPU],
                    powerRecord.lastIntervalPower[PowerRecord.CPU]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.WAKELOCK] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("唤醒锁",
                    powerRecord.totalPower[PowerRecord.WAKELOCK],
                    powerRecord.lastIntervalPower[PowerRecord.WAKELOCK]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.MOBILE_RADIO] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("3G/4G",
                    powerRecord.totalPower[PowerRecord.MOBILE_RADIO],
                    powerRecord.lastIntervalPower[PowerRecord.MOBILE_RADIO]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.WIFI] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("Wifi",
                    powerRecord.totalPower[PowerRecord.WIFI],
                    powerRecord.lastIntervalPower[PowerRecord.WIFI]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.BLUETOOTH] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("蓝牙",
                    powerRecord.totalPower[PowerRecord.BLUETOOTH],
                    powerRecord.lastIntervalPower[PowerRecord.BLUETOOTH]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.SENSOR] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("传感器",
                    powerRecord.totalPower[PowerRecord.SENSOR],
                    powerRecord.lastIntervalPower[PowerRecord.SENSOR]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.CAMERA] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("相机",
                    powerRecord.totalPower[PowerRecord.CAMERA],
                    powerRecord.lastIntervalPower[PowerRecord.CAMERA]);
            data.add(powerDetail);
        }

        if (powerRecord.totalPower[PowerRecord.FLASHLIGHT] > 0.0001)
        {
            PowerDetail powerDetail = new PowerDetail("闪光灯",
                    powerRecord.totalPower[PowerRecord.FLASHLIGHT],
                    powerRecord.lastIntervalPower[PowerRecord.FLASHLIGHT]);
            data.add(powerDetail);
        }

        Collections.sort(data, new Comparator<PowerDetail>() {
            @Override
            public int compare(PowerDetail o1, PowerDetail o2) {
                if (o1.totalPower > o2.totalPower)
                    return -1;
                else if (o1.totalPower < o2.totalPower)
                    return 1;
                return 0;
            }
        });
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder viewHolder;

        if (convertView == null)
        {
            //创建新view
            convertView = View.inflate(context, R.layout.listview_powerdetail, null);
            viewHolder = new ViewHolder();
            viewHolder.componentNameText = convertView.
                    findViewById(R.id.componentNameText_PowerDetailList);
            viewHolder.totalPowerText = convertView.
                    findViewById(R.id.totalPowerText_PowerDetailList);
            viewHolder.lastIntervalPowerText = convertView.
                    findViewById(R.id.lastIntervalPowerText_PowerDetailList);
            viewHolder.percentBar = convertView.
                    findViewById(R.id.percentBar_PowerDetailList);
            convertView.setTag(viewHolder);
        }
        else
            //复用已有view
            viewHolder = (ViewHolder) convertView.getTag();

        //设置view的内容
        PowerDetail dataItem = data.get(position);
        viewHolder.componentNameText.setText(dataItem.componentName);
        viewHolder.totalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                dataItem.totalPower));
        viewHolder.lastIntervalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                dataItem.lastIntervalPower));
        viewHolder.percentBar.setProgress((int)(dataItem.totalPower * 100 / totalPowerAll));

        return convertView;
    }

    class ViewHolder
    {
        TextView componentNameText;
        TextView totalPowerText;
        TextView lastIntervalPowerText;
        ProgressBar percentBar;
    }

    class PowerDetail
    {
        public String componentName;
        public double totalPower;
        public double lastIntervalPower;

        public PowerDetail(String componentName, double totalPower, double lastIntervalPower)
        {
            this.componentName = componentName;
            this.totalPower = totalPower;
            this.lastIntervalPower = lastIntervalPower;
        }
    }
}
