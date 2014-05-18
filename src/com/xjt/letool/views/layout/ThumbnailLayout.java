
package com.xjt.letool.views.layout;

import android.graphics.Rect;

import com.xjt.letool.animations.IntegerAnim;
import com.xjt.letool.view.ThumbnailView;
import com.xjt.letool.view.ThumbnailView.Renderer;

public abstract class ThumbnailLayout {

    private static final String TAG = ThumbnailLayout.class.getSimpleName();

    public static final boolean WIDE = false;
    public static final int INDEX_NONE = -1;

    protected int mVisibleStart;
    protected int mVisibleEnd;

    protected int mThumbnailCount;
    protected int mThumbnailWidth;
    protected int mThumbnailHeight;
    protected int mThumbnailGap;

    protected ThumbnailLayoutSpec mSpec;
    protected Renderer mRenderer;

    protected int mWidth;
    protected int mHeight;

    protected int mUnitCount;
    protected int mContentLength;
    protected int mScrollPosition;
    protected IntegerAnim mVerticalPadding = new IntegerAnim();
    protected IntegerAnim mHorizontalPadding = new IntegerAnim();

    private LayoutListener mLayoutListener;

    public interface LayoutListener {

        public void onLayoutBeing(int count);
    }

    public int getThumbnailCount() {
        return mThumbnailCount;
    }

    public int getThumbnailWidth() {
        return mThumbnailWidth;
    }

    public int getThumbnailHeight() {
        return mThumbnailHeight;
    }

    public int getVisibleStart() {
        return mVisibleStart;
    }

    public int getVisibleEnd() {
        return mVisibleEnd;
    }

    public int getScrollLimit() {
        int limit = WIDE ? mContentLength - mWidth : mContentLength - mHeight;
        return limit <= 0 ? 0 : limit;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void setRenderer(ThumbnailView.Renderer render) {
        mRenderer = render;
    }

    public void setLayoutListener(LayoutListener l) {
        mLayoutListener = l;
    }

    public void setThumbnailCount(int thumbnailCount) {
        if (thumbnailCount == mThumbnailCount)
            return;
        if (thumbnailCount > 0) {
            //            mVerticalPadding.setEnabled(true);
            //            mHorizontalPadding.setEnabled(true);
        }
        mThumbnailCount = thumbnailCount;
        initThumbnailParameters();
        if (mLayoutListener != null) {
            mLayoutListener.onLayoutBeing(thumbnailCount);
        }
    }

    public void setThumbnailViewSize(int width, int height) {
        mWidth = width;
        mHeight = height;
        initThumbnailParameters();
    }

    public void setScrollPosition(int position) {
        if (mScrollPosition == position)
            return;
        mScrollPosition = position;
        updateVisibleThumbnailRange();
    }

    protected void setVisibleRange(int start, int end) {
        if (start == mVisibleStart && end == mVisibleEnd)
            return;
        if (start < end) {
            mVisibleStart = start;
            mVisibleEnd = end;
        } else {
            mVisibleStart = mVisibleEnd = 0;
        }
        if (mRenderer != null) {
            mRenderer.onVisibleRangeChanged(mVisibleStart, mVisibleEnd);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public abstract Rect getThumbnailRect(int index, Rect rect);

    public abstract int getThumbnailIndexByPosition(float x, float y);

    protected abstract void initThumbnailParameters();

    protected abstract void updateVisibleThumbnailRange();

    public boolean advanceAnimation(long animTime) {
        return mVerticalPadding.calculate(animTime) | mHorizontalPadding.calculate(animTime);
    }
}
