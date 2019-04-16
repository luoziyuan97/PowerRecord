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
        guidance[0] = "本应用需要获取一个特殊的权限才能使用\n" +
                "下面是获取该权限的方法，一旦成功获取，后续使用时不必再进行以下步骤\n\n" +
                "将手机使用USB连接到电脑，打开手机的USB调试选项\n" +
                "(若手机连接电脑后没有自动提示USB调试，可以进入手机设置的开发者选项手动打开)\n";
        guidance[1] = "在电脑上下载adb工具，下载地址如下\n\n" +
                "http://adbshell.com/downloads\n\n" +
                "点击ADB Kits进行下载，下载完成之后解压即可使用";
        guidance[2] = "打开cmd命令行，切换到adb.exe所在目录\n\n" +
                "输入命令:\n" +
                "adb devices\n\n" +
                "若输出了一段字母数字组合表示的设备，说明手机连接成功且adb工具可以使用";
        guidance[3] = "继续输入命令(以下内容全部输入在一行，中间以空格隔开):\n\n" +
                "adb shell pm grant\n" +
                "com.luoziyuan.powerrecord\n" +
                "android.permission.BATTERY_STATS\n\n" +
                "若无提示信息表明执行成功\n\n" +
                "如果提示Neither user 2000 nor current process has " +
                "android.permission.GRANT_RUNTIME_PERMISSION，" +
                "请打开开发者选项中的\"USB调试(安全设置)\"";
        guidance[4] = "长按手机桌面空白处，出现调整布局界面后，点击窗口小工具，" +
                "找到“耗电记录仪”，将其添加到桌面\n\n" +
                "此举是为了避免本应用在后台运行时被系统挂起，如果长按桌面空白处没反应，" +
                "请查询所使用手机添加窗口小工具的方法\n\n" +
                "如果使用过程中出现应用在后台运行时被系统自动关闭的情况，" +
                "请确定允许了此应用显示通知，并将此应用添加到省电白名单";

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
