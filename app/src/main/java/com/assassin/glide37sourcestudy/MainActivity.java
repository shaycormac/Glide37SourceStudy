package com.assassin.glide37sourcestudy;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView imageView = (ImageView) findViewById(R.id.imgGlide1);
        Glide.with(this).load("").error(R.mipmap.ic_launcher).fallback(R.mipmap.ic_launcher_round).into(imageView);
    }
}
