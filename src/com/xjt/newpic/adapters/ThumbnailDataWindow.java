
package com.xjt.newpic.adapters;

import android.graphics.Bitmap;
import android.os.Message;

import com.xjt.newpic.NpContext;
import com.xjt.newpic.common.Future;
import com.xjt.newpic.common.FutureListener;
import com.xjt.newpic.common.JobLimiter;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.common.SynchronizedHandler;
import com.xjt.newpic.imagedata.utils.BitmapLoader;
import com.xjt.newpic.metadata.MediaItem;
import com.xjt.newpic.metadata.MediaPath;
import com.xjt.newpic.metadata.loader.ThumbnailDataLoader;
import com.xjt.newpic.utils.Utils;
import com.xjt.newpic.views.opengl.BitmapTexture;
import com.xjt.newpic.views.opengl.TiledTexture;


/**
 * control the data window ,[activate range:media data], [content range: meta
 * data] 1,cache image 2,compute meta data cache range
 * 
 * @author jetoo
 * 
 */
public class ThumbnailDataWindow implements ThumbnailDataLoader.DataChangedListener {

    private static final String TAG = ThumbnailDataWindow.class.getSimpleName();

    private static final int MSG_UPDATE_ENTRY = 0;
    private static final int JOB_LIMIT = 1;
    private static final int CACHE_SIZE = 48;

    private int mActiveRequestCount = 0;
    private boolean mIsActive = false;

    private final ThumbnailDataLoader mDataSource;
    private DataListener mDataListener;
    private final AlbumEntry mImageData[];
    private final SynchronizedHandler mHandler;
    private final JobLimiter mThreadPool;
    private int mSize;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    public static interface DataListener {

        public void onSizeChanged(int size);

        public void onContentChanged();
    }

    public static class AlbumEntry {

        public MediaItem item;
        public MediaPath path;
        public int rotation;
        public int mediaType;
        public boolean isWaitDisplayed;
        public BitmapTexture bitmapTexture;
        private BitmapLoader contentLoader;
    }

    public ThumbnailDataWindow(NpContext fragment, ThumbnailDataLoader source) {
        source.setDataChangedListener(this);
        mDataSource = source;
        mImageData = new AlbumEntry[CACHE_SIZE];
        mSize = source.size();
        mHandler = new SynchronizedHandler(fragment.getGLController()) {

            @Override
            public void handleMessage(Message message) {
                if (message.what == MSG_UPDATE_ENTRY) {
                    ((ThumbnailLoader) message.obj).updateEntry();
                }
            }
        };
        mThreadPool = new JobLimiter(fragment.getThreadPool(), JOB_LIMIT);

    }

    public void setListener(DataListener listener) {
        mDataListener = listener;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd)
            return;

        if (!mIsActive) {
            mContentStart = contentStart;
            mContentEnd = contentEnd;
            mDataSource.setActiveWindow(contentStart, contentEnd);
            return;
        }

        if (contentStart >= mContentEnd || mContentStart >= contentEnd) {
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                freeThumbnailContent(i);
            }
            mDataSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart; i < contentEnd; ++i) {
                prepareThumbnailContent(i);
            }
        } else {
            for (int i = mContentStart; i < contentStart; ++i) {
                freeThumbnailContent(i);
            }
            for (int i = contentEnd, n = mContentEnd; i < n; ++i) {
                freeThumbnailContent(i);
            }
            mDataSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart, n = mContentStart; i < n; ++i) {
                prepareThumbnailContent(i);
            }
            for (int i = mContentEnd; i < contentEnd; ++i) {
                prepareThumbnailContent(i);
            }
        }

        mContentStart = contentStart;
        mContentEnd = contentEnd;
    }

    /**
     * 1,set image or video cache range. 2,set image's/video's meta data range.
     * 1,|_____1/2 data length_____|__________________move by step__________________|______data length_________|
     */
    public void setActiveWindow(int start, int end) {
        if (!(start <= end && end - start <= mImageData.length && end <= mSize)) {
            Utils.fail("%s, %s, %s, %s", start, end, mImageData.length, mSize);
        }
        AlbumEntry data[] = mImageData;

        mActiveStart = start;
        mActiveEnd = end;

        int contentStart = Utils.clamp((start + end) / 2 - data.length / 2, 0, Math.max(0, mSize - data.length));
        int contentEnd = Math.min(contentStart + data.length, mSize);
        setContentWindow(contentStart, contentEnd);

        if (mIsActive)
            updateAllImageRequests();
    }

    public AlbumEntry get(int slotIndex) {
        if (!isActiveThumbnail(slotIndex)) {
            Utils.fail("invalid slot: %s outsides (%s, %s)", slotIndex, mActiveStart, mActiveEnd);
        }
        return mImageData[slotIndex % mImageData.length];
    }

    public boolean isActiveThumbnail(int slotIndex) {
        return slotIndex >= mActiveStart && slotIndex < mActiveEnd;
    }

    // We would like to request non active slots in the following order:
    // Order:    8 6 4 2                   1 3 5 7
    //         |---------|---------------|---------|
    //                   |<-  active  ->|
    //         |<-------- cached range ----------->|
    private void requestNonactiveImages() {
        int range = Math.max((mContentEnd - mActiveEnd), (mActiveStart - mContentStart));
        for (int i = 0; i < range; ++i) {
            requestThumbnailImage(mActiveEnd + i);
            requestThumbnailImage(mActiveStart - 1 - i);
        }
    }

    private void cancelNonactiveImages() {
        int range = Math.max((mContentEnd - mActiveEnd), (mActiveStart - mContentStart));
        for (int i = 0; i < range; ++i) {
            cancelThumbnailImage(mActiveEnd + i);
            cancelThumbnailImage(mActiveStart - 1 - i);
        }
    }

    // return whether the request is in progress or not
    private boolean requestThumbnailImage(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd)
            return false;
        AlbumEntry entry = mImageData[slotIndex % mImageData.length];
        if (entry.bitmapTexture != null || entry.item == null)
            return false;
        entry.contentLoader.startLoad();
        return entry.contentLoader.isRequestInProgress();
    }

    private void cancelThumbnailImage(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd)
            return;
        AlbumEntry item = mImageData[slotIndex % mImageData.length];
        if (item.contentLoader != null)
            item.contentLoader.cancelLoad();
    }

    private void prepareThumbnailContent(int slotIndex) {
        AlbumEntry entry = new AlbumEntry();
        MediaItem item = mDataSource.get(slotIndex); // item could be null;
        entry.item = item;
        entry.mediaType = (item == null) ? MediaItem.MEDIA_TYPE_UNKNOWN : entry.item.getMediaType();
        entry.path = (item == null) ? null : item.getPath();
        entry.rotation = (item == null) ? 0 : item.getRotation();
        entry.contentLoader = new ThumbnailLoader(slotIndex, entry.item);
        mImageData[slotIndex % mImageData.length] = entry;
    }

    private void freeThumbnailContent(int slotIndex) {
        AlbumEntry data[] = mImageData;
        int index = slotIndex % data.length;
        AlbumEntry entry = data[index];
        if (entry.contentLoader != null)
            entry.contentLoader.recycle();
        if (entry.bitmapTexture != null)
            entry.bitmapTexture.recycle();
        data[index] = null;
    }

    private void updateAllImageRequests() {
        mActiveRequestCount = 0;
        for (int i = mActiveStart, n = mActiveEnd; i < n; ++i) {
            if (requestThumbnailImage(i))
                ++mActiveRequestCount;
        }
        if (mActiveRequestCount == 0) {
            requestNonactiveImages();
        } else {
            cancelNonactiveImages();
        }
    }

    public void resume() {
        LLog.i(TAG, " resume1:" + System.currentTimeMillis());
        mIsActive = true;
        TiledTexture.prepareResources();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            prepareThumbnailContent(i);
        }
        LLog.i(TAG, " resume2:" + System.currentTimeMillis());
        updateAllImageRequests(); // Frist start no use, just for backing from other
    }

    public void pause() {
        mIsActive = false;
        TiledTexture.freeResources();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            freeThumbnailContent(i);
        }
    }

    @Override
    public void onContentChanged(int index) {
        if (index >= mContentStart && index < mContentEnd && mIsActive) {

            //LLog.i(TAG, "onContentChanged:" + index + " :" + System.currentTimeMillis());
            freeThumbnailContent(index);
            prepareThumbnailContent(index);
            updateAllImageRequests();
            if (mDataListener != null && isActiveThumbnail(index)) {
                mDataListener.onContentChanged();
            }
        }
    }

    @Override
    public void onSizeChanged(int size) {
        if (mSize != size) {
            //LLog.i(TAG, "onSizeChanged:" + size + " :" + System.currentTimeMillis());
            mSize = size;
            if (mDataListener != null) {
                mDataListener.onSizeChanged(mSize);
            }
            if (mContentEnd > mSize)
                mContentEnd = mSize;
            if (mActiveEnd > mSize)
                mActiveEnd = mSize;
        }
    }

    private class ThumbnailLoader extends BitmapLoader {

        private final int mThumbnailIndex;

        private final MediaItem mItem;

        public ThumbnailLoader(int slotIndex, MediaItem item) {
            mThumbnailIndex = slotIndex;
            mItem = item;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return mThreadPool.submit(mItem.requestImage(MediaItem.TYPE_MICROTHUMBNAIL), this);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ENTRY, this).sendToTarget();
        }

        public void updateEntry() {
            Bitmap bitmap = getBitmap();
            if (bitmap == null)
                return; // error or recycled

            AlbumEntry entry = mImageData[mThumbnailIndex % mImageData.length];
            entry.bitmapTexture = new BitmapTexture(bitmap);
            if (isActiveThumbnail(mThumbnailIndex)) {

                --mActiveRequestCount;
                if (mActiveRequestCount == 0)
                    requestNonactiveImages();
                if (mDataListener != null) {
                    mDataListener.onContentChanged();
                }
            } else {

            }
        }
    }
}
