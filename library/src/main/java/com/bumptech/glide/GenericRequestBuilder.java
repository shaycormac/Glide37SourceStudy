package com.bumptech.glide;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.animation.Animation;
import android.widget.ImageView;

import com.bumptech.glide.load.Encoder;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.UnitTransformation;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.manager.Lifecycle;
import com.bumptech.glide.manager.RequestTracker;
import com.bumptech.glide.provider.ChildLoadProvider;
import com.bumptech.glide.provider.LoadProvider;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.GenericRequest;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestCoordinator;
import com.bumptech.glide.request.RequestFutureTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.ThumbnailRequestCoordinator;
import com.bumptech.glide.request.animation.GlideAnimationFactory;
import com.bumptech.glide.request.animation.NoAnimation;
import com.bumptech.glide.request.animation.ViewAnimationFactory;
import com.bumptech.glide.request.animation.ViewPropertyAnimation;
import com.bumptech.glide.request.animation.ViewPropertyAnimationFactory;
import com.bumptech.glide.request.target.PreloadTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.EmptySignature;
import com.bumptech.glide.util.Util;

import java.io.File;

/**
 * A generic class that can handle setting options and staring loads for generic resource types.
 *
 * @param <ModelType> The type of model representing the resource.
 * @param <DataType> The data type that the resource {@link com.bumptech.glide.load.model.ModelLoader} will provide that
 *                  can be decoded by the {@link com.bumptech.glide.load.ResourceDecoder}.
 * @param <ResourceType> The type of the resource that will be loaded.
 * @param <TranscodeType> The type of resource the decoded resource will be transcoded to.
 */
public class GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> implements Cloneable {
    protected final Class<ModelType> modelClass;
    protected final Context context;
    protected final Glide glide;
    protected final Class<TranscodeType> transcodeClass;
    protected final RequestTracker requestTracker;
    protected final Lifecycle lifecycle;
    private ChildLoadProvider<ModelType, DataType, ResourceType, TranscodeType> loadProvider;

    private ModelType model;
    private Key signature = EmptySignature.obtain();
    // model may occasionally be null, so to enforce that load() was called, set a boolean rather than relying on model
    // not to be null.
    private boolean isModelSet;
    private int placeholderId;
    private int errorId;
    private RequestListener<? super ModelType, TranscodeType> requestListener;
    private Float thumbSizeMultiplier;
    private GenericRequestBuilder<?, ?, ?, TranscodeType> thumbnailRequestBuilder;
    private Float sizeMultiplier = 1f;
    private Drawable placeholderDrawable;
    private Drawable errorPlaceholder;
    private Priority priority = null;
    private boolean isCacheable = true;
    private GlideAnimationFactory<TranscodeType> animationFactory = NoAnimation.getFactory();
    private int overrideHeight = -1;
    private int overrideWidth = -1;
    private DiskCacheStrategy diskCacheStrategy = DiskCacheStrategy.RESULT;
    private Transformation<ResourceType> transformation = UnitTransformation.get();
    private boolean isTransformationSet;
    private boolean isThumbnailBuilt;
    private Drawable fallbackDrawable;
    private int fallbackResource;

    GenericRequestBuilder(LoadProvider<ModelType, DataType, ResourceType, TranscodeType> loadProvider,
            Class<TranscodeType> transcodeClass, GenericRequestBuilder<ModelType, ?, ?, ?> other) {
        this(other.context, other.modelClass, loadProvider, transcodeClass, other.glide, other.requestTracker,
                other.lifecycle);
        this.model = other.model;
        this.isModelSet = other.isModelSet;
        this.signature = other.signature;
        this.diskCacheStrategy = other.diskCacheStrategy;
        this.isCacheable = other.isCacheable;
    }

    GenericRequestBuilder(Context context, Class<ModelType> modelClass,
            LoadProvider<ModelType, DataType, ResourceType, TranscodeType> loadProvider,
            Class<TranscodeType> transcodeClass, Glide glide, RequestTracker requestTracker, Lifecycle lifecycle) {
        this.context = context;
        this.modelClass = modelClass;
        this.transcodeClass = transcodeClass;
        this.glide = glide;
        this.requestTracker = requestTracker;
        this.lifecycle = lifecycle;
        this.loadProvider = loadProvider != null
                ? new ChildLoadProvider<ModelType, DataType, ResourceType, TranscodeType>(loadProvider) : null;

        if (context == null) {
            throw new NullPointerException("Context can't be null");
        }
        if (modelClass != null && loadProvider == null) {
            throw new NullPointerException("LoadProvider must not be null");
        }
    }

    /**
     * Loads and displays the resource retrieved by the given thumbnail request if it finishes before this request.
     * Best used for loading thumbnail resources that are smaller and will be loaded more quickly than the full size
     * resource. There are no guarantees about the order in which the requests will actually finish. However, if the
     * thumb request completes after the full request, the thumb resource will never replace the full resource.
     *
     * @see #thumbnail(float)
     *
     * <p>
     *     Recursive calls to thumbnail are supported.
     * </p>
     *
     * @param thumbnailRequest The request to use to load the thumbnail.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> thumbnail(
            GenericRequestBuilder<?, ?, ?, TranscodeType> thumbnailRequest) {
        if (this.equals(thumbnailRequest)) {
            throw new IllegalArgumentException("You cannot set a request as a thumbnail for itself. Consider using "
                    + "clone() on the request you are passing to thumbnail()");
        }
        this.thumbnailRequestBuilder = thumbnailRequest;

        return this;
    }

    /**
     * Loads a resource in an identical manner to this request except with the dimensions of the target multiplied
     * by the given size multiplier. If the thumbnail load completes before the fullsize load, the thumbnail will
     * be shown. If the thumbnail load completes afer the fullsize load, the thumbnail will not be shown.
     *
     * <p>
     *     Note - The thumbnail resource will be smaller than the size requested so the target (or {@link ImageView})
     *     must be able to scale the thumbnail appropriately. See {@link android.widget.ImageView.ScaleType}.
     * </p>
     *
     * <p>
     *     Almost all options will be copied from the original load, including the
     *     {@link com.bumptech.glide.load.model.ModelLoader}, {@link com.bumptech.glide.load.ResourceDecoder}, and
     *     {@link Transformation}s. However, {@link #placeholder(int)} and {@link #error(int)},
     *     and {@link #listener(RequestListener)} will only be used on the fullsize load and will not be copied for
     *     the thumbnail load.
     * </p>
     *
     * <p>
     *     Recursive calls to thumbnail are supported.
     * </p>
     *
     * @param sizeMultiplier The multiplier to apply to the {@link Target}'s dimensions when loading the thumbnail.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> thumbnail(
            float sizeMultiplier) {
        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.thumbSizeMultiplier = sizeMultiplier;

        return this;
    }

    /**
     * Applies a multiplier to the {@link Target}'s size before loading the resource. Useful for loading thumbnails
     * or trying to avoid loading huge resources (particularly {@link android.graphics.Bitmap}s on devices with overly
     * dense screens.
     *
     * @param sizeMultiplier The multiplier to apply to the {@link Target}'s dimensions when loading the resource.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> sizeMultiplier(
            float sizeMultiplier) {
        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.sizeMultiplier = sizeMultiplier;

        return this;
    }

    /**
     * Sets the {@link com.bumptech.glide.load.ResourceDecoder} to use to load the resource from the original data.
     * By default, this decoder will only be used if the final transformed resource is not in the disk cache.
     *
     * @see #cacheDecoder(com.bumptech.glide.load.ResourceDecoder)
     * @see com.bumptech.glide.load.engine.DiskCacheStrategy
     *
     * @param decoder The {@link com.bumptech.glide.load.ResourceDecoder} to use to decode the resource.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> decoder(
            ResourceDecoder<DataType, ResourceType> decoder) {
        // loadProvider will be null if model is null, in which case we're not going to load anything so it's ok to
        // ignore the decoder.
        if (loadProvider != null) {
            loadProvider.setSourceDecoder(decoder);
        }

        return this;
    }

    /**
     * Sets the {@link com.bumptech.glide.load.ResourceDecoder} to use to load the resource from the disk cache. By
     * default, this decoder will only be used if the final transformed resource is already in the disk cache.
     *
     * @see #decoder(com.bumptech.glide.load.ResourceDecoder)
     * @see com.bumptech.glide.load.engine.DiskCacheStrategy
     *
     * @param cacheDecoder The decoder to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> cacheDecoder(
            ResourceDecoder<File, ResourceType> cacheDecoder) {
        // loadProvider will be null if model is null, in which case we're not going to load anything so it's ok to
        // ignore the decoder.
        if (loadProvider != null) {
            loadProvider.setCacheDecoder(cacheDecoder);
        }

        return this;
    }

    /**
     * Sets the source encoder to use to encode the data retrieved by this request directly into cache. The returned
     * resource will then be decoded from the cached data.
     *
     * @see com.bumptech.glide.load.engine.DiskCacheStrategy
     *
     * @param sourceEncoder The encoder to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> sourceEncoder(
            Encoder<DataType> sourceEncoder) {
        if (loadProvider != null) {
            loadProvider.setSourceEncoder(sourceEncoder);
        }

        return this;
    }

    /**
     * Sets the {@link com.bumptech.glide.load.engine.DiskCacheStrategy} to use for this load. Defaults to
     * {@link com.bumptech.glide.load.engine.DiskCacheStrategy#RESULT}.
     *
     * <p>
     *     For most applications {@link com.bumptech.glide.load.engine.DiskCacheStrategy#RESULT} is ideal.
     *     Applications that use the same resource multiple times in multiple sizes and are willing to trade off some
     *     speed and disk space in return for lower bandwidth usage may want to consider using
     *     {@link com.bumptech.glide.load.engine.DiskCacheStrategy#SOURCE} or
     *     {@link com.bumptech.glide.load.engine.DiskCacheStrategy#RESULT}. Any download only operations should
     *     typically use {@link com.bumptech.glide.load.engine.DiskCacheStrategy#SOURCE}.
     * </p>
     *
     * @param strategy The strategy to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType>  diskCacheStrategy(
            DiskCacheStrategy strategy) {
        this.diskCacheStrategy = strategy;

        return this;
    }

    /**
     * Sets the {@link com.bumptech.glide.load.Encoder} to use to encode the original data directly to cache. Will only
     * be used if the original data is not already in cache and if the
     * {@link com.bumptech.glide.load.engine.DiskCacheStrategy} is set to
     * {@link com.bumptech.glide.load.engine.DiskCacheStrategy#SOURCE} or
     * {@link com.bumptech.glide.load.engine.DiskCacheStrategy#ALL}.
     *
     * @see #sourceEncoder(com.bumptech.glide.load.Encoder)
     * @see com.bumptech.glide.load.engine.DiskCacheStrategy
     *
     * @param encoder The encoder to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> encoder(
            ResourceEncoder<ResourceType> encoder) {
        // loadProvider will be null if model is null, in which case we're not going to load anything so it's ok to
        // ignore the encoder.
        if (loadProvider != null) {
            loadProvider.setEncoder(encoder);
        }

        return this;
    }

    /**
     * Sets the priority for this load.
     *
     * @param priority A priority.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> priority(
            Priority priority) {
        this.priority = priority;

        return this;
    }

    /**
     * Transform resources with the given {@link Transformation}s. Replaces any existing transformation or
     * transformations.
     *
     * @param transformations the transformations to apply in order.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> transform(
            Transformation<ResourceType>... transformations) {
        isTransformationSet = true;
        if (transformations.length == 1) {
            transformation = transformations[0];
        } else {
            transformation = new MultiTransformation<ResourceType>(transformations);
        }

        return this;
    }

    /**
     * Removes the current {@link com.bumptech.glide.load.Transformation}.
     *
     * @return This request builder.
     */
    @SuppressWarnings("unchecked")
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> dontTransform() {
        Transformation<ResourceType> transformation = UnitTransformation.get();
        return transform(transformation);
    }

    /**
     * Sets the {@link com.bumptech.glide.load.resource.transcode.ResourceTranscoder} to use for this load.
     *
     * @see com.bumptech.glide.load.resource.transcode.UnitTranscoder
     * @see com.bumptech.glide.load.resource.transcode.GlideBitmapDrawableTranscoder
     * @see com.bumptech.glide.load.resource.transcode.GifBitmapWrapperDrawableTranscoder
     *
     * @param transcoder The transcoder to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> transcoder(
            ResourceTranscoder<ResourceType, TranscodeType> transcoder) {
        if (loadProvider != null) {
            loadProvider.setTranscoder(transcoder);
        }

        return this;
    }

    /**
     * Removes any existing animation set on the builder. Will be overridden by subsequent calls that set an animation.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> dontAnimate() {
        GlideAnimationFactory<TranscodeType> animation = NoAnimation.getFactory();
        return animate(animation);
    }

    /**
     * Sets an animation to run on the wrapped target when an resource load finishes. Will only be run if the resource
     * was loaded asynchronously (ie was not in the memory cache)
     *
     * @param animationId The resource id of the animation to run
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(int animationId) {
        return animate(new ViewAnimationFactory<TranscodeType>(context, animationId));
    }

    /**
     * Sets an animation to run on the wrapped target when a resource load finishes. Will only be run if the resource
     * was loaded asynchronously (ie was not in the memory cache)
     *
     * @see #animate(int)
     * @see #animate(com.bumptech.glide.request.animation.ViewPropertyAnimation.Animator)
     *
     * @deprecated If this builder is used for multiple loads, using this method will result in multiple view's being
     * asked to start an animation using a single {@link android.view.animation.Animation} object which results in
     * views animating repeatedly. Use {@link #animate(int)} or
     * {@link #animate(com.bumptech.glide.request.animation.ViewPropertyAnimation.Animator)}. Scheduled to be removed in
     * Glide 4.0.
     * @param animation The animation to run
     * @return This request builder.
     */
    @Deprecated
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(Animation animation) {
        return animate(new ViewAnimationFactory<TranscodeType>(animation));
    }

    /**
     * Sets an animator to run a {@link android.view.ViewPropertyAnimator} on a view that the target may be wrapping
     * when a resource load finishes. Will only be run if the load was loaded asynchronously (ie was not in the
     * memory cache).
     *
     * @param animator The {@link com.bumptech.glide.request.animation.ViewPropertyAnimation.Animator} to run.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(
            ViewPropertyAnimation.Animator animator) {
        return animate(new ViewPropertyAnimationFactory<TranscodeType>(animator));
    }

    GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> animate(
            GlideAnimationFactory<TranscodeType> animationFactory) {
        if (animationFactory == null) {
            throw new NullPointerException("Animation factory must not be null!");
        }
        this.animationFactory = animationFactory;

        return this;
    }

    /**
     * Sets an Android resource id for a {@link android.graphics.drawable.Drawable} resourceto display while a resource
     * is loading.
     *
     * @param resourceId The id of the resource to use as a placeholder
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> placeholder(
            int resourceId) {
        this.placeholderId = resourceId;

        return this;
    }

    /**
     * Sets an {@link android.graphics.drawable.Drawable} to display while a resource is loading.
     *
     * @param drawable The drawable to display as a placeholder.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> placeholder(
            Drawable drawable) {
        this.placeholderDrawable = drawable;

        return this;
    }

    /**
     * Sets an {@link android.graphics.drawable.Drawable} to display if the model provided to
     * {@link #load(Object)} is {@code null}.
     *
     * <p>
     *   If a fallback is not set, null models will cause the error drawable to be displayed. If
     *   the error drawable is not set, the placeholder will be displayed.
     * </p>
     *
     * @see #placeholder(Drawable)
     * @see #placeholder(int)
     *
     * @param drawable The drawable to display as a placeholder.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> fallback(
            Drawable drawable) {
        this.fallbackDrawable = drawable;

        return this;
    }

    /**
     * Sets a resource to display if the model provided to {@link #load(Object)} is {@code null}.
     *
     * <p>
     *   If a fallback is not set, null models will cause the error drawable to be displayed. If
     *   the error drawable is not set, the placeholder will be displayed.
     * </p>
     *
     * @see #placeholder(Drawable)
     * @see #placeholder(int)
     *
     * @param resourceId The id of the resource to use as a fallback.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> fallback(
            int resourceId) {
        this.fallbackResource = resourceId;

        return this;
    }

    /**
     * Sets a resource to display if a load fails.
     *
     * @param resourceId The id of the resource to use as a placeholder.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> error(
            int resourceId) {
        this.errorId = resourceId;

        return this;
    }

    /**
     * Sets a {@link Drawable} to display if a load fails.
     *
     * @param drawable The drawable to display.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> error(
            Drawable drawable) {
        this.errorPlaceholder = drawable;

        return this;
    }

    /**
     * Sets a RequestBuilder listener to monitor the resource load. It's best to create a single instance of an
     * exception handler per type of request (usually activity/fragment) rather than pass one in per request to
     * avoid some redundant object allocation.
     *
     * @param requestListener The request listener to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> listener(
            RequestListener<? super ModelType, TranscodeType> requestListener) {
        this.requestListener = requestListener;

        return this;
    }

    /**
     * Allows the loaded resource to skip the memory cache.
     *
     * <p>
     *     Note - this is not a guarantee. If a request is already pending for this resource and that request is not
     *     also skipping the memory cache, the resource will be cached in memory.
     * </p>
     *
     * @param skip True to allow the resource to skip the memory cache.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> skipMemoryCache(boolean skip) {
        this.isCacheable = !skip;

        return this;
    }

    /**
     * Overrides the {@link Target}'s width and height with the given values. This is useful almost exclusively for
     * thumbnails, and should only be used when you both need a very specific sized image and when it is impossible or
     * impractical to return that size from {@link Target#getSize(com.bumptech.glide.request.target.SizeReadyCallback)}.
     *
     * @param width The width in pixels to use to load the resource.
     * @param height The height in pixels to use to load the resource.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> override(int width, int height) {
        if (!Util.isValidDimensions(width, height)) {
            throw new IllegalArgumentException("Width and height must be Target#SIZE_ORIGINAL or > 0");
        }
        this.overrideWidth = width;
        this.overrideHeight = height;

        return this;
    }

    /**
     * Sets some additional data to be mixed in to the memory and disk cache keys allowing the caller more control over
     * when cached data is invalidated.
     *
     * <p>
     *     Note - The signature does not replace the cache key, it is purely additive.
     * </p>
     *
     * @see com.bumptech.glide.signature.StringSignature
     *
     * @param signature A unique non-null {@link com.bumptech.glide.load.Key} representing the current state of the
     *                  model that will be mixed in to the cache key.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> signature(Key signature) {
        if (signature == null) {
            throw new NullPointerException("Signature must not be null");
        }
        this.signature = signature;
        return this;
    }

    /**
     * Sets the specific model to load data for.
     *
     * <p>
     *      This method must be called at least once before {@link #into(com.bumptech.glide.request.target.Target)} is
     *      called.
     * </p>
     *
     * @param model The model to load data for, or null.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> load(ModelType model) {
        this.model = model;
        isModelSet = true;
        return this;
    }

    /**
     * Returns a copy of this request builder with all of the options set so far on this builder.
     *
     * <p>
     *     This method returns a "deep" copy in that all non-immutable arguments are copied such that changes to one
     *     builder will not affect the other builder. However, in addition to immutable arguments, the current model
     *     is not copied copied so changes to the model will affect both builders.
     * </p>
     */
    @SuppressWarnings("unchecked")
    @Override
    public GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> clone() {
        try {
            GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType> clone =
                    (GenericRequestBuilder<ModelType, DataType, ResourceType, TranscodeType>) super.clone();
            clone.loadProvider = loadProvider != null ? loadProvider.clone() : null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set the target the resource will be loaded into.
     *
     * @see Glide#clear(com.bumptech.glide.request.target.Target)
     *
     * @param target The target to load the resource into.
     * @return The given target.
     * 此时Y为GlideDrawableImageViewTarget对象
     */
    public <Y extends Target<TranscodeType>> Y into(Y target) {
        Util.assertMainThread();
        if (target == null) {
            throw new IllegalArgumentException("You must pass in a non null Target");
        }
        if (!isModelSet) {
            throw new IllegalArgumentException("You must first set a model (try #load())");
        }

        Request previous = target.getRequest();

        if (previous != null) {
            previous.clear();
            requestTracker.removeRequest(previous);
            previous.recycle();
        }
        //核心 构建一个Request对象
        //Request是用来发出加载图片请求的，它是Glide中非常关键的一个组件。
        // 我们先来看buildRequest()方法是如何构建Request对象的：
        //将之前的load里面的方法封装成一个Request对象，
        Request request = buildRequest(target);
        target.setRequest(request);
        lifecycle.addListener(target);
        //执行这个Request对象，这也是重点，核心代码
        requestTracker.runRequest(request);

        return target;
    }

    /**
     * Sets the {@link ImageView} the resource will be loaded into, cancels any existing loads into the view, and frees
     * any resources Glide may have previously loaded into the view so they may be reused.
     *
     * @see Glide#clear(android.view.View)
     *
     * @param view The view to cancel previous loads for and load the new resource into.
     * @return The {@link com.bumptech.glide.request.target.Target} used to wrap the given {@link ImageView}.
     */
    public Target<TranscodeType> into(ImageView view) {
        //强制保证在主线程中执行
        Util.assertMainThread();
        if (view == null) {
            throw new IllegalArgumentException("You must pass in a non null View");
        }
        //ImageView默认的ScaleType是fitCenter
        if (!isTransformationSet && view.getScaleType() != null) {
            switch (view.getScaleType()) {
                case CENTER_CROP:
                    applyCenterCrop();
                    break;
                    //ImageView默认的scaleType是FIT_CENTER，因此会自动添加一个FitCenter的图片变换，
                // 而在这个图片变换过程中做了某些操作，导致图片充满了全屏
                case FIT_CENTER:
                case FIT_START:
                case FIT_END:
                    applyFitCenter();
                    break;
                //$CASES-OMITTED$
                default:
                    // Do nothing.
            }
        }

        //最后调用了这句代码，此时transcodeClass为GlideDrawable?
        //通过glide.buildImageViewTarget()方法，我们构建出了一个GlideDrawableImageViewTarget对象
        return into(glide.buildImageViewTarget(view, transcodeClass));
    }

    /**
     * Returns a future that can be used to do a blocking get on a background thread.
     *
     * @param width The desired width in pixels, or {@link Target#SIZE_ORIGINAL}. This will be overridden by
     *             {@link #override * (int, int)} if previously called.
     * @param height The desired height in pixels, or {@link Target#SIZE_ORIGINAL}. This will be overridden by
     *              {@link #override * (int, int)}} if previously called).
     *
     * @see Glide#clear(com.bumptech.glide.request.FutureTarget)
     *
     * @return An {@link com.bumptech.glide.request.FutureTarget} that can be used to obtain the
     *         resource in a blocking manner.
     */
    public FutureTarget<TranscodeType> into(int width, int height) {
        final RequestFutureTarget<ModelType, TranscodeType> target =
                new RequestFutureTarget<ModelType, TranscodeType>(glide.getMainHandler(), width, height);

        // TODO: Currently all loads must be started on the main thread...
        glide.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                if (!target.isCancelled()) {
                    into(target);
                }
            }
        });

        return target;
    }

    /**
     * Preloads the resource into the cache using the given width and height.
     *
     * <p>
     *     Pre-loading is useful for making sure that resources you are going to to want in the near future are
     *     available quickly.
     * </p>
     *
     *
     * @see com.bumptech.glide.ListPreloader
     *
     * @param width The desired width in pixels, or {@link Target#SIZE_ORIGINAL}. This will be overridden by
     *             {@link #override * (int, int)} if previously called.
     * @param height The desired height in pixels, or {@link Target#SIZE_ORIGINAL}. This will be overridden by
     *              {@link #override * (int, int)}} if previously called).
     * @return A {@link Target} that can be used to cancel the load via
     *        {@link Glide#clear(com.bumptech.glide.request.target.Target)}.
     */
    public Target<TranscodeType> preload(int width, int height) {
        final PreloadTarget<TranscodeType> target = PreloadTarget.obtain(width, height);
        return into(target);
    }

    /**
     * Preloads the resource into the cache using {@link Target#SIZE_ORIGINAL} as the target width and height.
     * Equivalent to calling {@link #preload(int, int)} with {@link Target#SIZE_ORIGINAL} as the width and height.
     *
     * @see #preload(int, int)
     *
     * @return A {@link Target} that can be used to cancel the load via
     *        {@link Glide#clear(com.bumptech.glide.request.target.Target)}.
     *        图片的预加载过程
     */
    public Target<TranscodeType> preload() {
        return preload(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
    }

    void applyCenterCrop() {
        // To be implemented by subclasses when possible.
    }

    void applyFitCenter() {
        // To be implemented by subclasses when possible.
    }

    private Priority getThumbnailPriority() {
        final Priority result;
        if (priority == Priority.LOW) {
            result = Priority.NORMAL;
        } else if (priority == Priority.NORMAL) {
            result = Priority.HIGH;
        } else {
            result = Priority.IMMEDIATE;
        }
        return result;
    }

    //构建buildRequest对象。
    private Request buildRequest(Target<TranscodeType> target) {
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        return buildRequestRecursive(target, null);
    }

    private Request buildRequestRecursive(Target<TranscodeType> target, ThumbnailRequestCoordinator parentCoordinator) {
        if (thumbnailRequestBuilder != null) {
            if (isThumbnailBuilt) {
                throw new IllegalStateException("You cannot use a request as both the main request and a thumbnail, "
                        + "consider using clone() on the request(s) passed to thumbnail()");
            }
            // Recursive case: contains a potentially recursive thumbnail request builder.
            if (thumbnailRequestBuilder.animationFactory.equals(NoAnimation.getFactory())) {
                thumbnailRequestBuilder.animationFactory = animationFactory;
            }

            if (thumbnailRequestBuilder.priority == null) {
                thumbnailRequestBuilder.priority = getThumbnailPriority();
            }

            if (Util.isValidDimensions(overrideWidth, overrideHeight)
                    && !Util.isValidDimensions(thumbnailRequestBuilder.overrideWidth,
                            thumbnailRequestBuilder.overrideHeight)) {
              thumbnailRequestBuilder.override(overrideWidth, overrideHeight);
            }

            ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
            Request fullRequest = obtainRequest(target, sizeMultiplier, priority, coordinator);
            // Guard against infinite recursion.
            isThumbnailBuilt = true;
            // Recursively generate thumbnail requests.
            Request thumbRequest = thumbnailRequestBuilder.buildRequestRecursive(target, coordinator);
            isThumbnailBuilt = false;
            coordinator.setRequests(fullRequest, thumbRequest);
            return coordinator;
        } else if (thumbSizeMultiplier != null) {
            // Base case: thumbnail multiplier generates a thumbnail request, but cannot recurse.
            ThumbnailRequestCoordinator coordinator = new ThumbnailRequestCoordinator(parentCoordinator);
            Request fullRequest = obtainRequest(target, sizeMultiplier, priority, coordinator);
            Request thumbnailRequest = obtainRequest(target, thumbSizeMultiplier, getThumbnailPriority(), coordinator);
            coordinator.setRequests(fullRequest, thumbnailRequest);
            return coordinator;
        } else {
            // Base case: no thumbnail.
            //上面的代码是处理缩略图的，这个是重点，没有缩略图的情况
            return obtainRequest(target, sizeMultiplier, priority, parentCoordinator);
        }
    }

    //这里面有调用了GenericRequest.obtain方法
    
    private Request obtainRequest(Target<TranscodeType> target, float sizeMultiplier, Priority priority,
            RequestCoordinator requestCoordinator) {
        //刚才在load()方法中调用的所有API，其实都是在这里组装到Request对象当中
        return GenericRequest.obtain(
                loadProvider,
                model,
                signature,
                context,
                priority,
                target,
                sizeMultiplier,
                placeholderDrawable,
                placeholderId,
                errorPlaceholder,
                errorId,
                fallbackDrawable,
                fallbackResource,
                requestListener,
                requestCoordinator,
                glide.getEngine(),
                transformation,
                transcodeClass,
                isCacheable,
                animationFactory,
                overrideWidth,
                overrideHeight,
                diskCacheStrategy);
    }
}
