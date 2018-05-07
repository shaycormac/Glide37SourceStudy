package com.bumptech.glide.load.resource.transcode;

import android.graphics.Bitmap;

import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapper;

/**
 * An {@link com.bumptech.glide.load.resource.transcode.ResourceTranscoder} that can transcode either an
 * {@link Bitmap} or an {@link com.bumptech.glide.load.resource.gif.GifDrawable} into an
 * {@link android.graphics.drawable.Drawable}.
 */
public class GifBitmapWrapperDrawableTranscoder implements ResourceTranscoder<GifBitmapWrapper, GlideDrawable> {
    private final ResourceTranscoder<Bitmap, GlideBitmapDrawable> bitmapDrawableResourceTranscoder;

    public GifBitmapWrapperDrawableTranscoder(
            ResourceTranscoder<Bitmap, GlideBitmapDrawable> bitmapDrawableResourceTranscoder) {
        this.bitmapDrawableResourceTranscoder = bitmapDrawableResourceTranscoder;
    }

    //执行解析
    //GifBitmapWrapperDrawableTranscoder的核心作用就是用来转码的。因为GifBitmapWrapper是无法直接显示到ImageView上面的，
    // 只有Bitmap或者Drawable才能显示到ImageView上。因此，这里的transcode()方法先从Resource<GifBitmapWrapper>中
    // 取出GifBitmapWrapper对象，然后再从GifBitmapWrapper中取出Resource<Bitmap>对象
    @SuppressWarnings("unchecked")
    @Override
    public Resource<GlideDrawable> transcode(Resource<GifBitmapWrapper> toTranscode) {
        GifBitmapWrapper gifBitmap = toTranscode.get();
        Resource<Bitmap> bitmapResource = gifBitmap.getBitmapResource();

        final Resource<? extends GlideDrawable> result;
        //接下来做了一个判断，如果Resource<Bitmap>为空，那么说明此时加载的是GIF图，直接调用getGifResource()方法将图片取出即可
        // ，因为Glide用于加载GIF图片是使用的GifDrawable这个类，它本身就是一个Drawable对象了。而如果Resource<Bitmap>不为空，
        // 那么就需要再做一次转码，将Bitmap转换成Drawable对象才行，因为要保证静图和动图的类型一致性，不然逻辑上是不好处理的
        if (bitmapResource != null) {
            //取出来的是净土  GlideBitmapDrawableTranscoder对象的transcode()方法
            result = bitmapDrawableResourceTranscoder.transcode(bitmapResource);
        } else {
            //取出来的是动图
            result = gifBitmap.getGifResource();
        }
        // This is unchecked but always safe, anything that extends a Drawable can be safely cast to a Drawable.
        return (Resource<GlideDrawable>) result;
    }

    @Override
    public String getId() {
        return "GifBitmapWrapperDrawableTranscoder.com.bumptech.glide.load.resource.transcode";
    }
}
