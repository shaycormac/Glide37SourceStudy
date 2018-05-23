package com.assassin.okhttpsourcestudy;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getDataSync();
    }
    
    
    //okhttp的同步请求
    public void getDataSync()
    {
        new Thread(new Runnable() {
            @Override
            public void run() 
            {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("http://www.baidu.com")
                        .build();
                Response response;
                try {
                    response = client.newCall(request).execute();
                    if (response!=null&&response.isSuccessful()) 
                    {
                        Log.d("kwwl", "response.code()==" + response.code());
                        Log.d("kwwl", "response.message()==" + response.message());
                        Log.d("kwwl", "res==" + response.body().string());
                        //此时的代码执行在子线程，修改UI的操作请使用handler跳转到UI线程。

                        /**
                         * 05-23 07:22:14.063 7347-7360/? D/kwwl: response.code()==200
                         05-23 07:22:14.063 7347-7360/? D/kwwl: response.message()==OK
                         05-23 07:22:14.063 7347-7360/? D/kwwl: res==<!DOCTYPE html>
                         <!--STATUS OK--><html> <head><meta http-equiv=content-type content=text/html;charset=utf-8>
                         <meta http-equiv=X-UA-Compatible content=IE=Edge><meta content=always name=referrer>
                         <link rel=stylesheet type=text/css href=http://s1.bdstatic.com/r/www/cache/bdorz/baidu.min.css>
                         <title>百度一下，你就知道</title></head> <body link=#0000cc> 
                         <div id=wrapper> <div id=head> <div class=head_wrapper> <div class=s_form>
                         <div class=s_form_wrapper> <div id=lg> <img hidefocus=true src=//www.baidu.com/img/bd_logo1.png width=270 height=129>...
                         */
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    //异步线程的post请求
    private void postDataWithParams()
    {
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder builder = new FormBody.Builder();
        builder.add("userName", "张三");
        Request request = new Request.Builder()//创建Request 对象。
                .url("http://www.baidu.com")
                .post(builder.build())//传递请求体
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });

        
    }
}
