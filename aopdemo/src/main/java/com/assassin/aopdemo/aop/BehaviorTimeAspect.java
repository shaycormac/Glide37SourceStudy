package com.assassin.aopdemo.aop;


import android.util.Log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Author: Shay-Patrick-Cormac
 * Email: fang47881@126.com
 * Ltd: 金螳螂企业（集团）有限公司
 * Date: 2018/5/14 0014 09:46
 * Version: 1.0
 * Description: aop处理
 * 1.获取标记点（获取我们注解的方法）
   2.处理注解

 详细实现：

 创建类加上@Aspect注解,创建切面类
 此次类所有处理代码如下
 * 
 */
@Aspect
public class BehaviorTimeAspect 
{
    SimpleDateFormat format =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    
    private static  final String TAG="AOPDemo";

    //比作切蛋糕，如何切蛋糕
    //第一步获切点，即获得想要处理方法：* *代表所有方法，（..）代表所有参数，这里可以根据具体的方法类型来做处理
    
    //这个方法用来处理，到根据注解类找到相应的方法：获得想要处理方法： * *代表所有方法，（..）代表所有参数，
    // 这里可以根据具体的方法类型来做处理
    @Pointcut("execution(@com.assassin.aopdemo.aop.BehaviorTimeCount * *(..))")
    public void insertBehavior() {
        
    }

    //对于想好切的蛋糕，如何吃
    //第二步处理获取的切点
    //关联上面的方法,括号不要忘记写了。
    @Around("insertBehavior()")
    public Object dealPoint(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        //获取方法的签名
        //固定写法，用于获取标记点
        MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
        //获取标记的注解方法
        //获取标记的方法
        BehaviorTimeCount annotation = signature.getMethod().getAnnotation(BehaviorTimeCount.class);
        //获取标记方法名,即我们注解传的参数
        //下面这些代码就是你想要的处理逻辑，可以随便添加，埋点啥的。。。。
        String value =annotation.value();
        Log.i(TAG,value+"开始使用的时间：   "+format.format(new Date()));
        long beagin=System.currentTimeMillis();
        Object proceed=null;

        //执行了方法
        //执行我们注解的方法
        proceed = proceedingJoinPoint.proceed();
        Log.i(TAG,"消耗时间：  "+(System.currentTimeMillis()-beagin)+"ms");
        return proceed;
    }
    
    
}
