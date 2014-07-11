
package com.xjt.letool.metadata.loader;

import android.os.Handler;
import android.os.Message;
import android.os.Process;

import com.xjt.letool.LetoolContext;
import com.xjt.letool.metadata.ContentListener;
import com.xjt.letool.metadata.MediaItem;
import com.xjt.letool.metadata.MediaObject;
import com.xjt.letool.metadata.MediaPath;
import com.xjt.letool.metadata.MediaSet;
import com.xjt.letool.utils.Utils;
import com.xjt.letool.common.LLog;
import com.xjt.letool.common.SynchronizedHandler;
import com.xjt.letool.fragment.LetoolFragment;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class ThumbnailSetDataLoader {

    private static final String TAG = ThumbnailSetDataLoader.class.getSimpleName();

    private static final int DATA_CACHE_SIZE = 256;

    private static final int INDEX_NONE = -1;
    private static final int MIN_LOAD_COUNT = 4;

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;

    public static interface DataListener {

        public void onContentChanged(int index);

        public void onSizeChanged(int size);
    }

    private final MediaSet[] mData;
    private final MediaItem[] mCoverItem;
    private final int[] mTotalCount;
    private final long[] mItemVersion;
    private final long[] mSetVersion;

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private final MediaSet mSource;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;
    private int mSize;

    private DataListener mDataListener;
    private DataLoadingListener mLoadingListener;
    private ReloadTask mReloadTask;

    private final Handler mMainHandler;

    private final MySourceListener mSourceListener = new MySourceListener();

    public ThumbnailSetDataLoader(LetoolContext activity, MediaSet albumSet) {
        mSource = Utils.checkNotNull(albumSet);
        mCoverItem = new MediaItem[DATA_CACHE_SIZE];
        mData = new MediaSet[DATA_CACHE_SIZE];
        mTotalCount = new int[DATA_CACHE_SIZE];
        mItemVersion = new long[DATA_CACHE_SIZE];
        mSetVersion = new long[DATA_CACHE_SIZE];
        Arrays.fill(mItemVersion, MediaObject.INVALID_DATA_VERSION);
        Arrays.fill(mSetVersion, MediaObject.INVALID_DATA_VERSION);

        mMainHandler = new SynchronizedHandler(activity.getGLController()) {

            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RUN_OBJECT:
                        ((Runnable) message.obj).run();
                        return;
                    case MSG_LOAD_START:
                        if (mLoadingListener != null)
                            mLoadingListener.onLoadingStarted();
                        return;
                    case MSG_LOAD_FINISH:
                        if (mLoadingListener != null)
                            mLoadingListener.onLoadingFinished(false);
                        return;
                }
            }
        };
    }

    public void pause() {
        mReloadTask.terminate();
        mReloadTask = null;
        mSource.removeContentListener(mSourceListener);
    }

    public void resume() {
        mSource.addContentListener(mSourceListener);
        mReloadTask = new ReloadTask();
        mReloadTask.start();
    }

    private void assertIsActive(int index) {
        if (index < mActiveStart && index >= mActiveEnd) {
            throw new IllegalArgumentException(String.format("%s not in (%s, %s)", index, mActiveStart, mActiveEnd));
        }
    }

    public MediaSet getMediaSet(int index) {
        assertIsActive(index);
        return mData[index % mData.length];
    }

    public MediaItem getCoverItem(int index) {
        assertIsActive(index);
        return mCoverItem[index % mCoverItem.length];
    }

    public int getTotalCount(int index) {
        assertIsActive(index);
        return mTotalCount[index % mTotalCount.length];
    }

    public int getActiveStart() {
        return mActiveStart;
    }

    public boolean isActive(int index) {
        return index >= mActiveStart && index < mActiveEnd;
    }

    public int size() {
        return mSize;
    }

    // Returns the index of the MediaSet with the given path or -1 if the path is not cached
    public int findSet(MediaPath id) {
        int length = mData.length;
        for (int i = mContentStart; i < mContentEnd; i++) {
            MediaSet set = mData[i % length];
            if (set != null && id == set.getPath()) {
                return i;
            }
        }
        return -1;
    }

    private void clearThumbnail(int thumbnailIndex) {
        mData[thumbnailIndex] = null;
        mCoverItem[thumbnailIndex] = null;
        mTotalCount[thumbnailIndex] = 0;
        mItemVersion[thumbnailIndex] = MediaObject.INVALID_DATA_VERSION;
        mSetVersion[thumbnailIndex] = MediaObject.INVALID_DATA_VERSION;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd)
            return;
        int length = mCoverItem.length;

        int start = this.mContentStart;
        int end = this.mContentEnd;

        mContentStart = contentStart;
        mContentEnd = contentEnd;

        if (contentStart >= end || start >= contentEnd) {
            for (int i = start, n = end; i < n; ++i) {
                clearThumbnail(i % length);
            }
        } else {
            for (int i = start; i < contentStart; ++i) {
                clearThumbnail(i % length);
            }
            for (int i = contentEnd, n = end; i < n; ++i) {
                clearThumbnail(i % length);
            }
        }
        mReloadTask.notifyDirty();
    }

    public void setActiveWindow(int start, int end) {
        if (start == mActiveStart && end == mActiveEnd)
            return;

        Utils.assertTrue(start <= end && end - start <= mCoverItem.length && end <= mSize);

        mActiveStart = start;
        mActiveEnd = end;

        int length = mCoverItem.length;
        // If no data is visible, keep the cache content
        if (start == end)
            return;

        int contentStart = Utils.clamp((start + end) / 2 - length / 2, 0, Math.max(0, mSize - length));
        int contentEnd = Math.min(contentStart + length, mSize);
        if (mContentStart > start || mContentEnd < end || Math.abs(contentStart - mContentStart) > MIN_LOAD_COUNT) {
            setContentWindow(contentStart, contentEnd);
        }
    }

    private class MySourceListener implements ContentListener {

        @Override
        public void onContentDirty() {
            mReloadTask.notifyDirty();
        }
    }

    public void setModelListener(DataListener listener) {
        mDataListener = listener;
    }

    public void setLoadingListener(DataLoadingListener listener) {
        mLoadingListener = listener;
    }

    private static class UpdateInfo {

        public long version;
        public int index;
        public int size;
        public MediaSet item;
        public MediaItem cover;
        public int totalCount;
    }

    private class GetUpdateInfo implements Callable<UpdateInfo> {

        private final long mVersion;

        public GetUpdateInfo(long version) {
            mVersion = version;
        }

        private int getInvalidIndex(long version) {
            long setVersion[] = mSetVersion;
            int length = setVersion.length;
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                int index = i % length;
                if (setVersion[index] != version)
                    return i;
            }
            return INDEX_NONE;
        }

        @Override
        public UpdateInfo call() throws Exception {
            int index = getInvalidIndex(mVersion);
            if (index == INDEX_NONE && mSourceVersion == mVersion)
                return null;
            UpdateInfo info = new UpdateInfo();
            info.version = mSourceVersion;
            info.index = index;
            info.size = mSize;
            return info;
        }
    }

    private class UpdateContent implements Callable<Void> {

        private final UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo info) {
            mUpdateInfo = info;
        }

        @Override
        public Void call() {
            // Avoid notifying listeners of status change after pause Otherwise gallery will be in inconsistent state after resume.
            if (mReloadTask == null)
                return null;
            UpdateInfo info = mUpdateInfo;
            mSourceVersion = info.version;
            if (mSize != info.size) {
                mSize = info.size;
                if (mDataListener != null)
                    mDataListener.onSizeChanged(mSize);
                if (mContentEnd > mSize)
                    mContentEnd = mSize;
                if (mActiveEnd > mSize)
                    mActiveEnd = mSize;
            }
            // Note: info.index could be INDEX_NONE, i.e., -1
            if (info.index >= mContentStart && info.index < mContentEnd) {
                int pos = info.index % mCoverItem.length;
                mSetVersion[pos] = info.version;
                long itemVersion = info.item.getDataVersion();
                if (mItemVersion[pos] == itemVersion)
                    return null;
                mItemVersion[pos] = itemVersion;
                mData[pos] = info.item;
                mCoverItem[pos] = info.cover;
                mTotalCount[pos] = info.totalCount;
                if (mDataListener != null && info.index >= mActiveStart && info.index < mActiveEnd) {
                    mDataListener.onContentChanged(info.index);
                }
            }
            return null;
        }
    }

    private <T> T executeAndWait(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<T>(callable);
        mMainHandler.sendMessage(
                mMainHandler.obtainMessage(MSG_RUN_OBJECT, task));
        try {
            return task.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private class ReloadTask extends Thread {

        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;
        private volatile boolean mIsLoading = false;

        private void updateLoading(boolean loading) {
            if (mIsLoading == loading)
                return;
            mIsLoading = loading;
            mMainHandler.sendEmptyMessage(loading ? MSG_LOAD_START : MSG_LOAD_FINISH);
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boolean updateComplete = false;
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty && updateComplete) {
                        if (!mSource.isLoading())
                            updateLoading(false);
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;
                updateLoading(true);
                long version = mSource.reload();
                UpdateInfo info = executeAndWait(new GetUpdateInfo(version));
                updateComplete = info == null;
                if (updateComplete)
                    continue;
                if (info.version != version) {
                    info.version = version;
                    info.size = mSource.getSubMediaSetCount();
                    // If the size becomes smaller after reload(), we may
                    // receive from GetUpdateInfo an index which is too
                    // big. Because the main thread is not aware of the size
                    // change until we call UpdateContent.
                    if (info.index >= info.size) {
                        info.index = INDEX_NONE;
                    }
                }
                if (info.index != INDEX_NONE) {
                    info.item = mSource.getSubMediaSet(info.index);
                    if (info.item == null)
                        continue;
                    info.totalCount = info.item.getTotalMediaItemCount();
                    info.cover = info.item.getCoverMediaItem();
                }
                executeAndWait(new UpdateContent(info));
            }
            updateLoading(false);
        }

        public synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }
}
