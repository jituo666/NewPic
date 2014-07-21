package com.xjt.letool.imagedata.blobcache;

import android.graphics.Bitmap;

import com.xjt.letool.LetoolApp;
import com.xjt.letool.common.ThreadPool.JobContext;
import com.xjt.letool.imagedata.utils.BitmapUtils;
import com.xjt.letool.metadata.MediaItem;
import com.xjt.letool.metadata.MediaPath;

public class LocalVideoBlobRequest extends BlobCacheRequest {
    private String mLocalFilePath;

    public LocalVideoBlobRequest(LetoolApp application, MediaPath path, long timeModified, int type, String localFilePath) {
        super(application, path, timeModified, type, MediaItem.getTargetSize(type));
        mLocalFilePath = localFilePath;
    }

    @Override
    public Bitmap onDecodeOriginal(JobContext jc, int type) {
        Bitmap bitmap = BitmapUtils.createVideoThumbnail(mLocalFilePath);
        if (bitmap == null || jc.isCancelled())
            return null;
        return bitmap;
    }
}