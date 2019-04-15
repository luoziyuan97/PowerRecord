package com.luoziyuan.powerrecord.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.luoziyuan.powerrecord.R;

public class GuideActivity extends AppCompatActivity {

    private int step;

    private String[] guidance;

    private TextView stepText;
    private TextView guideText;
    private Button lastStepButton;
    private Button nextStepButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        //上方标题栏添加返回按钮
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        step = 1;
        guidance = new String[5];
        guidance[0] = "本应用需要获取一个特殊的权限才能正常工作\n" +
                "下面是获取该权限的方法，一旦成功获取，后续使用时不必再进行以下步骤\n\n" +
                "将手机使用USB连接到电脑，打开手机的USB调试选项\n" +
                "(若手机连接电脑后没有自动提示USB调试，可以进入手机设置的开发者选项手动打开)\n" +
                "小米手机还需要打开开发者选项中的USB调试(安全设置)";
        guidance[1] = "在电脑上下载adb工具，下载地址如下\n" +
                "http://adbshell.com/downloads\n" +
                "点击ADB Kits进行下载，下载完成之后解压即可使用";
        guidance[2] = "打开cmd命令行，切换到adb.exe所在目录\n" +
                "输入命令:\n" +
                "adb devices\n" +
                "若输出了一段字母数字组合表示的设备，说明手机连接成功且adb工具可以使用";
        guidance[3] = "继续输入命令(以下内容全部输入在一行，中间以空格隔开):\n" +
                "adb shell pm grant\n" +
                "com.luoziyuan.powerrecord\n" +
                "android.permission.BATTERY_STATS\n" +
                "若无提示信息表明执行成功";
        guidance[4] = "长按手机桌面空白处，点击窗口小工具，找到“耗电记录仪”，将其添加到桌面\n" +
                "(此举是为了避免本应用在后台运行时被系统挂起)";

        stepText = findViewById(R.id.stepText_GuideActivity);
        guideText = findViewById(R.id.guideText_GuideActivity);
        guideText.setText(guidance[0]);

        lastStepButton = findViewById(R.id.lastStepButton_GuideActivity);
        lastStepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (step > 1)
                {
                    step--;
                    String stepInfo = "第" + step + "/5步";
                    stepText.setText(stepInfo);
                    guideText.setText(guidance[step - 1]);
                }
                else
                    finish();
            }
        });

        nextStepButton = findViewById(R.id.nextStepButton_GuideActivity);
        nextStepButton.setText("下一步");
        nextStepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (step == 5)
                {
                    if (ActivityCompat.checkSelfPermission(GuideActivity.this,
                            Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED)
                    {
                        Toast.makeText(GuideActivity.this,
                                "操作完成，已获取耗电统计权限！", Toast.LENGTH_LONG).show();
                        finish();
                    }
                    else
                        Toast.makeText(GuideActivity.this,
                                "未检测到耗电统计权限，请确认完成了每一步操作",
                                Toast.LENGTH_SHORT).show();
                }
                else
                {
                    step++;
                    String stepInfo = "第" + step + "/5步";
                    stepText.setText(stepInfo);
                    guideText.setText(guidance[step - 1]);
                    if (step == 4)
                        nextStepButton.setText("完成");
                }
            }
        });
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
}
