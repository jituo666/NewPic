
package com.xjt.letool.views.render;


import com.xjt.letool.LetoolContext;
import com.xjt.letool.adapters.ThumbnailVideoDataWindow;
import com.xjt.letool.adapters.ThumbnailVideoDataWindow.VideoEntry;
import com.xjt.letool.metadata.MediaPath;
import com.xjt.letool.metadata.loader.ThumbnailDataLoader;
import com.xjt.letool.selectors.ContractSelector;
import com.xjt.letool.view.ThumbnailView;
import com.xjt.letool.views.opengl.ColorTexture;
import com.xjt.letool.views.opengl.GLESCanvas;
import com.xjt.letool.views.opengl.Texture;
import com.xjt.letool.views.opengl.UploadedBitmapTexture;
import com.xjt.letool.views.utils.AlbumLabelMaker;
import com.xjt.letool.views.utils.ViewConfigs;

public class ThumbnailVideoRenderer extends AbstractThumbnailRender {

    private static final String TAG = ThumbnailVideoRenderer.class.getSimpleName();

    private static final int CACHE_SIZE = 48;
    private final int mPlaceholderColor;

    private final ColorTexture mWaitLoadingTexture;
    private LetoolContext mActivity;

    private ThumbnailView mThumbnailView;
    private ThumbnailVideoDataWindow mDataWindow;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private MediaPath mHighlightItemPath = null;
    private boolean mInSelectionMode;
    private ContractSelector mMediaSelector;

    private LabelSpec mLabelSpec;

    public static class LabelSpec {

        public int labelHeight;
        public int titleOffset;
        public int countOffset;
        public int titleFontSize;
        public int countFontSize;
        public int leftMargin;
        public int iconSize;
        public int titleRightMargin;
        public int backgroundColor;
        public int titleColor;
        public int countColor;
        public int borderSize;
    }

    private class MyCacheListener implements ThumbnailVideoDataWindow.Listener {

        @Override
        public void onSizeChanged(int size) {
            mThumbnailView.setThumbnailCount(size);
        }

        @Override
        public void onContentChanged() {
            mThumbnailView.invalidate();
        }
    }

    public ThumbnailVideoRenderer(LetoolContext activity, ThumbnailView thumbnailView, ContractSelector selector) {
        super(activity.getAppContext());
        mActivity = activity;
        mThumbnailView = thumbnailView;
        mPlaceholderColor = 0xFFE8E8E8;
        mMediaSelector = selector;
        mLabelSpec = ViewConfigs.VideoPage.get(activity.getAppContext()).labelSpec;

        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);
    }

    public void setModel(ThumbnailDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mDataWindow = null;
            mThumbnailView.setThumbnailCount(0);
        }
        if (model != null) {
            mDataWindow = new ThumbnailVideoDataWindow(mActivity, model, mLabelSpec, CACHE_SIZE);
            mDataWindow.setListener(new MyCacheListener());
            mThumbnailView.setThumbnailCount(mDataWindow.size());
        }
    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index)
            return;
        mPressedIndex = index;
        mThumbnailView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1)
            return;
        mAnimatePressedUp = true;
        mThumbnailView.invalidate();
    }

    public void setHighlightItemPath(MediaPath path) {
        if (mHighlightItemPath == path)
            return;
        mHighlightItemPath = path;
        mThumbnailView.invalidate();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static Texture checkLabelTexture(Texture texture) {
        return ((texture instanceof UploadedBitmapTexture) && ((UploadedBitmapTexture) texture).isUploading()) ? null : texture;
    }


    @Override
    public int renderThumbnail(GLESCanvas canvas, int index, int pass, int width, int height) {
        VideoEntry entry = mDataWindow.get(index);
        int renderRequestFlags = 0;
        renderRequestFlags |= renderContent(canvas, entry, width, height - mLabelSpec.labelHeight);
        renderRequestFlags |= renderLabel(canvas, entry, width, height);
        renderRequestFlags |= renderOverlay(canvas, entry, index, width, height - mLabelSpec.labelHeight);
        return renderRequestFlags;
    }

    protected int renderContent(GLESCanvas canvas, VideoEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        Texture content = entry.bitmapTexture;
        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitLoadingDisplayed = true;
        }
        drawContent(canvas, content, width, height, entry.rotation);
        drawVideoOverlay(canvas, width, height);
        return renderRequestFlags;
    }

    protected int renderLabel(GLESCanvas canvas, VideoEntry entry, int width, int height) {
        Texture content = checkLabelTexture(entry.labelTexture);
        if (content == null) {
            content = mWaitLoadingTexture;
            return 0;
        }
        int b = AlbumLabelMaker.getBorderSize();
        int h = mLabelSpec.labelHeight;
        content.draw(canvas, -b, height - h + b, width + b + b, h);
        return 0;
    }

    protected int renderOverlay(GLESCanvas canvas, VideoEntry entry, int index, int width, int height) {
        int renderRequestFlags = 0;

        if (mPressedIndex == index) {
            if (mAnimatePressedUp) {
                drawPressedUpFrame(canvas, width, height);
                renderRequestFlags |= ThumbnailView.RENDER_MORE_FRAME;
                if (isPressedUpFrameFinished()) {
                    mAnimatePressedUp = false;
                    mPressedIndex = -1;
                }
            } else {
                drawPressedFrame(canvas, width, height);
            }
        } else if ((mHighlightItemPath != null)) {
            drawSelectedFrame(canvas, width, height);
        } else if (mInSelectionMode) {
            if (mMediaSelector.isItemSelected(entry.setPath)) {
                drawSelectedFrame(canvas, width, height);
            } else {
                drawPreSelectedFrame(canvas, width, height);
            }
        }
        return renderRequestFlags;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void prepareDrawing() {
        mInSelectionMode = mMediaSelector.inSelectionMode();
    }

    public void pause() {
        mDataWindow.pause();
    }

    public void resume() {
        mDataWindow.resume();
    }

    @Override
    public void onVisibleThumbnailRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
        }
    }

    @Override
    public void onThumbnailSizeChanged(int width, int height) {
        if (mDataWindow != null) {
            mDataWindow.onThumbnailSizeChanged(width, height);
        }
    }

}