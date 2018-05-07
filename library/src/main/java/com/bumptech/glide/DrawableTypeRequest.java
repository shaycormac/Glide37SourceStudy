package com.bumptech.glide;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import com.bumptech.glide.load.model.ImageVideoModelLoader;
import com.bumptech.glide.load.model.ImageVideoWrapper;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapper;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.manager.Lifecycle;
import com.bumptech.glide.manager.RequestTracker;
import com.bumptech.glide.provider.DataLoadProvider;
import com.bumptech.glide.provider.FixedLoadProvider;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.io.InputStream;

/**
 * A class for creating a load request that loads either an animated GIF drawable or a Bitmap drawable directly, or
 * adds an {@link com.bumptech.glide.load.resource.transcode.ResourceTranscoder} to transcode the data into a
 * resource type other than a {@link android.graphics.drawable.Drawable}.
 *
 * @param <ModelType> The type of model to use to load the {@link android.graphics.drawable.BitmapDrawable} or
 * {@link com.bumptech.glide.load.resource.gif.GifDrawable}.
 *                   这个类是干啥的呢？？可以看到，最主要的就是它提供了asBitmap()和asGif()这两个方法。
 *                   这两个方法我们在上一篇文章当中都是学过的，分别是用于强制指定加载静态图片和动态图片。
 *                   而从源码中可以看出，它们分别又创建了一个BitmapTypeRequest和GifTypeRequest，
 *                   如果没有进行强制指定的话，那默认就是使用DrawableTypeRequest。
 */
public class DrawableTypeRequest<ModelType> extends DrawableRequestBuilder<ModelType> implements DownloadOptions {
    private final ModelLoader<ModelType, InputStream> streamModelLoader;
    private final ModelLoader<ModelType, ParcelFileDescriptor> fileDescriptorModelLoader;
    private final RequestManager.OptionsApplier optionsApplier;

    private static <A, Z, R> FixedLoadProvider<A, ImageVideoWrapper, Z, R> buildProvider(Glide glide,
            ModelLoader<A, InputStream> streamModelLoader,
            ModelLoader<A, ParcelFileDescriptor> fileDescriptorModelLoader, Class<Z> resourceClass,
            Class<R> transcodedClass,
            ResourceTranscoder<Z, R> transcoder) {
        if (streamModelLoader == null && fileDescriptorModelLoader == null) {
            return null;
        }
        //构造方法中最后一个参数是null
        if (transcoder == null) {
            //glide.buildTranscoder()方法来构建一个ResourceTranscoder，它是用于对图片进行转码的
            //由于ResourceTranscoder是一个接口，这里实际会构建出一个GifBitmapWrapperDrawableTranscoder对象。
            transcoder = glide.buildTranscoder(resourceClass, transcodedClass);
        }
        //调用了glide.buildDataProvider()方法来构建一个DataLoadProvider，它是用于对图片进行编解码的，
        // 由于DataLoadProvider是一个接口，这里实际会构建出一个ImageVideoGifDrawableLoadProvider对象。
        DataLoadProvider<ImageVideoWrapper, Z> dataLoadProvider = glide.buildDataProvider(ImageVideoWrapper.class,
                resourceClass);
        //new了一个ImageVideoModelLoader的实例，并把之前loadGeneric()方法中构建的两个ModelLoader封装到了
        // ImageVideoModelLoader当中
        ImageVideoModelLoader<A> modelLoader = new ImageVideoModelLoader<A>(streamModelLoader,
                fileDescriptorModelLoader);
      //  new出一个FixedLoadProvider，并把刚才构建的出来的GifBitmapWrapperDrawableTranscoder、
        // ImageVideoModelLoader、ImageVideoGifDrawableLoadProvider都封装进去，这个也就是GenericRequest的onSizeReady()方法中的loadProvider了。
        return new FixedLoadProvider<A, ImageVideoWrapper, Z, R>(modelLoader, transcoder, dataLoadProvider);
    }

    DrawableTypeRequest(Class<ModelType> modelClass, ModelLoader<ModelType, InputStream> streamModelLoader,
            ModelLoader<ModelType, ParcelFileDescriptor> fileDescriptorModelLoader, Context context, Glide glide,
            RequestTracker requestTracker, Lifecycle lifecycle, RequestManager.OptionsApplier optionsApplier) 
    {
        //构造方法中构建了GenericRequest的 loadProvider 对象
        super(context, modelClass,
                buildProvider(glide, streamModelLoader, fileDescriptorModelLoader, GifBitmapWrapper.class,
                        GlideDrawable.class, null),
                glide, requestTracker, lifecycle);
        this.streamModelLoader = streamModelLoader;
        this.fileDescriptorModelLoader = fileDescriptorModelLoader;
        this.optionsApplier = optionsApplier;
    }

    /**
     * Attempts to always load the resource as a {@link android.graphics.Bitmap}, even if it could actually be animated.
     *
     * @return A new request builder for loading a {@link android.graphics.Bitmap}
     */
    public BitmapTypeRequest<ModelType> asBitmap() {
        return optionsApplier.apply(new BitmapTypeRequest<ModelType>(this, streamModelLoader,
                fileDescriptorModelLoader, optionsApplier));
    }

    /**
     * Attempts to always load the resource as a {@link com.bumptech.glide.load.resource.gif.GifDrawable}.
     * <p>
     *     If the underlying data is not a GIF, this will fail. As a result, this should only be used if the model
     *     represents an animated GIF and the caller wants to interact with the GIfDrawable directly. Normally using
     *     just an {@link com.bumptech.glide.DrawableTypeRequest} is sufficient because it will determine whether or
     *     not the given data represents an animated GIF and return the appropriate animated or not animated
     *     {@link android.graphics.drawable.Drawable} automatically.
     * </p>
     *
     * @return A new request builder for loading a {@link com.bumptech.glide.load.resource.gif.GifDrawable}.
     */
    public GifTypeRequest<ModelType> asGif() {
        return optionsApplier.apply(new GifTypeRequest<ModelType>(this, streamModelLoader, optionsApplier));
    }

    /**
     * {@inheritDoc}
     */
    public <Y extends Target<File>> Y downloadOnly(Y target) {
        return getDownloadOnlyRequest().downloadOnly(target);
    }

    /**
     * {@inheritDoc}
     */
    public FutureTarget<File> downloadOnly(int width, int height) {
        return getDownloadOnlyRequest().downloadOnly(width, height);
    }

    private GenericTranscodeRequest<ModelType, InputStream, File> getDownloadOnlyRequest() {
        return optionsApplier.apply(new GenericTranscodeRequest<ModelType, InputStream, File>(File.class, this,
                streamModelLoader, InputStream.class, File.class, optionsApplier));
    }
}
