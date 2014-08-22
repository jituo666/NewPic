
package com.xjt.newpic.views.render;

import android.content.Context;
import android.graphics.Rect;

import com.xjt.newpic.R;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.view.ThumbnailView;
import com.xjt.newpic.views.opengl.FadeOutTexture;
import com.xjt.newpic.views.opengl.GLESCanvas;
import com.xjt.newpic.views.opengl.NinePatchTexture;
import com.xjt.newpic.views.opengl.ResourceTexture;
import com.xjt.newpic.views.opengl.Texture;

/**
 * @Author Jituo.Xuan
 * @Date 8:17:02 PM Jul 24, 2014
 * @Comments:null
 */
public abstract class AbstractThumbnailRender implements ThumbnailView.Renderer {

    private static final String TAG = AbstractThumbnailRender.class.getSimpleName();

    private final ResourceTexture mVideoOverlay;
    protected ResourceTexture mVideoPlayIcon;
    protected final NinePatchTexture mFramePressed;
    protected final ResourceTexture mFramePreSelected;
    protected final ResourceTexture mFrameSelected;
    private FadeOutTexture mFramePressedUp;

    protected AbstractThumbnailRender(Context context) {
        mVideoOverlay = new ResourceTexture(context, R.drawable.ic_video_thumb);
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_video_play);
        mFramePressed = new NinePatchTexture(context, R.drawable.grid_pressed);
        mFramePreSelected = new ResourceTexture(context, R.drawable.grid_preselected);
        mFrameSelected = new ResourceTexture(context, R.drawable.grid_selected);
    }

    protected void drawContent(GLESCanvas canvas, Texture content, int width, int height, int rotation) {
        canvas.save(GLESCanvas.SAVE_FLAG_MATRIX);
        // The content is always rendered in to the largest square that fits inside the thumbnail, aligned to the top of the thumbnail.
        width = height = Math.min(width, height);
        if (rotation != 0) {
            canvas.translate(width / 2, height / 2);
            canvas.rotate(rotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
        }
        // Fit the content into the box
        float scale = Math.min((float) width / content.getWidth(), (float) height / content.getHeight());
        //LLog.i(TAG, "scale:" + scale);
        canvas.scale(scale, scale, 1);
        content.draw(canvas, 0, 0);
        canvas.restore();
    }

    protected void drawVideoOverlay(GLESCanvas canvas, int width, int height) {
        // Scale the video overlay to the height of the thumbnail and put it  on the left side.
        ResourceTexture v = mVideoOverlay;
        float scale = (float) height / v.getHeight();
        int w = Math.round(scale * v.getWidth());
        int h = Math.round(scale * v.getHeight());
        v.draw(canvas, 0, 0, w, h);

        int s = Math.min(width, height) / 6;
        mVideoPlayIcon.draw(canvas, (width - s) / 2, (height - s) / 2, s, s);
    }

    protected boolean isPressedUpFrameFinished() {
        if (mFramePressedUp != null) {
            if (mFramePressedUp.isAnimating()) {
                return false;
            } else {
                mFramePressedUp = null;
            }
        }
        return true;
    }

    protected void drawPressedUpFrame(GLESCanvas canvas, int width, int height) {
        if (mFramePressedUp == null) {
            mFramePressedUp = new FadeOutTexture(mFramePressed);
        }
        drawFrame(canvas, mFramePressed.getPaddings(), mFramePressedUp, 0, 0, width, height);
    }

    protected void drawPressedFrame(GLESCanvas canvas, int width, int height) {
        drawFrame(canvas, mFramePressed.getPaddings(), mFramePressed, 0, 0, width, height);
    }

    protected void drawPreSelectedFrame(GLESCanvas canvas, int width, int height) {
        drawFrame(canvas, new Rect(0, 0, 0, 0), mFramePreSelected, 0, 0, width, height);
    }

    protected void drawSelectedFrame(GLESCanvas canvas, int width, int height) {
        drawFrame(canvas, new Rect(0, 0, 0, 0), mFrameSelected, 0, 0, width, height);
    }

    protected static void drawFrame(GLESCanvas canvas, Rect padding, Texture frame,
            int x, int y, int width, int height) {
        frame.draw(canvas, x - padding.left, y - padding.top, width + padding.left + padding.right,
                height + padding.top + padding.bottom);
    }
}