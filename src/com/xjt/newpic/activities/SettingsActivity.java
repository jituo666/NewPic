
package com.xjt.newpic.activities;

import com.umeng.analytics.MobclickAgent;
import com.xjt.newpic.LetoolApp;
import com.xjt.newpic.LetoolContext;
import com.xjt.newpic.R;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.common.OrientationManager;
import com.xjt.newpic.common.ThreadPool;
import com.xjt.newpic.fragment.CameraSourceSettingFragment;
import com.xjt.newpic.fragment.SettingFragment;
import com.xjt.newpic.metadata.DataManager;
import com.xjt.newpic.view.GLController;
import com.xjt.newpic.view.LetoolBottomBar;
import com.xjt.newpic.view.LetoolSlidingMenu;
import com.xjt.newpic.view.LetoolTopBar;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/**
 * @Author Jituo.Xuan
 * @Date 8:16:31 PM Jul 24, 2014
 * @Comments:null
 */
public class SettingsActivity extends FragmentActivity implements LetoolContext {

    private static final String TAG = SettingsActivity.class.getSimpleName();

    public static final String KEY_SOURCE_TYPE = "source_type";
    public static final String KEY_FROM_TIP = "from_tip";

    private LetoolTopBar mTopBar;
    private LetoolSlidingMenu mSlidingMenu;
    private boolean mIsFromTip;

    private void startFirstFragment() {
        Fragment fragment = mIsFromTip ? new CameraSourceSettingFragment() : new SettingFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.local_image_browse_main_view, fragment);
        ft.commit();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.app_settings);
        mIsFromTip = getIntent().getBooleanExtra(KEY_FROM_TIP, false);
        mTopBar = new LetoolTopBar(this, (ViewGroup) findViewById(R.id.letool_top_bar_container));
        mSlidingMenu = new LetoolSlidingMenu(this, getSupportFragmentManager(), findViewById(R.id.letool_top_bar_container));
        startFirstFragment();
    }

    @Override
    protected void onResume() {
        MobclickAgent.onResume(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        MobclickAgent.onPause(this);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            popContentFragment();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_left_in, R.anim.slide_right_out);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public LetoolTopBar getLetoolTopBar() {
        return mTopBar;
    }

    @Override
    public LetoolBottomBar getLetoolBottomBar() {
        return null;
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
        return null;
    }

    @Override
    public boolean isImageBrwosing() {
        return mIsFromTip;
    }

    @Override
    public GLController getGLController() {
        return null;
    }

    @Override
    public void showEmptyView(int iconResIcon, int messageResId) {

    }

    @Override
    public void pushContentFragment(Fragment newFragment, Fragment oldFragment, boolean backup) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (oldFragment != null) {
            ft.remove(oldFragment);
            if (backup)
                ft.addToBackStack(null);
        }
        LLog.i(TAG, " add :" + newFragment.getClass().getSimpleName());
        ft.add(R.id.local_image_browse_main_view, newFragment, newFragment.getClass().getSimpleName());
        ft.commit();
    }

    @Override
    public void popContentFragment() {
        LLog.i(TAG, " popBackStack :" + getSupportFragmentManager().getBackStackEntryCount());
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            finish();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void hideEmptyView() {

    }

    @Override
    public View getGuidTipView() {
        return null;
    }

}