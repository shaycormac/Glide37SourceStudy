package com.bumptech.glide.load.engine;

import android.util.Log;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.executor.Prioritized;
import com.bumptech.glide.request.ResourceCallback;

/**
 * A runnable class responsible for using an {@link com.bumptech.glide.load.engine.DecodeJob} to decode resources on a
 * background thread in two stages.
 *
 * <p>
 *     In the first stage, this class attempts to decode a resource
 *     from cache, first using transformed data and then using source data. If no resource can be decoded from cache,
 *     this class then requests to be posted again. During the second stage this class then attempts to use the
 *     {@link com.bumptech.glide.load.engine.DecodeJob} to decode data directly from the original source.
 * </p>
 *
 * <p>
 *     Using two stages with a re-post in between allows us to run fast disk cache decodes on one thread and slow source
 *     fetches on a second pool so that loads for local data are never blocked waiting for loads for remote data to
 *     complete.
 * </p>
 */
class EngineRunnable implements Runnable, Prioritized {
    private static final String TAG = "EngineRunnable";

    private final Priority priority;
    private final EngineRunnableManager manager;
    private final DecodeJob<?, ?, ?> decodeJob;

    private Stage stage;

    private volatile boolean isCancelled;

    public EngineRunnable(EngineRunnableManager manager, DecodeJob<?, ?, ?> decodeJob, Priority priority) {
        this.manager = manager;
        this.decodeJob = decodeJob;
        this.stage = Stage.CACHE;
        this.priority = priority;
    }

    public void cancel() {
        isCancelled = true;
        decodeJob.cancel();
    }

    @Override
    public void run() {
        if (isCancelled) {
            return;
        }

        Exception exception = null;
        Resource<?> resource = null;
        try {
            //看上去所有的逻辑都应该在这里执行了
            //我们最终得到了这个Resource<GlideDrawable>对象，那么接下来就是如何将它显示出来了
            resource = decode();
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Exception decoding", e);
            }
            exception = e;
        }

        if (isCancelled) {
            if (resource != null) {
                resource.recycle();
            }
            return;
        }

        if (resource == null) {
            onLoadFailed(exception);
        } else {
            //表示图片加载已经完成了
            onLoadComplete(resource);
        }
    }

    private boolean isDecodingFromCache() {
        return stage == Stage.CACHE;
    }

    private void onLoadComplete(Resource resource) {
        //这个manager就是EngineJob对象
        manager.onResourceReady(resource);
    }

    private void onLoadFailed(Exception e) {
        if (isDecodingFromCache()) {
            stage = Stage.SOURCE;
            manager.submitForSource(this);
        } else {
            manager.onException(e);
        }
    }

    private Resource<?> decode() throws Exception {
        //分两种情况，从硬盘缓存图片还是从原始图片解码
        if (isDecodingFromCache()) {
            //从硬盘中找，又分为解码过的和原始图片
            return decodeFromCache();
        } else {
            //从网络中找图片
            return decodeFromSource();
        }
    }

    //从硬盘缓存中读取
    private Resource<?> decodeFromCache() throws Exception {
        Resource<?> result = null;
        try {
            //这两个方法的区别其实就是DiskCacheStrategy.RESULT和DiskCacheStrategy.SOURCE这两个参数的区别
            //尝试从硬盘中的压缩过的图片中读取
            //它们都是调用了loadFromCache()方法从缓存当中读取数据，如果是decodeResultFromCache()方法就直接将数据解码并返回，
            // 如果是decodeSourceFromCache()方法，还要调用一下transformEncodeAndTranscode()方法先将数据转换一下再解码并返回
            result = decodeJob.decodeResultFromCache();
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Exception decoding result from cache: " + e);
            }
        }

        if (result == null) {
            //缓存的原始图片读取
            result = decodeJob.decodeSourceFromCache();
        }
        return result;
    }

    private Resource<?> decodeFromSource() throws Exception {
        return decodeJob.decodeFromSource();
    }

    @Override
    public int getPriority() {
        return priority.ordinal();
    }

    private enum Stage {
        /** Attempting to decode resource from cache. */
        CACHE,
        /** Attempting to decode resource from source data. */
        SOURCE
    }

    interface EngineRunnableManager extends ResourceCallback {
        void submitForSource(EngineRunnable runnable);
    }
}
