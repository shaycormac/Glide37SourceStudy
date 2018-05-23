package com.assassin.aopdemo.aop;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/**
 * Author: Shay-Patrick-Cormac
 * Email: fang47881@126.com
 * Ltd: 金螳螂企业（集团）有限公司
 * Date: 2018/5/14 0014 09:41
 * Version: 1.0
 * Description: 作为测试方法执行消耗的时间测试类
 */

public class MethodTime
{
    /**
     * 静态变量的含义
     */
    public static   final String TAG = "MethodTime";
    //未使用面向切面的方法
    /**
     * 语音的模块
     *
     * @param view
     */
    public  void shake(View view)
    {
        long beagin=System.currentTimeMillis();

        //摇一摇的代码逻辑

        SystemClock.sleep(2000);
        Log.i(TAG,"摇到一个妹子，距离500公里");


        Log.i(TAG,"消耗时间：  "+(System.currentTimeMillis()-beagin)+"ms");
    }
    
    
    //使用面向切面编程后的方法，一行注解解决。
    @BehaviorTimeCount("摇一摇")
    public  void shake()
    {
        SystemClock.sleep(2000);
        Log.i(TAG,"摇到一个妹子，距离500公里");
    }
    
}
