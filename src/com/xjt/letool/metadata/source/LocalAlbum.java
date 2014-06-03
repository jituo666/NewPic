
package com.xjt.letool.metadata.source;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;

import com.xjt.letool.LetoolApp;
import com.xjt.letool.R;
import com.xjt.letool.common.LLog;
import com.xjt.letool.metadata.DataManager;
import com.xjt.letool.metadata.DataNotifier;
import com.xjt.letool.metadata.MediaItem;
import com.xjt.letool.metadata.MediaPath;
import com.xjt.letool.metadata.MediaSet;
import com.xjt.letool.metadata.MediaSetUtils;
import com.xjt.letool.metadata.image.LocalImage;
import com.xjt.letool.metadata.image.LocalMediaItem;
import com.xjt.letool.metadata.video.LocalVideo;
import com.xjt.letool.utils.LetoolUtils;
import com.xjt.letool.views.layout.ThumbnailExpandLayout.SortTag;
import com.xjt.letool.views.layout.ThumbnailExpandLayout.ThumbnailPos;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class LocalAlbum extends MediaSet {

    private static final String TAG = LocalAlbum.class.getSimpleName();
    private final String mWhereClause;
    private final String mOrderClause;
    private final Uri mBaseUri;
    private final String[] mProjection;

    private final LetoolApp mApplication;
    private final ContentResolver mResolver;
    private final long mBucketId;
    private final String mName;
    private final boolean mIsImage;
    private final DataNotifier mNotifier;
    private final String mItemPath;
    private Cursor mAlbumCursor;

    public LocalAlbum(MediaPath path, LetoolApp application, long bucketId, boolean isImage, String name) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mBucketId = bucketId;
        mName = getLocalizedName(application.getResources(), bucketId, name);
        mIsImage = isImage;

        if (isImage) {
            mWhereClause = ImageColumns.BUCKET_ID + " = ?";
            mOrderClause = ImageColumns.DATE_TAKEN + " DESC, "
                    + ImageColumns._ID + " DESC";
            mBaseUri = Images.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalImage.PROJECTION;
            mItemPath = LocalImage.ITEM_PATH;
        } else {
            mWhereClause = VideoColumns.BUCKET_ID + " = ?";
            mOrderClause = VideoColumns.DATE_TAKEN + " DESC, "
                    + VideoColumns._ID + " DESC";
            mBaseUri = Video.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalVideo.PROJECTION;
            mItemPath = LocalVideo.ITEM_PATH;
        }
        mNotifier = new DataNotifier(this, mBaseUri, application);
    }

    public LocalAlbum(MediaPath path, LetoolApp application, long bucketId,
            boolean isImage) {
        this(path, application, bucketId, isImage, LocalAlbumSet.getBucketName(application.getContentResolver(), bucketId));
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        long time = System.currentTimeMillis();
        ArrayList<MediaItem> list = new ArrayList<MediaItem>();
        LetoolUtils.assertNotInRenderThread();
        if (mAlbumCursor == null)
            return list;

        if (mAlbumCursor.moveToPosition(start)) {
            int i = 0;
            do {
                int id = mAlbumCursor.getInt(0);
                MediaPath childPath = new MediaPath(mItemPath, id);
                MediaItem item = loadOrUpdateItem(childPath, mAlbumCursor, mApplication.getDataManager(), mApplication, mIsImage);
                list.add(item);
            } while (++i < count && mAlbumCursor.moveToNext());
        }
        LLog.w(TAG, "query getMediaItem:" + count + " spend " + (System.currentTimeMillis() - time));
        return list;
    }

    private static MediaItem loadOrUpdateItem(MediaPath path, Cursor cursor, DataManager dataManager, LetoolApp app, boolean isImage) {
        LocalMediaItem item = (LocalMediaItem) path.getObject();
        if (item == null) {
            if (isImage) {
                item = new LocalImage(path, app, cursor);
            } else {
                item = new LocalVideo(path, app, cursor);
            }
        } else {
            item.updateContent(cursor);
        }
        return item;
    }

    @Override
    public int getMediaItemCount() {

        long time = System.currentTimeMillis();
        if (mAlbumCursor == null) {
            mAlbumCursor = mResolver.query(mBaseUri, mProjection, mWhereClause, new String[] {
                    String.valueOf(mBucketId)
            }, mOrderClause);
            if (mAlbumCursor == null) {
                return 0;
            }
        }
        LLog.i(TAG, "----------------getMediaItemCount:" + mAlbumCursor.getCount() + " spend " + (System.currentTimeMillis() - time));
        return mAlbumCursor.getCount();
    }

    @Override
    public ArrayList<SortTag> analysisSortTags() {
        long starttime = System.currentTimeMillis();
        ArrayList<SortTag> ret = new ArrayList<SortTag>();
        Cursor cursor = mResolver.query(mBaseUri, new String[] {
                ImageColumns.DATE_TAKEN, ImageColumns.BUCKET_ID,
                "count(" + ImageColumns.BUCKET_ID + ")"
        },
                mWhereClause + ") group by ("
                        + ImageColumns.DATE_TAKEN + "/86400000",
                new String[] {
                    String.valueOf(mBucketId)
                },
                ImageColumns.DATE_TAKEN + " DESC ");
        if (cursor == null) {
            LLog.w(TAG, "query fail: " + mBaseUri);
            return ret;
        }
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd E");
            int lastCount = 0;
            while (cursor.moveToNext()) {
                SortTag tag = new SortTag();
                tag.name = formatter.format(cursor.getLong(0));
                if (ret.size() > 0) {
                    tag.index = lastCount;
                } else {
                    tag.index = 0;
                }
                // Log.i(TAG, "xxxx:" + tag.name + ":::" + tag.index + ":" +
                // cursor.getInt(2));
                lastCount += cursor.getInt(2);
                ret.add(tag);
            }
        } finally {
            cursor.close();
        }
        LLog.i(TAG, "-------------get tags used time:" + (System.currentTimeMillis() - starttime) + ":" + ret.size());
        return ret;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            closeCursor();
        }
        return mDataVersion;
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_DELETE | SUPPORT_SHARE | SUPPORT_INFO;
    }

    @Override
    public void delete() {
        LetoolUtils.assertNotInRenderThread();
        mResolver.delete(mBaseUri, mWhereClause,
                new String[] {
                    String.valueOf(mBucketId)
                });
        mApplication.getDataManager().broadcastLocalDeletion();
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    private static String getLocalizedName(Resources res, long bucketId,
            String name) {
        if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            return res.getString(R.string.folder_download);
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            return res.getString(R.string.folder_imported);
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            return res.getString(R.string.folder_screenshot);
        } else {
            return name;
        }
    }

    @Override
    public void closeCursor() {
        if (mAlbumCursor != null) {
            try {
                mAlbumCursor.close();
            } finally {
                mAlbumCursor = null;
            }
        }
    }

    @Override
    public ArrayList<MediaPath> getMediaItem(ArrayList<ThumbnailPos> slotPos,int checkedCount) {
        ArrayList<MediaPath> list = new ArrayList<MediaPath>();
        LetoolUtils.assertNotInRenderThread();
        Cursor cursor = mResolver.query(mBaseUri, mProjection, mWhereClause,
                new String[] {
                    String.valueOf(mPath.getIdentity())
                },
                mOrderClause);
        if (cursor == null || cursor.getCount() != slotPos.size()) {
            return list;
        }
        try {
            int index = 0;
            while (cursor.moveToNext() && list.size() < checkedCount) {
                if (slotPos.get(index).isChecked) {
                    list.add(new MediaPath(mItemPath, cursor.getLong(LocalImage.INDEX_ID)));
                }
                index++;
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    public static Cursor getItemCursor(ContentResolver resolver, Uri uri, String[] projection, long id) {
        return resolver.query(uri, projection, "_id=?",
                new String[] {
                    String.valueOf(id)
                }, null);
    }
}
