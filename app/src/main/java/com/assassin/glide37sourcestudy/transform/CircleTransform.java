package com.assassin.glide37sourcestudy.transform;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

/**
 * Author: Shay-Patrick-Cormac
 * Email: fang47881@126.com
 * Ltd: 金螳螂企业（集团）有限公司
 * Date: 2018/5/8 14:28
 * Version: 1.0
 * Description: Glide 自定义图片转换格式 ，圆形图片
 */

public class CircleTransform  extends BitmapTransformation{
    public CircleTransform(Context context) {
        super(context);
    }

    public CircleTransform(BitmapPool bitmapPool) {
        super(bitmapPool);
    }

    //自定义圆形的id
    //getId()方法中要求返回一个唯一的字符串来作为id，以和其他的图片变换做区分。通常情况下，我们直接返回当前类的完整类名就可以了
    @Override
    public String getId() {
        return "CircleTransform.com.assassin.glide37sourcestudy";
    }

    //对静态图片的圆形处理，gif图？不存在的，暂时不考虑gif图
    //这四个参数不明确的，可以参照centerCrop
    @Override
    protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) 
    {
        //由于需要处理的是圆形图片，因此直径取长宽最短者
        int diameter = Math.min(toTransform.getWidth(), toTransform.getHeight());
        //从缓存池中取出，和centerCrop的套路类似，娶不到，创建一个，并放到缓存池中
        Bitmap toReuse = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        //最后的结果
        Bitmap result;
        if (toReuse!=null)
        {
            result = toReuse;
        }else 
        {
            //没有的话，就自己创建一个图算了，要不要放到缓存池中呢？
            result = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888);
        }
        
        //画布的偏移值，并且根据刚才得到的直径算出半径来进行画圆
        //偏移量
        int dx = (toTransform.getWidth() - diameter) / 2;
        int dy = (toTransform.getHeight() - diameter) / 2;

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        //shade?忘记了
        BitmapShader shader = new BitmapShader(toTransform, BitmapShader.TileMode.CLAMP,
                BitmapShader.TileMode.CLAMP);
        //shade偏移,从而能让画布在正中间画图？？
        //保证画圆的中心点在图片的正中心
        if (dx!=0 || dy!=0)
        {
            Matrix matrix = new Matrix();
            matrix.setTranslate(-dx, -dy);
            shader.setLocalMatrix(matrix);
        }
        
        //设置画笔
        paint.setShader(shader);
        paint.setAntiAlias(true);
        
        //设置圆的半径
        float radius = diameter / 2;
        //画圆
        canvas.drawCircle(radius,radius, radius,paint);
        
        //回收图片
        if (toReuse != null && !pool.put(toReuse)) {
            toReuse.recycle();
        }
        return result;
    }
}
