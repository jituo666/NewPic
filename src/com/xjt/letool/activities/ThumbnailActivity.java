package com.xjt.letool.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.xjt.letool.R;
import com.xjt.letool.common.LLog;
import com.xjt.letool.fragment.PhotoFragment;

/**
 * @Author Jituo.Xuan
 * @Date 8:20:06 PM Apr 20, 2014
 * @Comments:null
 */
public class ThumbnailActivity extends BaseActivity {

    private static final String TAG = ThumbnailActivity.class.getSimpleName();

    public static final String KEY_ALBUM_TITLE = "album_title";
    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_ALBUM_ID = "album_id";
    public static final String KEY_IS_CAMERA = "is_camera";

    public static final int REQUEST_FOR_PHOTO = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.layout_main);
        super.onCreate(savedInstanceState);
        Fragment fragment = new PhotoFragment();
        Bundle data = new Bundle();
        String albumTitle = getIntent().getStringExtra(KEY_ALBUM_TITLE);
        long albumId = getIntent().getLongExtra(KEY_ALBUM_ID, 0);
        String albumMediaPath = getIntent().getStringExtra(KEY_MEDIA_PATH);
        data.putString(KEY_ALBUM_TITLE, albumTitle);
        data.putLong(KEY_ALBUM_ID, albumId);
        data.putString(KEY_MEDIA_PATH, albumMediaPath);
        data.putBoolean(KEY_IS_CAMERA, false);
        data.putIntArray(PhotoFragment.KEY_SET_CENTER, getIntent().getIntArrayExtra(PhotoFragment.KEY_SET_CENTER));
        fragment.setArguments(data);
        LLog.i(TAG, " start album id:" + albumId + " albumTitle:" + albumTitle + " albumMediaPath:" + albumMediaPath);
        mFragmentManager.beginTransaction().add(R.id.root_container, fragment, "PhotoFragment").commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
