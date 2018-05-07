package com.bumptech.glide.load.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.request.ResourceCallback;
import com.bumptech.glide.util.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A class that manages a load by adding and removing callbacks for for the load and notifying callbacks when the
 * load completes.
 */
class EngineJob implements EngineRunnable.EngineRunnableManager {
    private static final EngineResourceFactory DEFAULT_FACTORY = new EngineResourceFactory();
    private static final Handler MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper(), new MainThreadCallback());

    private static final int MSG_COMPLETE = 1;
    private static final int MSG_EXCEPTION = 2;

    private final List<ResourceCallback> cbs = new ArrayList<ResourceCallback>();
    private final EngineResourceFactory engineResourceFactory;
    private final EngineJobListener listener;
    private final Key key;
    private final ExecutorService diskCacheService;
    private final ExecutorService sourceService;
    private final boolean isCacheable;

    private boolean isCancelled;
    // Either resource or exception (particularly exception) may be returned to us null, so use booleans to track if
    // we've received them instead of relying on them to be non-null. See issue #180.
    private Resource<?> resource;
    private boolean hasResource;
    private Exception exception;
    private boolean hasException;
    // A set of callbacks that are removed while we're notifying other callbacks of a change in status.
    private Set<ResourceCallback> ignoredCallbacks;
    private EngineRunnable engineRunnable;
    private EngineResource<?> engineResource;

    private volatile Future<?> future;

    public EngineJob(Key key, ExecutorService diskCacheService, ExecutorService sourceService, boolean isCacheable,
            EngineJobListener listener) {
        this(key, diskCacheService, sourceService, isCacheable, listener, DEFAULT_FACTORY);
    }

    public EngineJob(Key key, ExecutorService diskCacheService, ExecutorService sourceService, boolean isCacheable,
            EngineJobListener listener, EngineResourceFactory engineResourceFactory) {
        this.key = key;
        this.diskCacheService = diskCacheService;
        this.sourceService = sourceService;
        this.isCacheable = isCacheable;
        this.listener = listener;
        this.engineResourceFactory = engineResourceFactory;
    }

    public void start(EngineRunnable engineRunnable) {
        this.engineRunnable = engineRunnable;
        future = diskCacheService.submit(engineRunnable);
    }

    @Override
    public void submitForSource(EngineRunnable runnable) {
        future = sourceService.submit(runnable);
    }

    public void addCallback(ResourceCallback cb) {
        Util.assertMainThread();
        if (hasResource) {
            cb.onResourceReady(engineResource);
        } else if (hasException) {
            cb.onException(exception);
        } else {
            cbs.add(cb);
        }
    }

    public void removeCallback(ResourceCallback cb) {
        Util.assertMainThread();
        if (hasResource || hasException) {
            addIgnoredCallback(cb);
        } else {
            cbs.remove(cb);
            if (cbs.isEmpty()) {
                cancel();
            }
        }
    }

    // We cannot remove callbacks while notifying our list of callbacks directly because doing so would cause a
    // ConcurrentModificationException. However, we need to obey the cancellation request such that if notifying a
    // callback early in the callbacks list cancels a callback later in the request list, the cancellation for the later
    // request is still obeyed. Using a set of ignored callbacks allows us to avoid the exception while still meeting
    // the requirement.
    private void addIgnoredCallback(ResourceCallback cb) {
        if (ignoredCallbacks == null) {
            ignoredCallbacks = new HashSet<ResourceCallback>();
        }
        ignoredCallbacks.add(cb);
    }

    private boolean isInIgnoredCallbacks(ResourceCallback cb) {
        return ignoredCallbacks != null && ignoredCallbacks.contains(cb);
    }

    // Exposed for testing.
    void cancel() {
        if (hasException || hasResource || isCancelled) {
            return;
        }
        engineRunnable.cancel();
        Future currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
        isCancelled = true;
        listener.onEngineJobCancelled(this, key);
    }

    // Exposed for testing.
    boolean isCancelled() {
        return isCancelled;
    }

    //调用这个方法，发送一个Handler,通知主线程刷新Ui
    @Override
    public void onResourceReady(final Resource<?> resource) {
        this.resource = resource;
        MAIN_THREAD_HANDLER.obtainMessage(MSG_COMPLETE, this).sendToTarget();
    }

    //图片应该是先缓存后，然后在显示在控件上的。看下面代码即可得知。
    private void handleResultOnMainThread() {
        if (isCancelled) {
            resource.recycle();
            return;
        } else if (cbs.isEmpty()) {
            throw new IllegalStateException("Received a resource without any callbacks to notify");
        }
        engineResource = engineResourceFactory.build(resource, isCacheable);
        hasResource = true;

        // Hold on to resource for duration of request so we don't recycle it in the middle of notifying if it
        // synchronously released by one of the callbacks.
        //EngineResource是用一个acquired变量用来记录图片被引用的次数，调用acquire()方法会让变量加1，
        // 调用release()方法会让变量减1
        engineResource.acquire();
        //图片缓存的地方,这个Listener是Engine,实现方法是存到弱引用的hashMap中
        listener.onEngineJobComplete(key, engineResource);
//后在第72行调用了handleResultOnMainThread()方法，这个方法中又通过一个循环，调用了所有ResourceCallback的
// onResourceReady()方法。
// 那么这个ResourceCallback是什么呢？答案在addCallback()方法当中，它会向cbs集合中去添加ResourceCallback。
// 那么这个addCallback()方法又是哪里调用的呢？其实调用的地方我们早就已经看过了，只不过之前没有注意，
// 现在重新来看一下Engine的load()方法
        for (ResourceCallback cb : cbs) {
            if (!isInIgnoredCallbacks(cb)) {
                engineResource.acquire();
                //回调，最终辗转反侧显示图片
                cb.onResourceReady(engineResource);
            }
        }
        // Our request is complete, so we can release the resource.
        engineResource.release();
    }

    @Override
    public void onException(final Exception e) {
        this.exception = e;
        MAIN_THREAD_HANDLER.obtainMessage(MSG_EXCEPTION, this).sendToTarget();
    }

    private void handleExceptionOnMainThread() {
        if (isCancelled) {
            return;
        } else if (cbs.isEmpty()) {
            throw new IllegalStateException("Received an exception without any callbacks to notify");
        }
        hasException = true;

        listener.onEngineJobComplete(key, null);

        for (ResourceCallback cb : cbs) {
            if (!isInIgnoredCallbacks(cb)) {
                cb.onException(exception);
            }
        }
    }

    // Visible for testing.
    static class EngineResourceFactory {
        public <R> EngineResource<R> build(Resource<R> resource, boolean isMemoryCacheable) {
            return new EngineResource<R>(resource, isMemoryCacheable);
        }
    }

    private static class MainThreadCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message message) {
            if (MSG_COMPLETE == message.what || MSG_EXCEPTION == message.what) {
                EngineJob job = (EngineJob) message.obj;
                if (MSG_COMPLETE == message.what) {
                    //调用这个方法
                    job.handleResultOnMainThread();
                } else {
                    job.handleExceptionOnMainThread();
                }
                return true;
            }

            return false;
        }
    }
}
