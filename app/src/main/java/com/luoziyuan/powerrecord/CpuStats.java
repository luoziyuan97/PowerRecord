package com.luoziyuan.powerrecord;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.os.BatteryStatsImpl;
import com.android.internal.os.PowerProfile;
import com.android.internal.os.ProcessCpuTracker;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by John on 2019/1/30.
 */

//存放CPU相关信息
public class CpuStats {

    private static final String TAG = "CpuStats";

    private PowerProfile powerProfile;

    private int numOfCpus;
    private int numOfCpuClusters;
    private int[] numOfCoresInCluster;          //各个cluster的核数
    private int[] numOfFrequenciesInCluster;    //各个cluster的可运行频率个数
    private double[][] cpuFrequencies;          //CPU的工作各个工作频率
    private double[][] cpuCurrents;             //CPU在各个工作频率下的电流
    private double[] currentFrequencies;        //各个CPU当前频率

    private int PROC_SPACE_TERM = (int)' ';
    private int PROC_LINE_TERM = (int)'\n';
    private int PROC_OUT_STRING = 0x1000;
    private int PROC_OUT_LONG = 0x2000;

    private int READ_LONG_FORMAT = PROC_SPACE_TERM|PROC_OUT_LONG;
    private int READ_STRING_FORMAT = PROC_OUT_STRING|PROC_LINE_TERM;

    private long[][] lastCpuTimes = null;           //用于存放proc/stat信息
    private long[][] currentCpuTimes = null;

    private long[] appLastCpuTimes = new long[2];       //用于存放自身应用的userTime和sysTime
    private long[] appCurrentCpuTimes = new long[2];

    private FileOutputStream outputStream;

    private DecimalFormat threeDecimalPlaces = new DecimalFormat("#.000");

    public CpuStats(Context context, FileOutputStream outputStream)
    {
        powerProfile = new PowerProfile(context);
        this.outputStream = outputStream;

        //获取可运行CPU个数
        numOfCpus = Runtime.getRuntime().availableProcessors();

        //获取cluster基本信息
        getClusterInfo();

        //获取各个cluster的可运行频率和电流，单位kHz,mA
        getCpuFrequenciesAndCurrent();

        currentFrequencies = new double[numOfCpus];

        //计算cpu使用率只使用proc/stat记录每行的前7个值，分别是
        //user，nice, system, idle, iowait, irq, softirq
        lastCpuTimes = new long[numOfCpus + 1][7];
        currentCpuTimes = new long[numOfCpus + 1][7];
    }

    //获取cluster个数以及各个cluster的核数
    private void getClusterInfo()
    {
        double temp = powerProfile.getAveragePower("cpu.clusters.cores", 0);

        //未读出数据，说明xml中不含cpu.clusters.cores项，默认一个cluster
        if (temp == 0)
        {
            numOfCpuClusters = 1;
            numOfCoresInCluster = new int[1];
            numOfCoresInCluster[0] = numOfCpus;
        }

        //这里打了个赌，即xml中包含cpu.clusters.cores项的手机一定有getNumCpuClusters()
        //和getNumCoresInCpuCluster()方法
        else
        {
            numOfCpuClusters = powerProfile.getNumCpuClusters();
            numOfCoresInCluster = new int[numOfCpuClusters];
            for (int i = 0; i < numOfCpuClusters; i++)
                numOfCoresInCluster[i] = powerProfile.getNumCoresInCpuCluster(i);
        }

    }

    //获取各cluster可运行频率和电流
    private void getCpuFrequenciesAndCurrent()
    {
        numOfFrequenciesInCluster = new int[numOfCpuClusters];
        cpuFrequencies = new double[numOfCpuClusters][];
        cpuCurrents = new double[numOfCpuClusters][];

        LinkedList<Double> frequencyList = new LinkedList<>();
        LinkedList<Double> currentList = new LinkedList<>();

        double lastFrequency = 0, tempFrequency = 0, tempCurrent = 0;

        if (numOfCpuClusters == 1)
        {
            //试探出cluster的频率个数
            for (int i = 0; true; i++)
            {
                //依次读取cpu的各个频率和电流
                Log.d(TAG, "reading cpu.speeds and cpu.active");
                tempFrequency = powerProfile.getAveragePower("cpu.speeds", i);
                tempCurrent = powerProfile.getAveragePower("cpu.active", i);

                //如果没读出,使用如下项目名再次尝试
                if (tempFrequency == 0)
                {
                    Log.d(TAG, "reading cpu.speeds.cluster0 and cpu.active.cluster0");
                    tempFrequency = powerProfile.getAveragePower("cpu.speeds.cluster0", i);
                    tempCurrent = powerProfile.getAveragePower("cpu.active.cluster0", i);
                }

                //使用列表缓存，避免再读一遍文件
                frequencyList.add(tempFrequency);
                currentList.add(tempCurrent);

                //前后两次读取的值相同，说明已经将所有值读出
                if (tempFrequency == lastFrequency)
                {
                    numOfFrequenciesInCluster[0] = i;
                    break;
                }
                lastFrequency = tempFrequency;
            }

            cpuFrequencies[0] = new double[numOfFrequenciesInCluster[0]];
            cpuCurrents[0] = new double[numOfFrequenciesInCluster[0]];

            //从列表取出给数组赋值
            for (int i = 0; i < numOfFrequenciesInCluster[0]; i++)
            {
                cpuFrequencies[0][i] = frequencyList.get(i);
                cpuCurrents[0][i] = currentList.get(i);
                Log.d(TAG, cpuFrequencies[0][i] + " : " + cpuCurrents[0][i]);
            }

            frequencyList.clear();
            currentList.clear();
        }

        else
        {
            //cluster数量多于1，逐个cluster进行读取
            for (int i = 0; i < numOfCpuClusters; i++)
            {
                //试探出cluster的频率个数
                for (int j = 0; true; j++)
                {
                    //依次读取该cluster的各个频率和电流
                    Log.d(TAG, "reading cpu.speeds.cluster" + i +
                            " and cpu.active.cluster" + i);
                    tempFrequency = powerProfile.getAveragePower("cpu.speeds.cluster" + i, j);
                    tempCurrent = powerProfile.getAveragePower("cpu.active.cluster" + i, j);

                    //使用列表缓存，避免再读一遍文件
                    frequencyList.add(tempFrequency);
                    currentList.add(tempCurrent);

                    //前后两次读取的值相同，说明已经将所有值读出
                    if (tempFrequency == lastFrequency)
                    {
                        numOfFrequenciesInCluster[i] = j;
                        break;
                    }
                    lastFrequency = tempFrequency;
                }

                cpuFrequencies[i] = new double[numOfFrequenciesInCluster[i]];
                cpuCurrents[i] = new double[numOfFrequenciesInCluster[i]];

                //从列表取出给数组赋值
                for (int j = 0; j < numOfFrequenciesInCluster[i]; j++)
                {
                    cpuFrequencies[i][j] = frequencyList.get(j);
                    cpuCurrents[i][j] = currentList.get(j);
                    Log.d(TAG, cpuFrequencies[i][j] + " : " + cpuCurrents[i][j]);
                }

                frequencyList.clear();
                currentList.clear();
            }
        }

    }

    //获取当前各个CPU的工作频率
    private void getCurrentFrequencies()
    {
        long[] tempFrequency = new long[1];
        int[] format = new int[] {READ_LONG_FORMAT};
        for (int i = 0; i < numOfCpus; i++)
        {
            Process.readProcFile("/sys/devices/system/cpu/cpu" + i +
                            "/cpufreq/scaling_cur_freq", format,
                    null, tempFrequency, null);
            currentFrequencies[i] = tempFrequency[0];
            Log.d(TAG, "currentFrequency : " + currentFrequencies[i]);
        }
    }

    //获取指定CPU的当前工作电流
    private double getCpuCurrent(int core)
    {
        //检测是否有读取当前频率
        if (currentFrequencies[core] == 0)
        {
            Log.w(TAG, "need to read current frequencies first!");
            return 0;
        }

        int cluster = -1;         //当前核所属cluster
        int coreNum = 0;

        //根据cpuIndex计算所属的cluster
        for (int i = 0; i < numOfCpuClusters; i++)
        {
            coreNum += numOfCoresInCluster[i];
            if (coreNum > core)
            {
                cluster = i;
                break;
            }
        }

        //如果该核的编号比所有cluster的核数多，将该核归到最后一个cluster
        if (cluster == -1)
            cluster = numOfCpuClusters - 1;

        //获取该核当前频率对应的电流
        int index = 0;
        while (index < numOfFrequenciesInCluster[cluster])
        {
            //当前频率匹配上了文件中的频率，直接返回对应的电流值
            if (cpuFrequencies[cluster][index] == currentFrequencies[core])
                return cpuCurrents[cluster][index];

            else if (cpuFrequencies[cluster][index] < currentFrequencies[core])
                index++;

            else if (cpuFrequencies[cluster][index] > currentFrequencies[core])
                break;
        }

        //当前频率未匹配上，进行插值
        Log.d(TAG, "frequency not match");
        double current = 0;

        //当前频率小于cluster最小频率，使用最开始两个频率进行插值
        if (index == 0)
            index++;

            //当前频率大于cluster最大频率，使用最后两个频率进行插值
        else if (index == numOfFrequenciesInCluster[cluster])
            index--;

        //正常插值
        current = (currentFrequencies[core] - cpuFrequencies[cluster][index - 1])
                * (cpuCurrents[cluster][index] - cpuCurrents[cluster][index - 1])
                / (cpuFrequencies[cluster][index] - cpuFrequencies[cluster][index - 1])
                + cpuCurrents[cluster][index - 1];

        return current;
    }

    //获取指定CPU的使用率,参数-1表示获取总cpu使用率
    private double getCpuUseRate(int core)
    {
        //判断是否有已对proc/stat进行了两次采样
        if (lastCpuTimes[0][0] == 0 || currentCpuTimes[0][0] == 0)
        {
            Log.w(TAG, "need to read proc/stat first!");
            return 0;
        }

        //根据两次采样的差值计算cpu利用率
        long totalCpuTimeOld = 0;
        long totalCpuTimeNew = 0;
        long allCpuTimeOld = 0;
        long allCpuTimeNew = 0;

        for (int i = 0; i < 7; i++)
        {
            totalCpuTimeOld += lastCpuTimes[core + 1][i];
            totalCpuTimeNew += currentCpuTimes[core + 1][i];
            allCpuTimeOld += lastCpuTimes[0][i];
            allCpuTimeNew += currentCpuTimes[0][i];
        }

        long idleCpuTimeOld = lastCpuTimes[core + 1][3];
        long idleCpuTimeNew = currentCpuTimes[core + 1][3];

//        Log.d(TAG, "totalCpuTimeNew : " + totalCpuTimeNew);
//        Log.d(TAG, "totalCpuTimeOld : " + totalCpuTimeOld);
        String jiffies = "";
        for (int i = 0; i < 7; i++)
            jiffies += (currentCpuTimes[core + 1][i] - lastCpuTimes[core + 1][i]) + " ";
        Log.d(TAG, "jiffies : " + jiffies);

        double useRate;
        useRate = ((totalCpuTimeNew - totalCpuTimeOld) - (idleCpuTimeNew - idleCpuTimeOld))
                * 1.0 / (allCpuTimeNew - allCpuTimeOld);

        //防止NaN
        if (Double.isNaN(useRate))
        {
            Log.w(TAG, "CpuUseRate NaN!");
            try {
                outputStream.write("CpuUseRate NaN!\n".getBytes());
            } catch (IOException e) {
                Log.d(TAG, "failed to write temp log.");
            }
            useRate = 0;
        }

        //防止负值
        if (useRate < 0)
        {
            Log.w(TAG, "CpuUseRate < 0!");
            try {
                outputStream.write("CpuUseRate < 0!\n".getBytes());
            } catch (IOException e) {
                Log.d(TAG, "failed to write temp log.");
            }
            useRate = 0;
        }

        Log.d(TAG, "cpu" + core + " useRate : " + useRate);

        return useRate;
    }

    //获取总cpu使用率
    public double getTotalCpuUseRate()
    {
        return getCpuUseRate(-1);
    }

    //获取指定cpu的估计耗电量
    private double getCpuPower(int core)
    {
        double cpuCurrent, cpuUseRate, cpuPower;
        String info = "cpu" + core + " ";
        cpuCurrent = getCpuCurrent(core);
        cpuUseRate = getCpuUseRate(core);
        cpuPower = cpuCurrent * cpuUseRate;
        info += cpuCurrent + " "
                + threeDecimalPlaces.format(cpuUseRate) + " "
                + threeDecimalPlaces.format(cpuPower) + "\n";

        Log.d(TAG, info);
        try {
            outputStream.write(info.getBytes());
        } catch (IOException e) {
            Log.d(TAG, "failed to write temp log.");
        }

        return cpuPower;
    }

    //获取所有cpu的估计耗电量
    public double getTotalCpuPower()
    {
        //获取当前各cpu频率
        getCurrentFrequencies();

        //读取cpu使用信息
        readProcStat();

        double totalCpuPower = 0;
        for (int i = 0; i < numOfCpus; i++)
            totalCpuPower += getCpuPower(i);
        Log.d(TAG, "total cpu power : " + totalCpuPower);

        return totalCpuPower;
    }

    //读取proc/[pid]/stat文件获取应用自身cpu时间信息
    public void readSelfProcStat()
    {
        String[] pidStat = new String[1];
        int[] longFormat = new int[]{READ_LONG_FORMAT};
        String[] statValues;

        Process.readProcFile("proc/" + Process.myPid() + "/stat", longFormat,
                pidStat, null, null);
        statValues = pidStat[0].split(" ");
        appCurrentCpuTimes[0] = Long.parseLong(statValues[13]);
        appCurrentCpuTimes[1] = Long.parseLong(statValues[14]);
    }

    //读取指定app的cpu使用信息
    public void readAppProcStat(String packageName)
    {
        try {
            //执行top命令并取结果，获取应用pid
            java.lang.Process process = Runtime.getRuntime().
                    exec("top -n 1 | grep " + packageName);
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String info = null;
            int pid = 0;

            info = reader.readLine();
            pid = Integer.parseInt(info.split(" ")[0]);

            //用上一次读取的值替换上上次读取的值
            appLastCpuTimes[0] = appCurrentCpuTimes[0];
            appLastCpuTimes[1] = appCurrentCpuTimes[1];

            //执行cat命令获取应用cpu使用信息
            process = Runtime.getRuntime().
                    exec("cat proc/" + pid + "/stat");
            inputStream = process.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));

            info = reader.readLine();
            String[] statValues = info.split(" ");
            appCurrentCpuTimes[0] = Long.parseLong(statValues[13]);
            appCurrentCpuTimes[1] = Long.parseLong(statValues[14]);

            //关闭对象
            reader.close();
            inputStream.close();
            process.destroy();

        } catch (IOException e) {
            Log.d(TAG, "error reading proc/stat");
        }
    }

    //执行linux系统的cat命令读取proc/stat文件
    public void readProcStat()
    {
        try {
            //执行cat命令并取结果
            java.lang.Process process = Runtime.getRuntime().
                    exec("cat proc/stat");
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String procStatString = null;
            String[] procStats = null;
            int cpuIndex = 0;

            //使用上一次的proc/stat记录覆盖上上次的
            for (int i = 0; i <= numOfCpus; i++)
                for (int j = 0; j < 7; j++)
                    lastCpuTimes[i][j] = currentCpuTimes[i][j];

            //依次解析本次记录的各行
            while ((procStatString = reader.readLine()) != null)
            {
                Log.d(TAG, "procStat : " + procStatString);

                if (!procStatString.startsWith("cpu"))
                    break;

                //解析首行，以cpu开头，之后有两个空格，然后是具体值
                //split之后第一个数组元素是"cpu"，第二个是空格，第三个是userTime值
                if (cpuIndex == 0)
                {
                    procStats = procStatString.split(" ");
                    for (int i = 2; i < 9; i++)
                        currentCpuTimes[0][i - 2] = Long.parseLong(procStats[i]);
                }

                //解析余下行，以cpuX开头，之后只有一个空格，然后是具体值
                //split之后第一个数组元素是"cpuX"，第二个是userTime值
                else
                {
                    procStats = procStatString.split(" ");
                    for (int i = 1; i < 8; i++)
                        currentCpuTimes[cpuIndex][i - 1] = Long.parseLong(procStats[i]);
                }

                cpuIndex++;
            }

            reader.close();
            inputStream.close();
            process.destroy();

        } catch (IOException e) {
            Log.d(TAG, "error reading proc/stat");
        }
    }
}
