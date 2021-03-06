
package com.xjt.newpic.views;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nineoldandroids.animation.ObjectAnimator;
import com.umeng.analytics.MobclickAgent;
import com.xjt.newpic.R;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.fragment.SlidingMenuFragment;
import com.xjt.newpic.stat.StatConstants;

/**
 * @Author Jituo.Xuan
 * @Date 8:18:01 PM Jul 24, 2014
 * @Comments:null
 */
public class NpSlidingMenu {

    private static final String TAG = NpSlidingMenu.class.getSimpleName();

    private FragmentManager mFragmentManager;
    private Fragment mFragment;
    private Fragment mAlphaHolder;
    private View mMenuTip;
    private Context mContext;

    public NpSlidingMenu(Context context, FragmentManager fm, View menuTip) {
        mFragmentManager = fm;
        mMenuTip = menuTip;
        mContext = context;
        LLog.i(TAG, " mFragmentManager" + mFragmentManager);
    }

    public void toggle() {
        if (mFragmentManager.findFragmentByTag(SlidingMenuFragment.class.getSimpleName()) == null) {
            mFragment = new SlidingMenuFragment();
            mAlphaHolder = new AlphaFragment();
            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.setCustomAnimations(R.anim.alpha_sliding_menu_in, 0);
            ft.add(R.id.local_image_browse_main_view, mAlphaHolder, NpSlidingMenu.AlphaFragment.class.getSimpleName());
            ft.commit();
            FragmentTransaction ft1 = mFragmentManager.beginTransaction();
            ft1.setCustomAnimations(R.anim.slide_left_in, 0);
            ft1.add(R.id.local_image_browse_main_view, mFragment, SlidingMenuFragment.class.getSimpleName()).commit();
            playTipAinm(true);
        } else if (mFragment != null) {
            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.setCustomAnimations(0, R.anim.alpha_sliding_menu_out);
            ft.remove(mAlphaHolder);
            ft.setCustomAnimations(0, R.anim.slide_left_out);
            ft.remove(mFragment).commit();
            playTipAinm(false);
        }
    }

    private void playTipAinm(boolean expand) {
        if (mMenuTip == null)
            return;
        View tip = mMenuTip.findViewById(R.id.action_navi_tip);
        ObjectAnimator anim = null;
        if (expand) {
            anim = ObjectAnimator.ofFloat(tip, "x", 0, -tip.getWidth() / 3).setDuration(300);
        } else {
            anim = ObjectAnimator.ofFloat(tip, "x", -tip.getWidth() / 3, 0).setDuration(300);
        }
        anim.start();
    }

    public static class AlphaFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.sliding_menu_alpha_holder, container, false);
            return rootView;
        }

    }
}
