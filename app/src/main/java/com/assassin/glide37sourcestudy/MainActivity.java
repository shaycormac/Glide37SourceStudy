package com.assassin.glide37sourcestudy;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.assassin.glide37sourcestudy.transform.CircleTransform;
import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView imageView = (ImageView) findViewById(R.id.imgGlide1);
        //dontTransform()表示不进行图片变换，那么当ImageView选择的大小为自适应的时候，加载的图片就会原始分辨率进行填充，否则的话，会充满屏幕
       // Glide.with(this).load("").error(R.mipmap.ic_launcher).dontTransform().fallback(R.mipmap.ic_launcher_round).into(imageView);
        String url = "http://cn.bing.com/az/hprichbg/rb/AvalancheCreek_ROW11173354624_1920x1080.jpg";
        Glide.with(this)
                .load(url)
                .transform(new CircleTransform(this))
                .into(imageView);
    }
}
