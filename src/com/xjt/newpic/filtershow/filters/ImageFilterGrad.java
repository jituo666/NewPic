package com.xjt.newpic.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.xjt.newpic.R;

import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.Script.LaunchOptions;
import android.support.v8.renderscript.Type;

import com.xjt.newpic.filtershow.pipeline.FilterEnvironment;

public class ImageFilterGrad extends ImageFilterRS {
    private static final String TAG = ImageFilterGrad.class.getSimpleName();
    private ScriptC_grad mScript;
    private Bitmap mSourceBitmap;

    private static final int STRIP_SIZE = 64;

    FilterGradRepresentation mParameters = new FilterGradRepresentation(0);

    public ImageFilterGrad() {
        mName = "grad";
    }

    @Override
    public FilterRepresentation getDefaultRepresentation() {
        return new FilterGradRepresentation(R.drawable.effect_sample_31);
    }

    @Override
    public void useRepresentation(FilterRepresentation representation) {
        mParameters = (FilterGradRepresentation) representation;
    }

    @Override
    protected void resetAllocations() {

    }

    @Override
    public void resetScripts() {
        if (mScript != null) {
            mScript.destroy();
            mScript = null;
        }
    }
    @Override
    protected void createFilter(android.content.res.Resources res, float scaleFactor, int quality) {
        createFilter(res, scaleFactor, quality, getInPixelsAllocation());
    }

    @Override
    protected void createFilter(android.content.res.Resources res, float scaleFactor, int quality, Allocation in) {
        RenderScript rsCtx = getRenderScriptContext();

        Type.Builder tb_float = new Type.Builder(rsCtx, Element.F32_4(rsCtx));
        tb_float.setX(in.getType().getX());
        tb_float.setY(in.getType().getY());
        mScript = new ScriptC_grad(rsCtx, res, R.raw.grad);
    }


    private Bitmap getSourceBitmap() {
        assert (mSourceBitmap != null);
        return mSourceBitmap;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (SIMPLE_ICONS && FilterEnvironment.QUALITY_ICON == quality) {
            return bitmap;
        }

        mSourceBitmap = bitmap;
        Bitmap ret = super.apply(bitmap, scaleFactor, quality);
        mSourceBitmap = null;

        return ret;
    }

    @Override
    protected void bindScriptValues() {
        int width = getInPixelsAllocation().getType().getX();
        int height = getInPixelsAllocation().getType().getY();
        mScript.set_inputWidth(width);
        mScript.set_inputHeight(height);
    }

    @Override
    protected void runFilter() {
        int[] x1 = mParameters.getXPos1();
        int[] y1 = mParameters.getYPos1();
        int[] x2 = mParameters.getXPos2();
        int[] y2 = mParameters.getYPos2();

        int width = getInPixelsAllocation().getType().getX();
        int height = getInPixelsAllocation().getType().getY();
        Matrix m = getOriginalToScreenMatrix(width, height);
        float[] coord = new float[2];
        for (int i = 0; i < x1.length; i++) {
            coord[0] = x1[i];
            coord[1] = y1[i];
            m.mapPoints(coord);
            x1[i] = (int) coord[0];
            y1[i] = (int) coord[1];
            coord[0] = x2[i];
            coord[1] = y2[i];
            m.mapPoints(coord);
            x2[i] = (int) coord[0];
            y2[i] = (int) coord[1];
        }

        mScript.set_mask(mParameters.getMask());
        mScript.set_xPos1(x1);
        mScript.set_yPos1(y1);
        mScript.set_xPos2(x2);
        mScript.set_yPos2(y2);

        mScript.set_brightness(mParameters.getBrightness());
        mScript.set_contrast(mParameters.getContrast());
        mScript.set_saturation(mParameters.getSaturation());

        mScript.invoke_setupGradParams();
        runSelectiveAdjust(getInPixelsAllocation(), getOutPixelsAllocation());

    }

    private void runSelectiveAdjust(Allocation in, Allocation out) {
        int width = in.getType().getX();
        int height = in.getType().getY();

        LaunchOptions options = new LaunchOptions();
        int ty;
        options.setX(0, width);

        for (ty = 0; ty < height; ty += STRIP_SIZE) {
            int endy = ty + STRIP_SIZE;
            if (endy > height) {
                endy = height;
            }
            options.setY(ty, endy);
            mScript.forEach_selectiveAdjust(in, out, options);
            if (checkStop()) {
                return;
            }
        }
    }

    private boolean checkStop() {
        RenderScript rsCtx = getRenderScriptContext();
        rsCtx.finish();
        if (getEnvironment().needsStop()) {
            return true;
        }
        return false;
    }
}

