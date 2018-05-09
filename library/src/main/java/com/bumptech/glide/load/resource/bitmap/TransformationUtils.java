package com.bumptech.glide.load.resource.bitmap;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Build;
import android.util.Log;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;

/**
 * A class with methods to efficiently resize Bitmaps.
 * 高效的转换图片？？
 */
public final class TransformationUtils {
    private static final String TAG = "TransformationUtils";
    public static final int PAINT_FLAGS = Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG;

    private TransformationUtils() {
        // Utility class.
    }

    /**
     * A potentially expensive operation to crop the given Bitmap so that it fills the given dimensions. This operation
     * is significantly less expensive in terms of memory if a mutable Bitmap with the given dimensions is passed in
     * as well.
     *
     * @param recycled A mutable Bitmap with dimensions width and height that we can load the cropped portion of toCrop
     *                 into.
     * @param toCrop The Bitmap to resize.
     * @param width The width in pixels of the final Bitmap.
     * @param height The height in pixels of the final Bitmap.
     * @return The resized Bitmap (will be recycled if recycled is not null).
     * 这段代码就是整个图片变换功能的核心代码了
     */
    public static Bitmap centerCrop(Bitmap recycled, Bitmap toCrop, int width, int height) {
        //如果要转换的图片和原始的图片一样，啥也不干！
        if (toCrop == null) {
            return null;
        } else if (toCrop.getWidth() == width && toCrop.getHeight() == height) {
            return toCrop;
        }
        // From ImageView/Bitmap.createScaledBitmap.
        //数学计算来算出画布的缩放的比例以及偏移值,到 m.postTranslate((为止
        final float scale;
        float dx = 0, dy = 0;
        Matrix m = new Matrix();
        if (toCrop.getWidth() * height > width * toCrop.getHeight()) {
            scale = (float) height / (float) toCrop.getHeight();
            dx = (width - toCrop.getWidth() * scale) * 0.5f;
        } else {
            scale = (float) width / (float) toCrop.getWidth();
            dy = (height - toCrop.getHeight() * scale) * 0.5f;
        }

        m.setScale(scale, scale);
        m.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
        //判断缓存池中取出的Bitmap对象是否为空，如果不为空就可以直接使用，如果为空则要创建一个新的Bitmap对象
        final Bitmap result;
        if (recycled != null) {
            result = recycled;
        } else {
            result = Bitmap.createBitmap(width, height, getSafeConfig(toCrop));
        }

        // We don't add or remove alpha, so keep the alpha setting of the Bitmap we were given.
        //将原图Bitmap对象的alpha值复制到裁剪Bitmap对象上面
        TransformationUtils.setAlpha(toCrop, result);
//裁剪Bitmap对象进行绘制，并将最终的结果进行返回
        //画布带上一个空的图片result
        Canvas canvas = new Canvas(result);
        //画笔
        Paint paint = new Paint(PAINT_FLAGS);
        //在画布上（已经有白纸了），把原图用画笔以及缩放画布，画出来
        canvas.drawBitmap(toCrop, m, paint);
        //返回这个画好的图片。
        return result;
    }

    /**
     * An expensive operation to resize the given Bitmap down so that it fits within the given dimensions maintain
     * the original proportions.
     *
     * @param toFit The Bitmap to shrink.
     * @param pool The BitmapPool to try to reuse a bitmap from.
     * @param width The width in pixels the final image will fit within.
     * @param height The height in pixels the final image will fit within.
     * @return A new Bitmap shrunk to fit within the given dimensions, or toFit if toFit's width or height matches the
     * given dimensions and toFit fits within the given dimensions
     * 默认的图片转换效果。
     */
    public static Bitmap fitCenter(Bitmap toFit, BitmapPool pool, int width, int height) {
        //如果需要裁剪的尺寸和原图一样大小，那就没必要裁剪了。直接返回原图
        if (toFit.getWidth() == width && toFit.getHeight() == height) 
        {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "requested target size matches input, returning input");
            }
            return toFit;
        }
        //需要的宽高和实际图片的宽高取值
        final float widthPercentage = width / (float) toFit.getWidth();
        final float heightPercentage = height / (float) toFit.getHeight();
        //取两者的最小值，理解就是保持充满屏幕的前提下，比例不失真，取小的。
        final float minPercentage = Math.min(widthPercentage, heightPercentage);

        // take the floor of the target width/height, not round. If the matrix
        // passed into drawBitmap rounds differently, we want to slightly
        // overdraw, not underdraw, to avoid artifacts from bitmap reuse.
        //然后拿这个值去和图片的宽高相乘，得到目标的宽高
        final int targetWidth = (int) (minPercentage * toFit.getWidth());
        final int targetHeight = (int) (minPercentage * toFit.getHeight());
       //如果一样的，啥也不做（正方形就可能存在这种情况）
        if (toFit.getWidth() == targetWidth && toFit.getHeight() == targetHeight) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "adjusted target size matches input, returning input");
            }
            return toFit;
        }
       //去图片缓存池中取可以复用的图片（大小相同）
        Bitmap.Config config = getSafeConfig(toFit);
        Bitmap toReuse = pool.get(targetWidth, targetHeight, config);
        if (toReuse == null) {
            toReuse = Bitmap.createBitmap(targetWidth, targetHeight, config);
        }
        // We don't add or remove alpha, so keep the alpha setting of the Bitmap we were given.
        //将原来的图片透明度给所用的图片
        TransformationUtils.setAlpha(toFit, toReuse);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "request: " + width + "x" + height);
            Log.v(TAG, "toFit:   " + toFit.getWidth() + "x" + toFit.getHeight());
            Log.v(TAG, "toReuse: " + toReuse.getWidth() + "x" + toReuse.getHeight());
            Log.v(TAG, "minPct:   " + minPercentage);
        }
       //同上了，首先仙剑一个画布，带上这个白纸
        Canvas canvas = new Canvas(toReuse);
        //对画布进行缩放
        Matrix matrix = new Matrix();
        matrix.setScale(minPercentage, minPercentage);
        //画笔
        Paint paint = new Paint(PAINT_FLAGS);
        //在画布上（已经有toReuse这张白纸了），通过画布的缩放进行画这个图
        canvas.drawBitmap(toFit, matrix, paint);
        //返回这个图
        return toReuse;
    }

    /**
     * Sets the alpha of the Bitmap we're going to re-use to the alpha of the Bitmap we're going to transform. This
     * keeps {@link android.graphics.Bitmap#hasAlpha()}} consistent before and after the transformation for
     * transformations that don't add or remove transparent pixels.
     *
     * @param toTransform The {@link android.graphics.Bitmap} that will be transformed.
     * @param outBitmap The {@link android.graphics.Bitmap} that will be returned from the transformation.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public static void setAlpha(Bitmap toTransform, Bitmap outBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1 && outBitmap != null) {
            outBitmap.setHasAlpha(toTransform.hasAlpha());
        }
    }

    /**
     * Returns a matrix with rotation set based on Exif orientation tag.
     * If the orientation is undefined or 0 null is returned.
     *
     * @deprecated No longer used by Glide, scheduled to be removed in Glide 4.0
     * @param pathToOriginal Path to original image file that may have exif data.
     * @return  A rotation in degrees based on exif orientation
     */
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @Deprecated
    public static int getOrientation(String pathToOriginal) {
        int degreesToRotate = 0;
        try {
            ExifInterface exif = new ExifInterface(pathToOriginal);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            return getExifOrientationDegrees(orientation);
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Unable to get orientation for image with path=" + pathToOriginal, e);
            }
        }
        return degreesToRotate;
    }

    /**
     * This is an expensive operation that copies the image in place with the pixels rotated.
     * If possible rather use getOrientationMatrix, and set that as the imageMatrix on an ImageView.
     *
     * @deprecated No longer used by Glide, scheduled to be removed in Glide 4.0
     * @param pathToOriginal Path to original image file that may have exif data.
     * @param imageToOrient Image Bitmap to orient.
     * @return The oriented bitmap. May be the imageToOrient without modification, or a new Bitmap.
     */
    @Deprecated
    public static Bitmap orientImage(String pathToOriginal, Bitmap imageToOrient) {
        int degreesToRotate = getOrientation(pathToOriginal);
        return rotateImage(imageToOrient, degreesToRotate);
    }

    /**
     * This is an expensive operation that copies the image in place with the pixels rotated.
     * If possible rather use getOrientationMatrix, and set that as the imageMatrix on an ImageView.
     *
     * @param imageToOrient Image Bitmap to orient.
     * @param degreesToRotate number of degrees to rotate the image by. If zero the original image is returned
     *                        unmodified.
     * @return The oriented bitmap. May be the imageToOrient without modification, or a new Bitmap.
     */
    public static Bitmap rotateImage(Bitmap imageToOrient, int degreesToRotate) {
        Bitmap result = imageToOrient;
        try {
            if (degreesToRotate != 0) {
                Matrix matrix = new Matrix();
                matrix.setRotate(degreesToRotate);
                result = Bitmap.createBitmap(
                        imageToOrient,
                        0,
                        0,
                        imageToOrient.getWidth(),
                        imageToOrient.getHeight(),
                        matrix,
                        true);
            }
        } catch (Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Exception when trying to orient image", e);
            }
        }
        return result;
    }

    /**
     * Get the # of degrees an image must be rotated to match the given exif orientation.
     *
     * @param exifOrientation The exif orientation [1-8]
     * @return the number of degrees to rotate
     */
    public static int getExifOrientationDegrees(int exifOrientation) {
        final int degreesToRotate;
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_TRANSPOSE:
            case ExifInterface.ORIENTATION_ROTATE_90:
                degreesToRotate = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                degreesToRotate = 180;
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
            case ExifInterface.ORIENTATION_ROTATE_270:
                degreesToRotate = 270;
                break;
            default:
                degreesToRotate = 0;

        }
        return degreesToRotate;
    }

    /**
     * Rotate and/or flip the image to match the given exif orientation.
     *
     * @param toOrient The bitmap to rotate/flip.
     * @param pool A pool that may or may not contain an image of the necessary dimensions.
     * @param exifOrientation the exif orientation [1-8].
     * @return The rotated and/or flipped image or toOrient if no rotation or flip was necessary.
     */
    public static Bitmap rotateImageExif(Bitmap toOrient, BitmapPool pool, int exifOrientation) {
        final Matrix matrix = new Matrix();
        initializeMatrixForRotation(exifOrientation, matrix);
        if (matrix.isIdentity()) {
            return toOrient;
        }

        // From Bitmap.createBitmap.
        final RectF newRect = new RectF(0, 0, toOrient.getWidth(), toOrient.getHeight());
        matrix.mapRect(newRect);

        final int newWidth = Math.round(newRect.width());
        final int newHeight = Math.round(newRect.height());

        Bitmap.Config config = getSafeConfig(toOrient);
        Bitmap result = pool.get(newWidth, newHeight, config);
        if (result == null) {
            result = Bitmap.createBitmap(newWidth, newHeight, config);
        }

        matrix.postTranslate(-newRect.left, -newRect.top);

        final Canvas canvas = new Canvas(result);
        final Paint paint = new Paint(PAINT_FLAGS);
        canvas.drawBitmap(toOrient, matrix, paint);

        return result;
    }

    private static Bitmap.Config getSafeConfig(Bitmap bitmap) {
      return bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
    }

    // Visible for testing.
    static void initializeMatrixForRotation(int exifOrientation, Matrix matrix) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                // Do nothing.
        }
    }
}
