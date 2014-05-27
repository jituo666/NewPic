
package com.xjt.letool.fragment;

import com.xjt.letool.LetoolApp;
import com.xjt.letool.R;
import com.xjt.letool.activities.FullImageActivity;
import com.xjt.letool.activities.ThumbnailActivity;
import com.xjt.letool.common.EyePosition;
import com.xjt.letool.common.Future;
import com.xjt.letool.common.LLog;
import com.xjt.letool.common.SynchronizedHandler;
import com.xjt.letool.metadata.DataManager;
import com.xjt.letool.metadata.MediaDetails;
import com.xjt.letool.metadata.MediaItem;
import com.xjt.letool.metadata.MediaObject;
import com.xjt.letool.metadata.MediaPath;
import com.xjt.letool.metadata.MediaSet;
import com.xjt.letool.metadata.MediaSetUtils;
import com.xjt.letool.metadata.loader.DataLoadingListener;
import com.xjt.letool.metadata.loader.ThumbnailDataLoader;
import com.xjt.letool.metadata.source.PhotoAlbum;
import com.xjt.letool.selectors.SelectionListener;
import com.xjt.letool.selectors.SelectionManager;
import com.xjt.letool.surpport.MenuItem;
import com.xjt.letool.surpport.PopupMenu;
import com.xjt.letool.surpport.PopupMenu.OnMenuItemClickListener;
import com.xjt.letool.utils.LetoolUtils;
import com.xjt.letool.utils.RelativePosition;
import com.xjt.letool.utils.StorageUtils;
import com.xjt.letool.utils.Utils;
import com.xjt.letool.view.CommonLoadingPanel;
import com.xjt.letool.view.BatchDeleteMediaListener;
import com.xjt.letool.view.BatchDeleteMediaListener.DeleteMediaProgressListener;
import com.xjt.letool.view.DetailsHelper;
import com.xjt.letool.view.GLBaseView;
import com.xjt.letool.view.GLController;
import com.xjt.letool.view.GLRootView;
import com.xjt.letool.view.LetoolActionBar;
import com.xjt.letool.view.ThumbnailView;
import com.xjt.letool.view.DetailsHelper.CloseListener;
import com.xjt.letool.views.layout.ThumbnailContractLayout;
import com.xjt.letool.views.layout.ThumbnailLayout;
import com.xjt.letool.views.layout.ThumbnailLayout.LayoutListener;
import com.xjt.letool.views.opengl.FadeTexture;
import com.xjt.letool.views.opengl.GLESCanvas;
import com.xjt.letool.views.render.ThumbnailRenderer;
import com.xjt.letool.views.utils.ViewConfigs;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @Author Jituo.Xuan
 * @Date 9:48:35 AM Apr 19, 2014
 * @Comments:null
 */
public class PhotoFragment extends LetoolFragment implements EyePosition.EyePositionListener, SelectionListener,
        LayoutListener, OnMenuItemClickListener {

    private static final String TAG = PhotoFragment.class.getSimpleName();

    public static final String KEY_SET_CENTER = "set-center";
    public static final String KEY_RESUME_ANIMATION = "resume_animation";
    private static final int BIT_LOADING_RELOAD = 1;
    private static final int BIT_LOADING_SYNC = 2;
    private static final int MSG_LAYOUT_CONFIRMED = 0;
    private static final int MSG_PICK_PHOTO = 1;

    //photo data
    private MediaPath mDataSetPath;
    private MediaSet mDataSet;
    private DetailsHelper mDetailsHelper;
    private MyDetailsSource mDetailsSource;
    private boolean mShowDetails;
    private ThumbnailDataLoader mAlbumDataSetLoader;
    private boolean mLoadingFailed;
    private int mLoadingBits = 0;
    private Future<Integer> mSyncTask = null; // synchronize data
    private boolean mInitialSynced = false;

    //views
    private CommonLoadingPanel mLoadingInsie;
    private GLRootView mGLRootView;
    private View mMore;
    private ViewConfigs.AlbumPage mConfig;
    private ThumbnailView mThumbnailView;
    private ThumbnailRenderer mRender;
    private RelativePosition mOpenCenter = new RelativePosition();
    private boolean mIsActive = false;

    private String mAlbumTitle;
    private boolean mIsCamera = false;
    private boolean mHasSDCard = false;
    private boolean mGetContent;
    private SynchronizedHandler mHandler;
    protected SelectionManager mSelector;
    private EyePosition mEyePosition; // The eyes' position of the user, the origin is at the center of the device and the unit is in pixels.
    private float mUserDistance; // in pixel
    private float mX;
    private float mY;
    private float mZ;

    private final GLBaseView mRootPane = new GLBaseView() {

        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            mEyePosition.resetPosition();
            LetoolActionBar actionBar = getLetoolActionBar();
            int thumbnailViewLeft = left + mConfig.paddingLeft;
            int thumbnailViewRight = right - left - mConfig.paddingRight;
            int thumbnailViewTop = top + mConfig.paddingTop + actionBar.getHeight();
            int thumbnailViewBottom = bottom - top - mConfig.paddingBottom;

            if (mShowDetails) {
                mDetailsHelper.layout(thumbnailViewLeft, thumbnailViewTop, right, bottom);
            } else {
                mRender.setHighlightItemPath(null);
            }
            // Set the mThumbnailView as a reference point to the open animation
            mOpenCenter.setReferencePosition(0, thumbnailViewTop);
            mThumbnailView.layout(thumbnailViewLeft, thumbnailViewTop, thumbnailViewRight, thumbnailViewBottom);
            LetoolUtils.setViewPointMatrix(mMatrix, (right - left) / 2, (bottom - top) / 2, -mUserDistance);
        }

        @Override
        protected void render(GLESCanvas canvas) {
            canvas.save(GLESCanvas.SAVE_FLAG_MATRIX);
            LetoolUtils.setViewPointMatrix(mMatrix, getWidth() / 2 + mX, getHeight() / 2 + mY, mZ);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            canvas.restore();
        }
    };

    private class MetaDataLoadingListener implements DataLoadingListener {

        @Override
        public void onLoadingStarted() {
            mLoadingFailed = false;
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadFailed) {
            mLoadingFailed = loadFailed;
            clearLoadingBit(BIT_LOADING_RELOAD);
        }
    }

    private void setLoadingBit(int loadTaskBit) {
        mLoadingBits |= loadTaskBit;
    }

    private void clearLoadingBit(int loadTaskBit) {
        mLoadingBits &= ~loadTaskBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mAlbumDataSetLoader.size() == 0) {
                Toast.makeText(getAndroidContext(), R.string.empty_album, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onLayoutFinshed(int count) {
        mHandler.obtainMessage(MSG_LAYOUT_CONFIRMED, count, 0).sendToTarget();
    }

    private void onDown(int index) {
        mRender.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            mRender.setPressedIndex(-1); // Avoid showing press-up animations for long-press.
        } else {
            mRender.setPressedUp();
        }
    }

    private void onSingleTapUp(int thumbnailIndex) {
        if (!mIsActive)
            return;

        if (mSelector.inSelectionMode()) {
            MediaItem item = mAlbumDataSetLoader.get(thumbnailIndex);
            if (item == null)
                return; // Item not ready yet, ignore the click
            mSelector.toggle(item.getPath());
            mThumbnailView.invalidate();
        } else { // Render transition in pressed state
            mRender.setPressedIndex(thumbnailIndex);
            mRender.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_PHOTO, thumbnailIndex, 0), FadeTexture.DURATION);
        }
    }

    public void onLongTap(int thumbnailIndex) {
        if (mGetContent)
            return;
        MediaItem item = mAlbumDataSetLoader.get(thumbnailIndex);
        if (item == null)
            return;
        mSelector.toggle(item.getPath());
        mThumbnailView.invalidate();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        LLog.i(TAG, "onAttach");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LLog.i(TAG, "onCreate");
    }

    private void initializeViews() {
        mSelector = new SelectionManager(this, false);
        mSelector.setSelectionListener(this);
        mConfig = ViewConfigs.AlbumPage.get(getAndroidContext());
        ThumbnailLayout layout = new ThumbnailContractLayout(mConfig.albumSpec);
        mThumbnailView = new ThumbnailView(this, layout);
        mThumbnailView.setBackgroundColor(LetoolUtils.intColorToFloatARGBArray(getResources().getColor(R.color.default_background_thumbnail)));
        mRender = new ThumbnailRenderer(this, mThumbnailView, mSelector);
        layout.setRenderer(mRender);
        layout.setLayoutListener(this);
        mThumbnailView.setThumbnailRenderer(mRender);
        mThumbnailView.setOverscrollEffect(ThumbnailView.OVERSCROLL_SYSTEM);
        mRender.setModel(mAlbumDataSetLoader);
        mRootPane.addComponent(mThumbnailView);
        mThumbnailView.setListener(new ThumbnailView.SimpleListener() {

            @Override
            public void onDown(int index) {
                PhotoFragment.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                PhotoFragment.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int thumbnailIndex) {
                PhotoFragment.this.onSingleTapUp(thumbnailIndex);
            }

            @Override
            public void onLongTap(int thumbnailIndex) {
                PhotoFragment.this.onLongTap(thumbnailIndex);
            }
        });
    }

    private void initBrowseActionBar() {
        LetoolActionBar actionBar = getLetoolActionBar();
        actionBar.setOnActionMode(LetoolActionBar.ACTION_BAR_MODE_BROWSE, this);
        if (mIsCamera) {
            actionBar.setTitleIcon(R.drawable.ic_drawer);
            View tip = getActivity().findViewById(R.id.action_navi_tip);
            int distance = Math.round(getResources().getDimension(R.dimen.letool_action_bar_height) / 12);
            ObjectAnimator.ofFloat(tip, "x", tip.getX() - distance, tip.getX()).setDuration(300).start();
        } else {
            actionBar.setTitleIcon(R.drawable.ic_action_previous_item);
        }
        actionBar.setTitleText(mAlbumTitle);
        mMore = actionBar.getActionPanel().findViewById(R.id.action_more);
        mMore.setVisibility(View.VISIBLE);
    }

    private void initializeData() {
        Bundle data = getArguments();
        mIsCamera = data.getBoolean(ThumbnailActivity.KEY_IS_CAMERA);
        if (!mIsCamera) {
            mAlbumTitle = data.getString(ThumbnailActivity.KEY_ALBUM_TITLE);
            mDataSetPath = new MediaPath(data.getString(ThumbnailActivity.KEY_MEDIA_PATH), data.getLong(ThumbnailActivity.KEY_ALBUM_ID));
            mDataSet = getDataManager().getMediaSet(mDataSetPath);
            if (mDataSet == null) {
                Utils.fail("MediaSet is null. Path = %s", mDataSetPath);
            }
        } else {
            mAlbumTitle = getString(R.string.common_photo);
            mDataSetPath = new MediaPath(data.getString(ThumbnailActivity.KEY_MEDIA_PATH), MediaSetUtils.MY_ALBUM_BUCKETS[0]);
            mDataSet = new PhotoAlbum(mDataSetPath, (LetoolApp) getActivity().getApplication()
                    , MediaSetUtils.MY_ALBUM_BUCKETS, true, getString(R.string.common_photo));

        }
        mAlbumDataSetLoader = new ThumbnailDataLoader(this, mDataSet);
        mAlbumDataSetLoader.setLoadingListener(new MetaDataLoadingListener());
        mRender.setModel(mAlbumDataSetLoader);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LLog.i(TAG, "onCreateView" + System.currentTimeMillis());
        View rootView = inflater.inflate(R.layout.gl_root_view, container, false);
        mHasSDCard = StorageUtils.externalStorageAvailable();

        mGLRootView = (GLRootView) rootView.findViewById(R.id.gl_root_view);
        initializeViews();
        initializeData();
        mHandler = new SynchronizedHandler(mGLRootView) {

            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_LAYOUT_CONFIRMED: {
                        //mLoadingInsie.setVisibility(View.GONE);
                        break;
                    }
                    case MSG_PICK_PHOTO: {
                        pickPhoto(message.arg1);
                        break;
                    }
                    default:
                        throw new AssertionError(message.what);
                }
            }
        };
        mEyePosition = new EyePosition(getAndroidContext(), this);
        initBrowseActionBar();
        if (!mHasSDCard) {
            TextView emptyView = (TextView) rootView.findViewById(R.id.empty_view);
            emptyView.setText(R.string.common_error_nosdcard);
            emptyView.setVisibility(View.VISIBLE);
            mGLRootView.setVisibility(View.GONE);
        } else {
            //mLoadingInsie = (CommonLoadingPanel) rootView.findViewById(R.id.loading);
            //mLoadingInsie.setVisibility(View.VISIBLE);
        }
        Bundle data = getArguments();
        if (data != null) {
            int[] center = data.getIntArray(KEY_SET_CENTER);
            if (center != null) {
                mOpenCenter.setAbsolutePosition(center[0], center[1]);
                mThumbnailView.startScatteringAnimation(mOpenCenter);
            } else {
                //mThumbnailView.startRisingAnimation();
                mOpenCenter.setAbsolutePosition(360, 480);
                mThumbnailView.startScatteringAnimation(mOpenCenter);
            }
        }
        return rootView;
    }

    private void initSelectionActionBar() {
        LetoolActionBar actionBar = getLetoolActionBar();
        actionBar.setOnActionMode(LetoolActionBar.ACTION_BAR_MODE_SELECTION, this);
        actionBar.setSelectionManager(mSelector);
        String format = getResources().getQuantityString(R.plurals.number_of_items_selected, 0);
        actionBar.setTitleText(String.format(format, 0));
    }

    @Override
    public void onStart() {
        super.onStart();
        LLog.i(TAG, "onStart");
    }

    @Override
    public void onMenuClicked() {
        if (!mSelector.inSelectionMode()) {
            showPopupMenu();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mHasSDCard) {
            LLog.i(TAG, "onResume" + System.currentTimeMillis());
            mGLRootView.onResume();
            mGLRootView.lockRenderThread();
            try {
                mIsActive = true;
                mGLRootView.setContentPane(mRootPane);
                // Set the reload bit here to prevent it exit this page in clearLoadingBit().
                setLoadingBit(BIT_LOADING_RELOAD);
                mLoadingFailed = false;
                mAlbumDataSetLoader.resume();
                mRender.resume();
                mRender.setPressedIndex(-1);
                mEyePosition.resume();
                if (!mInitialSynced) {
                    setLoadingBit(BIT_LOADING_SYNC);
                    //mSyncTask = mDataSet.requestSync(this);
                }
            } finally {
                mGLRootView.unlockRenderThread();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mHasSDCard) {
            LLog.i(TAG, "onPause");
            mGLRootView.onPause();
            mGLRootView.lockRenderThread();
            try {
                mIsActive = false;
                mRender.setThumbnailFilter(null);
                mAlbumDataSetLoader.pause();
                mRender.pause();
                DetailsHelper.pause();
                mEyePosition.resume();
                if (mSyncTask != null) {
                    mSyncTask.cancel();
                    mSyncTask = null;
                    clearLoadingBit(BIT_LOADING_SYNC);
                }
            } finally {
                mGLRootView.unlockRenderThread();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        LLog.i(TAG, "onStop");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LLog.i(TAG, "onDestroyView");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        LLog.i(TAG, "onDetach");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDataSet != null) {
            mDataSet.closeCursor();
        }
        LLog.i(TAG, "onDestroy");
    }

    @Override
    public GLController getGLController() {
        return mGLRootView;
    }

    @Override
    public void onEyePositionChanged(float x, float y, float z) {
        mRootPane.lockRendering();
        mX = x;
        mY = y;
        mZ = z;
        mRootPane.unlockRendering();
        mRootPane.invalidate();
    }

    @Override
    public void onClick(View v) {
        if (!mIsActive) {
            return;
        }
        if (v.getId() == R.id.action_navi) {
            if (!mIsCamera) {
                getActivity().finish();
            } else {
                getLetoolSlidingMenu().toggle();
            }
        } else if (v.getId() == R.id.operation_delete) {
            int count = mSelector.getSelectedCount();
            if (count <= 0) {
                Toast t = Toast.makeText(getActivity(), R.string.common_selection_tip, Toast.LENGTH_SHORT);
                t.setGravity(Gravity.CENTER, 0, 0);
                t.show();
                return;
            }
            BatchDeleteMediaListener cdl = new BatchDeleteMediaListener(getActivity(), mSelector, getDataManager(),
                    new DeleteMediaProgressListener() {

                        @Override
                        public void onConfirmDialogDismissed(boolean confirmed) {
                            mSelector.leaveSelectionMode();
                        }

                    });
            new AlertDialog.Builder(getActivity())
                    .setMessage(getString(R.string.common_delete_tip))
                    .setOnCancelListener(cdl)
                    .setPositiveButton(R.string.ok, cdl)
                    .setNegativeButton(R.string.cancel, cdl)
                    .create().show();
        } else if (v.getId() == R.id.action_more) {
            showPopupMenu();
        } else if (v.getId() == R.id.enter_selection_indicate) {
            mSelector.leaveSelectionMode();
        }
    }

    public void showPopupMenu() {
        PopupMenu popup = new PopupMenu(this.getActivity());
        popup.setOnItemSelectedListener(this);
        popup.add(0, R.string.popup_menu_defaut_order);
        popup.add(1, R.string.popup_menu_time_order);
        popup.add(2, R.string.popup_menu_select_mode);
        popup.show(mMore);

    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                initSelectionActionBar();
                mRootPane.invalidate();
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                initBrowseActionBar();
                mRootPane.invalidate();
                break;
            }
            case SelectionManager.SELECT_ALL_MODE: {
                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange(MediaPath path, boolean selected) {
        int count = mSelector.getSelectedCount();
        String format = getResources().getQuantityString(R.plurals.number_of_items_selected, count);
        getLetoolActionBar().setTitleText(String.format(format, count));
    }

    private void pickPhoto(int index) {
        Intent it = new Intent();
        it.setClass(getAndroidContext(), FullImageActivity.class);
        if (mIsCamera) {
            it.putExtra(ThumbnailActivity.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_LOCAL_IMAGE_ONLY));
            it.putExtra(ThumbnailActivity.KEY_IS_CAMERA, true);
        } else {
            it.putExtra(ThumbnailActivity.KEY_ALBUM_ID, mDataSet.getPath().getIdentity());
            it.putExtra(ThumbnailActivity.KEY_MEDIA_PATH, getDataManager().getTopSetPath(DataManager.INCLUDE_LOCAL_IMAGE_ONLY));
            it.putExtra(ThumbnailActivity.KEY_IS_CAMERA, false);
            it.putExtra(ThumbnailActivity.KEY_ALBUM_TITLE, mDataSet.getName());
        }
        it.putExtra(FullImageFragment.KEY_INDEX_HINT, index);
        startActivity(it);
    }

    //-----------------------------------------------details-----------------------------------------------------------------------

    private class MyDetailsSource implements DetailsHelper.DetailsSource {

        private int mIndex;

        @Override
        public int size() {
            return mAlbumDataSetLoader.size();
        }

        @Override
        public int setIndex() {
            MediaPath id = mSelector.getSelected(false).get(0);
            mIndex = mAlbumDataSetLoader.findItem(id);
            return mIndex;
        }

        @Override
        public MediaDetails getDetails() {
            // this relies on setIndex() being called beforehand
            MediaObject item = mAlbumDataSetLoader.get(mIndex);
            if (item != null) {
                mRender.setHighlightItemPath(item.getPath());
                return item.getDetails();
            } else {
                return null;
            }
        }
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(this, mRootPane, mDetailsSource);
            mDetailsHelper.setCloseListener(new CloseListener() {

                @Override
                public void onClose() {
                    hideDetails();
                }
            });
        }
        mDetailsHelper.show();
    }

    private void hideDetails() {
        mShowDetails = false;
        mDetailsHelper.hide();
        mRender.setHighlightItemPath(null);
        mThumbnailView.invalidate();
    }

    @Override
    public void onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                break;

            case 1:
                break;

            case 2:
                if (mSelector != null) {
                    mSelector.enterSelectionMode();
                }
                break;

        }
    }

}
