package com.xjt.newpic.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.TypedValue;

import com.xjt.newpic.R;
import com.xjt.newpic.views.opengl.ColorTexture;
import com.xjt.newpic.views.opengl.GLESCanvas;
import com.xjt.newpic.views.opengl.NinePatchTexture;

/**
 * @Author Jituo.Xuan
 * @Date 8:18:12 PM Jul 24, 2014
 * @Comments:null
 */
public class ScrollBarView extends GLView {

    @SuppressWarnings("unused")
    private static final String TAG = ScrollBarView.class.getSimpleName();

    private int mGripHeight;
    private int mGripWidth;
    private int mGripMajorPos;
    private int mGripMinorPos;
    private int mContentPosition;
    private int mContentTotal;

    private int mTrackLength;
    private NinePatchTexture mGripTexture;
    private ColorTexture mTrackTexture;

    public ScrollBarView(Context context, int gripWidth, int gripHeight) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.scrollbarThumbHorizontal, outValue, true);
        mGripTexture = new NinePatchTexture(context,R.drawable.scrollbar_handle_vertical);
        mTrackTexture = new ColorTexture(context.getResources().getColor(R.color.scroll_bar_track_color));
        mGripWidth = gripWidth;
        mGripHeight = gripHeight;
        mGripMajorPos = 0;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!changed)
            return;
        Rect b = bounds();
        if (mGripWidth > mGripHeight) {
            mGripMinorPos = (b.bottom - b.top - mGripHeight);
            mTrackLength = b.right - b.left;
        } else {
            mGripMinorPos = (b.right - b.left - mGripWidth);
            mTrackLength = b.bottom - b.top;
        }
    }

    // The content position is between 0 to "total". The current position is in "position".
    public void setContentPosition(int position, int total) {
        if (position == mContentPosition && total == mContentTotal) {
            return;
        }
        invalidate();
        mContentPosition = position;
        mContentTotal = total;
        // If the grip cannot move, don't draw it.
        if (mContentTotal <= 0) {
            mGripMajorPos = 0;
            mGripWidth = 0;
            return;
        }
        // Map from the content range to scroll bar range.
        // mContentTotal --> getWidth() - mGripWidth
        // mContentPosition --> mGripMajorPos
        float r = 0.0f;
        if (mGripWidth > mGripHeight) {
            r = (getWidth() - mGripWidth) / (float) mContentTotal;
        } else {
            r = (getHeight() - mGripHeight) / (float) mContentTotal;
        }
        mGripMajorPos = position + Math.round(r * mContentPosition);
    }

    @Override
    protected void render(GLESCanvas canvas) {
        super.render(canvas);
        if (mGripWidth == 0)
            return;
        if (mGripWidth > mGripHeight) {
            mTrackTexture.draw(canvas, mContentPosition, mGripMinorPos + mGripHeight / 2, mTrackLength, 1);
            mGripTexture.draw(canvas, mGripMajorPos, mGripMinorPos, mGripWidth, mGripHeight);
        } else {
            mTrackTexture.draw(canvas, mGripMinorPos + mGripWidth / 2, mContentPosition, 1, mTrackLength);
            mGripTexture.draw(canvas, mGripMinorPos, mGripMajorPos, mGripWidth, mGripHeight);
        }

    }
}
