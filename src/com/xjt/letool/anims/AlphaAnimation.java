package com.xjt.letool.anims;

import com.xjt.letool.opengl.GLESCanvas;
import com.xjt.letool.utils.Utils;


public class AlphaAnimation extends CanvasAnimation {
    private final float mStartAlpha;
    private final float mEndAlpha;
    private float mCurrentAlpha;

    public AlphaAnimation(float from, float to) {
        mStartAlpha = from;
        mEndAlpha = to;
        mCurrentAlpha = from;
    }

    @Override
    public void apply(GLESCanvas canvas) {
        canvas.multiplyAlpha(mCurrentAlpha);
    }

    @Override
    public int getCanvasSaveFlags() {
        return GLESCanvas.SAVE_FLAG_ALPHA;
    }

    @Override
    protected void onCalculate(float progress) {
        mCurrentAlpha = Utils.clamp(mStartAlpha
                + (mEndAlpha - mStartAlpha) * progress, 0f, 1f);
    }
}
