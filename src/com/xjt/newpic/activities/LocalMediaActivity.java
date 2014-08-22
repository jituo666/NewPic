
package com.xjt.newpic.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Toast;

import com.umeng.analytics.MobclickAgent;
import com.xjt.newpic.LetoolApp;
import com.xjt.newpic.LetoolContext;
import com.xjt.newpic.R;
import com.xjt.newpic.common.GlobalConstants;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.common.OrientationManager;
import com.xjt.newpic.common.ThreadPool;
import com.xjt.newpic.fragment.GalleryFragment;
import com.xjt.newpic.fragment.PhotoFragment;
import com.xjt.newpic.fragment.SlidingMenuFragment;
import com.xjt.newpic.fragment.VideoFragment;
import com.xjt.newpic.imagedata.utils.LetoolBitmapPool;
import com.xjt.newpic.metadata.DataManager;
import com.xjt.newpic.metadata.MediaItem;
import com.xjt.newpic.metadata.MediaSetUtils;
import com.xjt.newpic.preference.GlobalPreference;
import com.xjt.newpic.stat.StatConstants;
import com.xjt.newpic.view.GLController;
import com.xjt.newpic.view.GLRootView;
import com.xjt.newpic.view.LetoolBottomBar;
import com.xjt.newpic.view.LetoolEmptyView;
import com.xjt.newpic.view.LetoolSlidingMenu;
import com.xjt.newpic.view.LetoolTopBar;

/**
 * @Author Jituo.Xuan
 * @Date 8:16:18 PM Jul 24, 2014
 * @Comments:null
 */
public class LocalMediaActivity extends FragmentActivity implements LetoolContext {

    private static final String TAG = LocalMediaActivity.class.getSimpleName();

    public static final String KEY_ALBUM_TITLE = "album_title";
    public static final String KEY_MEDIA_PATH = "media_path";
    public static final String KEY_ALBUM_ID = "album_id";
    public static final String KEY_IS_CAMERA_SOURCE = "is_camera_source";
    public static final String KEY_IS_IMAGE = "is_image";

    public static final int REQUEST_CODE_SETTINGS = 100;

    private LetoolTopBar mTopBar;
    private LetoolBottomBar mBottomBar;
    private LetoolSlidingMenu mSlidingMenu;
    private ViewGroup mMainView;
    private GLRootView mGLESView;
    private Toast mExitToast;
    private OrientationManager mOrientationManager;
    private boolean mWaitingForExit = false;
    public boolean mIsImage = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.local_media_main_view);
        mTopBar = new LetoolTopBar(this, (ViewGroup) findViewById(R.id.letool_top_bar_container));
        mBottomBar = new LetoolBottomBar(this, (ViewGroup) findViewById(R.id.letool_bottom_bar_container));
        mSlidingMenu = new LetoolSlidingMenu(this, getSupportFragmentManager(), findViewById(R.id.letool_top_bar_container));
        mMainView = (ViewGroup) findViewById(R.id.local_image_browse_main_view);
        mGLESView = (GLRootView) mMainView.findViewById(R.id.gl_root_view);
        mOrientationManager = new OrientationManager(this);
        startFirstFragment();
    }

    private void startFirstFragment() {

        Fragment fragment = null;
        MediaSetUtils.initializeMyAlbumBuckets(this);
        if (!getIntent().hasExtra(KEY_IS_IMAGE) &&
                GlobalPreference.rememberLastUI(getActivityContext()) &&
                GlobalPreference.getLastUI(getActivityContext()).length() > 0) {
            String lastUI = GlobalPreference.getLastUI(getActivityContext());
            LLog.i(TAG, "----------remember last ui:" + lastUI);
            if (lastUI.equals(GlobalConstants.UI_TYPE_VIDEO_ITEMS)) {
                LLog.i(TAG, "----------remember last ui 1:" + lastUI);
                mIsImage = false;
                fragment = new VideoFragment();
                Bundle data = new Bundle();
                data.putString(LocalMediaActivity.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_LOCAL_VIDEO_ONLY));
                data.putBoolean(LocalMediaActivity.KEY_IS_CAMERA_SOURCE, true);
                fragment.setArguments(data);
            } else if (lastUI.equals(GlobalConstants.UI_TYPE_VIDEO_SETS)) {
                LLog.i(TAG, "----------remember last ui 2:" + lastUI);
                mIsImage = false;
                fragment = new GalleryFragment();
                Bundle data = new Bundle();
                data.putString(LocalMediaActivity.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_LOCAL_VIDEO_SET_ONLY));
                fragment.setArguments(data);
            } else if (lastUI.equals(GlobalConstants.UI_TYPE_IMAGE_SETS)) {
                LLog.i(TAG, "----------remember last ui 3:" + lastUI);
                mIsImage = true;
                fragment = new GalleryFragment();
                Bundle data = new Bundle();
                data.putString(LocalMediaActivity.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_LOCAL_IMAGE_SET_ONLY));
                fragment.setArguments(data);
            } else {
                LLog.i(TAG, "----------remember last ui 4:" + lastUI);
                mIsImage = true;
                fragment = new PhotoFragment();
                Bundle data = new Bundle();
                data.putString(LocalMediaActivity.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_LOCAL_IMAGE_ONLY));
                data.putBoolean(LocalMediaActivity.KEY_IS_CAMERA_SOURCE, true);
                fragment.setArguments(data);
            }
        } else {

            LLog.i(TAG, "----------remember last ui: 100");
            mIsImage = getIntent().getBooleanExtra(KEY_IS_IMAGE, true);
            if (MediaSetUtils.getBucketsIds().length <= 0) {
                fragment = new GalleryFragment();
                Bundle data = new Bundle();
                data.putString(LocalMediaActivity.KEY_MEDIA_PATH, getDataManager()
                        .getTopSetPath(isImageBrwosing() ? DataManager.INCLUDE_LOCAL_IMAGE_SET_ONLY : DataManager.INCLUDE_LOCAL_VIDEO_SET_ONLY));
                fragment.setArguments(data);
            } else {
                fragment = mIsImage ? new PhotoFragment() : new VideoFragment();
                Bundle data = new Bundle();
                data.putString(LocalMediaActivity.KEY_MEDIA_PATH, getDataManager()
                        .getTopSetPath(isImageBrwosing() ? DataManager.INCLUDE_LOCAL_IMAGE_ONLY : DataManager.INCLUDE_LOCAL_VIDEO_ONLY));
                data.putBoolean(LocalMediaActivity.KEY_IS_CAMERA_SOURCE, true);
                fragment.setArguments(data);

            }
            LLog.i(TAG, "----------remember last ui: 5");
        }

        pushContentFragment(fragment, null, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
        if (mGLESView.getVisibility() == View.VISIBLE)
            mGLESView.onResume();
        getDataManager().resume();
        mOrientationManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
        if (mGLESView.getVisibility() == View.VISIBLE)
            mGLESView.onPause();
        getDataManager().pause();
        mOrientationManager.pause();
        LetoolBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void showEmptyView(int iconResIcon, int messageResId) {
        LetoolEmptyView emptyView = (LetoolEmptyView) LayoutInflater.from(this).inflate(R.layout.local_media_empty_view, null);
        emptyView.updataView(iconResIcon, messageResId);
        //
        ViewGroup normalView = (ViewGroup) mMainView.findViewById(R.id.normal_root_view);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        normalView.removeAllViews();
        normalView.addView(emptyView, lp);
        normalView.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideEmptyView() {
        ViewGroup normalView = (ViewGroup) mMainView.findViewById(R.id.normal_root_view);
        normalView.removeAllViews();
        normalView.setVisibility(View.GONE);
    }

    public void pushContentFragment(Fragment newFragment, Fragment oldFragment, boolean backup) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (oldFragment != null) {
            ft.remove(oldFragment);
            if (backup)
                ft.addToBackStack(null);
        }
        LLog.i(TAG, " add :" + newFragment.getClass().getSimpleName());
        ft.add(newFragment, newFragment.getClass().getSimpleName());
        ft.commit();
    }

    public void popContentFragment() {
        LLog.i(TAG, " popBackStack :" + getSupportFragmentManager().getBackStackEntryCount());
        getSupportFragmentManager().popBackStack();
    }

    //

    @Override
    public void onBackPressed() {

        if (getLetoolTopBar().getActionBarMode() == LetoolTopBar.ACTION_BAR_MODE_SELECTION) {
            getLetoolTopBar().exitSelection();
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            popContentFragment();
        } else {
            Fragment f = getSupportFragmentManager().findFragmentByTag(SlidingMenuFragment.class.getSimpleName());
            if (f != null) {
                mSlidingMenu.toggle();
            } else {
                if (mWaitingForExit) {
                    if (mExitToast != null) {
                        mExitToast.cancel();
                    }
                    finish();
                } else {
                    mWaitingForExit = true;
                    mExitToast = Toast.makeText(this, R.string.common_exit_tip, Toast.LENGTH_SHORT);
                    mExitToast.show();
                    new Handler().postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            mWaitingForExit = false;
                        }
                    }, 3000);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Intent it = new Intent();
            it.setClass(this, LocalMediaActivity.class);
            it.putExtra(KEY_IS_IMAGE, mIsImage);
            startActivity(it);
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LLog.i(TAG, "onKeyDown menu1:" + keyCode);
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (getLetoolTopBar().getActionBarMode() == LetoolTopBar.ACTION_BAR_MODE_SELECTION) {
                return true;
            } else if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                MobclickAgent.onEvent(this, StatConstants.EVENT_KEY_HARD_MENU_MENU);
                mSlidingMenu.toggle();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public LetoolTopBar getLetoolTopBar() {
        return mTopBar;
    }

    @Override
    public LetoolBottomBar getLetoolBottomBar() {
        return mBottomBar;
    }

    @Override
    public LetoolSlidingMenu getLetoolSlidingMenu() {
        return mSlidingMenu;
    }

    @Override
    public DataManager getDataManager() {
        return ((LetoolApp) getApplication()).getDataManager();
    }

    @Override
    public Context getActivityContext() {
        return this;
    }

    @Override
    public ThreadPool getThreadPool() {
        return ((LetoolApp) getApplication()).getThreadPool();
    }

    @Override
    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    public GLController getGLController() {
        return mGLESView;
    }

    @Override
    public boolean isImageBrwosing() {
        return mIsImage;
    }

    @Override
    public View getGuidTipView() {
        return findViewById(R.id.function_guide_tip);
    }

}