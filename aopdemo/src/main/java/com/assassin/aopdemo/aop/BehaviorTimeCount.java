package com.assassin.aopdemo.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: Shay-Patrick-Cormac
 * Email: fang47881@126.com
 * Ltd: 金螳螂企业（集团）有限公司
 * Date: 2018/5/14 0014 09:38
 * Version: 1.0
 * Description: 面向切面编程的一个注解类
 * 该注解类主要用于测试一个方法执行的时间。
 *
 */
//todo 1.创建注解类 
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface BehaviorTimeCount 
{
    String value();
}
