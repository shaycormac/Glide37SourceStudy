package com.bumptech.glide.request.target;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.request.animation.GlideAnimation;

/**
 * A base {@link com.bumptech.glide.request.target.Target} for displaying resources in
 * {@link android.widget.ImageView}s.
 *
 * @param <Z> The type of resource that this target will display in the wrapped {@link android.widget.ImageView}.
 */
public abstract class ImageViewTarget<Z> extends ViewTarget<ImageView, Z> implements GlideAnimation.ViewAdapter {

    public ImageViewTarget(ImageView view) {
        super(view);
    }

    /**
     * Returns the current {@link android.graphics.drawable.Drawable} being displayed in the view using
     * {@link android.widget.ImageView#getDrawable()}.
     */
    @Override
    public Drawable getCurrentDrawable() {
        return view.getDrawable();
    }

    /**
     * Sets the given {@link android.graphics.drawable.Drawable} on the view using
     * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}.
     *
     * @param drawable {@inheritDoc}
     */
    @Override
    public void setDrawable(Drawable drawable) {
        view.setImageDrawable(drawable);
    }

    /**
     * Sets the given {@link android.graphics.drawable.Drawable} on the view using
     * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}.
     *
     * @param placeholder {@inheritDoc}
     * 刚开始使用占位图
     */
    @Override
    public void onLoadStarted(Drawable placeholder) {
        view.setImageDrawable(placeholder);
    }

    /**
     * Sets the given {@link android.graphics.drawable.Drawable} on the view using
     * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}.
     *
     * @param errorDrawable {@inheritDoc}
     * 直接执行了这个方法，调用ImageView.setImageDrawable
     */
    @Override
    public void onLoadFailed(Exception e, Drawable errorDrawable) {
        view.setImageDrawable(errorDrawable);
    }

    /**
     * Sets the given {@link android.graphics.drawable.Drawable} on the view using
     * {@link android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)}.
     *
     * @param placeholder {@inheritDoc}
     */
    @Override
    public void onLoadCleared(Drawable placeholder) {
        view.setImageDrawable(placeholder);
    }

    @Override
    public void onResourceReady(Z resource, GlideAnimation<? super Z> glideAnimation) {
        if (glideAnimation == null || !glideAnimation.animate(resource, this)) {
            //静态图片的抽象方法，ImageViewTarget的setResource()方法是一个抽象方法，具体的实现还是在子类那边实现的。
            //那子类的setResource()方法是怎么实现的呢？回头再来看一下GlideDrawableImageViewTarget的setResource()方法，没错，
            // 调用的view.setImageDrawable()方法，而这个view就是ImageView。代码执行到这里，图片终于也就显示出来了。
            setResource(resource);
        }
    }

    protected abstract void setResource(Z resource);

}

