package com.goldmantis.shaystudy.message_queue;

import android.os.Handler;
import android.os.Looper;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Author: Shay-Patrick-Cormac
 * Email: fang47881@126.com
 * Ltd: 金螳螂企业（集团）有限公司
 * Date: 2018/5/9 16:20
 * Version: 1.0
 * Description: 一个简单的消息队列，实际上是Volley框架请求源码的简化版
 */

public class VolleyCopyCat 
{
    //消息队列
    //一个链表而已
    Queue<SimpleRequest> mSimpleRequestQueue = new LinkedList<>();
    
    /**
     *就这样，一个简化版的网络请求的轮子就完成了，是不是很简单，虽然我们没有考虑同步，缓存等问题，
     * 但其实看过Volley源码的朋友也应该清楚，Volley的核心就是这样的队列，只不过不是一个队列，
     * 而是两种队列(一个队列真正的进行网络请求，一个是尝试从缓存中找对应request的返回内容)

     作者：qing的世界
     链接：https://www.jianshu.com/p/9f3b96937253
     來源：简书
     著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
     */
    public void excuteTask()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //启动一个新县城，用一个True的while循环不停地从队列里面获取第一个request并且处理
                //todo  代码的核心也就是用while循环不停的弹出请求，再处理而已。
                while(true)
                {
                    if (!mSimpleRequestQueue.isEmpty())
                    {
                        //直接从链表中拉出来这个请求
                        SimpleRequest request = mSimpleRequestQueue.poll();
                        String response = ""; // 处理request 的 url，这一步将是耗时的操作，省略细节
                        //回到主线程
                        new Handler(Looper.getMainLooper()).post(request.callback);
                    }
                    
                }
            }
        }).start();
        
    }
    
    //在一个新的类中使用这个傻瓜版轮子添加请求,简易的就是这么简单粗暴。
    public void addRequest(String url ,Runnable callback)
    {
        mSimpleRequestQueue.add(new SimpleRequest(url, callback));
    }
    
    
    /**
     简化版本的请求类，包含请求的Url和一个Runnable 回调
     **/
    class SimpleRequest
    {
        public String url;
        public Runnable callback;

        public SimpleRequest(String url, Runnable callback) {
            this.url = url;
            this.callback = callback;
        }
    }
}
