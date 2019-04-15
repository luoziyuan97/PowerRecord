package com.luoziyuan.powerrecord.adaptor;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.luoziyuan.powerrecord.data.PowerRecord;
import com.luoziyuan.powerrecord.R;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by John on 2019/3/20.
 */

public class PowerListAdaptor extends BaseAdapter {

    private Context context;
    private ArrayList<PowerRecord> data;

    @SuppressWarnings("unchecked")
    public PowerListAdaptor(Context context, ArrayList<PowerRecord> data)
    {
        this.context = context;
        this.data = (ArrayList<PowerRecord>) data.clone();
    }

    @SuppressWarnings("unchecked")
    public void setData(ArrayList<PowerRecord> newData)
    {
        data = (ArrayList<PowerRecord>) newData.clone();
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

    public int getPowerRecordUid(int position)
    {
        return data.get(position).uid;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder viewHolder;

        if (convertView == null)
        {
            //创建新view
            convertView = View.inflate(context, R.layout.listview_powerlist, null);
            viewHolder = new ViewHolder();
            viewHolder.imageView = convertView.findViewById(R.id.iconImage);
            viewHolder.packageNameText = convertView.findViewById(R.id.packageNameText);
            viewHolder.totalPowerText = convertView.findViewById(R.id.totalPowerText);
            viewHolder.lastIntervalPowerText = convertView.findViewById(
                    R.id.lastIntervalPowerText);
            convertView.setTag(viewHolder);
        }
        else
            //复用已有view
            viewHolder = (ViewHolder) convertView.getTag();

        PowerRecord dataItem = data.get(position);
        if (dataItem.icon != null)
            viewHolder.imageView.setImageDrawable(dataItem.icon);
        //如果应用名称未知，显示包名
        if (dataItem.label.equals("UNKNOWN"))
            viewHolder.packageNameText.setText(dataItem.packageName);
        else
            viewHolder.packageNameText.setText(dataItem.label);
        viewHolder.totalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                dataItem.totalPower[PowerRecord.ALL]));
        viewHolder.lastIntervalPowerText.setText(String.format(Locale.CHINA, "%.4f mAh",
                dataItem.lastIntervalPower[PowerRecord.ALL]));

        return convertView;
    }

    class ViewHolder
    {
        ImageView imageView;
        TextView packageNameText;
        TextView totalPowerText;
        TextView lastIntervalPowerText;
    }
}
