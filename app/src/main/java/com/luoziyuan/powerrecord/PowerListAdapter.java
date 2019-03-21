package com.luoziyuan.powerrecord;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Created by John on 2019/3/20.
 */

public class PowerListAdapter extends BaseAdapter {

    //ListView所使用的布局
    private int layoutId;

    private Context context;
    private List<PowerRecord> data;

    public PowerListAdapter(Context context, int layoutId, List<PowerRecord> data)
    {
        this.layoutId = layoutId;
        this.context = context;
        this.data = data;
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
            convertView = View.inflate(context, R.layout.listview, null);
            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.iconImage);
            viewHolder.packageNameText = (TextView) convertView.findViewById(R.id.packageNameText);
            viewHolder.totalPowerText = (TextView) convertView.findViewById(R.id.totalPowerText);
            viewHolder.lastIntervalPowerText = (TextView) convertView.findViewById(
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
