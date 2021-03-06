package com.xjt.newpic.fragment;

import com.umeng.analytics.MobclickAgent;
import com.xjt.newpic.NpApp;
import com.xjt.newpic.NpContext;
import com.xjt.newpic.R;
import com.xjt.newpic.activities.NpMediaActivity;
import com.xjt.newpic.activities.NpMoviePlayActivity;
import com.xjt.newpic.common.EyePosition;
import com.xjt.newpic.common.GlobalConstants;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.common.SynchronizedHandler;
import com.xjt.newpic.metadata.DataManager;
import com.xjt.newpic.metadata.MediaDetails;
import com.xjt.newpic.metadata.MediaItem;
import com.xjt.newpic.metadata.MediaPath;
import com.xjt.newpic.metadata.MediaSet;
import com.xjt.newpic.metadata.MediaSetUtils;
import com.xjt.newpic.metadata.loader.DataLoadingListener;
import com.xjt.newpic.metadata.loader.ThumbnailDataLoader;
import com.xjt.newpic.metadata.source.LocalAlbum;
import com.xjt.newpic.preference.GlobalPreference;
import com.xjt.newpic.share.ShareManager;
import com.xjt.newpic.stat.StatConstants;
import com.xjt.newpic.utils.LetoolUtils;
import com.xjt.newpic.utils.RelativePosition;
import com.xjt.newpic.utils.StorageUtils;
import com.xjt.newpic.utils.Utils;
import com.xjt.newpic.views.DetailsHelper;
import com.xjt.newpic.views.GLController;
import com.xjt.newpic.views.GLView;
import com.xjt.newpic.views.NpBottomBar;
import com.xjt.newpic.views.NpDialog;
import com.xjt.newpic.views.NpTopBar;
import com.xjt.newpic.views.SingleDeleteMediaListener;
import com.xjt.newpic.views.ThumbnailView;
import com.xjt.newpic.views.DetailsHelper.CloseListener;
import com.xjt.newpic.views.DetailsHelper.DetailsSource;
import com.xjt.newpic.views.NpTopBar.OnActionModeListener;
import com.xjt.newpic.views.SingleDeleteMediaListener.SingleDeleteMediaProgressListener;
import com.xjt.newpic.views.layout.ThumbnailLayout;
import com.xjt.newpic.views.opengl.FadeTexture;
import com.xjt.newpic.views.opengl.GLESCanvas;
import com.xjt.newpic.views.render.ThumbnailVideoRenderer;
import com.xjt.newpic.views.utils.ActionItem;
import com.xjt.newpic.views.utils.ViewConfigs;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @Author Jituo.Xuan
 * @Date 9:48:35 AM Apr 19, 2014
 * @Comments:null
 */
public class VideoFragment extends Fragment implements EyePosition.EyePositionListener, OnActionModeListener {

    private static final String TAG = VideoFragment.class.getSimpleName();

    private static final int BIT_LOADING_RELOAD = 1;
    //
    private static final int MSG_PICK_VIDEO = 1;

    private static final int MENU_ITEM_SHARE = 0;
    private static final int MENU_ITEM_DETAIL = 1;
    private static final int MENU_ITEM_DELETE = 2;

    private NpContext mLetoolContext;

    // photo data
    private String mAlbumTitle;
    private MediaPath mVideoPath;
    private MediaSet mVideoData;
    private ThumbnailDataLoader mVideoDataLoader;
    private int mLoadingBits = 0;
    private boolean mIsCameraSource = false;

    // views
    private GLController mGLController;
    private ViewConfigs.VideoPage mConfig;
    private ThumbnailView mThumbnailView;
    private ThumbnailVideoRenderer mRender;

    // gl
    private EyePosition mEyePosition; // The eyes' position of the user, the origin is at the center of the device and the unit is in pixels.
    private float mUserDistance; // in pixel
    private float mX;
    private float mY;
    private float mZ;

    private boolean mIsActive = false;
    private boolean mIsSDCardMountedCorreclty = false;
    private boolean mHasDefaultDCIMDirectory = false;
    private boolean mShowDetails;
    private DetailsHelper mDetailsHelper;
    private SynchronizedHandler mHandler;
    private RelativePosition mOpenCenter = new RelativePosition();
    private int mLongPressedIndex = 0;

    private final GLView mRootPane = new GLView() {

        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            mEyePosition.resetPosition();
            NpTopBar actionBar = mLetoolContext.getLetoolTopBar();
            int thumbnailViewLeft = left + mConfig.paddingLeft;
            int thumbnailViewRight = right - left - mConfig.paddingRight;
            int thumbnailViewTop = top + mConfig.paddingTop + actionBar.getHeight();
            int thumbnailViewBottom = bottom - top - mConfig.paddingBottom;
            mRender.setHighlightItemPath(null);
            mOpenCenter.setReferencePosition(0, thumbnailViewTop);
            mOpenCenter.setAbsolutePosition((right - left) / 2, (bottom - top) / 2);
            mThumbnailView.layout(thumbnailViewLeft, thumbnailViewTop, thumbnailViewRight, thumbnailViewBottom);
            if (mShowDetails) {
                mDetailsHelper.layout(thumbnailViewLeft, thumbnailViewTop, thumbnailViewRight, thumbnailViewBottom);
            }
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
            setLoadingBit(BIT_LOADING_RELOAD);
        }

        @Override
        public void onLoadingFinished(boolean loadFailed) {
            clearLoadingBit(BIT_LOADING_RELOAD);
        }
    }

    private void setLoadingBit(int loadTaskBit) {
        mLoadingBits |= loadTaskBit;
    }

    private void clearLoadingBit(int loadTaskBit) {
        mLoadingBits &= ~loadTaskBit;
        if (mLoadingBits == 0 && mIsActive) {
            if (mVideoDataLoader.size() == 0) {
                mLetoolContext.showEmptyView(R.drawable.ic_no_video, R.string.common_error_no_video);
            } else {
                mLetoolContext.hideEmptyView();
            }
        }
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

    private void onSingleTapUp(int videoIndex) {
        if (!mIsActive)
            return;
        mRender.setPressedIndex(videoIndex);
        mRender.setPressedUp();
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_VIDEO, videoIndex, 0), FadeTexture.DURATION);

    }

    public void onLongTap(int videoIndex) {
        MobclickAgent.onEvent(mLetoolContext.getActivityContext(), StatConstants.EVENT_KEY_PHOTO_LONG_PRESSED);
        MediaItem item = mVideoDataLoader.get(videoIndex);
        if (item == null)
            return;
        mLongPressedIndex = videoIndex;
        List<ActionItem> items = new ArrayList<ActionItem>();
        addMenuItem(items, MENU_ITEM_SHARE, R.string.common_share);
        addMenuItem(items, MENU_ITEM_DETAIL, R.string.common_detail);
        addMenuItem(items, MENU_ITEM_DELETE, R.string.common_delete);
        final NpDialog dlg = new NpDialog(getActivity());
        dlg.setTitle(item.getName());
        ListView listView = dlg.setListAdapter(new MenuItemAdapter(getActivity(), items));
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                dlg.dismiss();
                if (position == MENU_ITEM_SHARE) {
                    ArrayList<Uri> uris = new ArrayList<Uri>();
                    String filePath = mVideoDataLoader.get(mLongPressedIndex).getFilePath();
                    uris.add(Uri.parse("file://" + filePath));
                    ShareManager.showAllShareDialog(getActivity(), GlobalConstants.MIMI_TYPE_VIDEO, uris, null);
                } else if (position == MENU_ITEM_DETAIL) {
                    showDetails();
                } else if (position == MENU_ITEM_DELETE) {
                    delete();
                }
            }
        });
        dlg.setCancelBtn(R.string.common_cancel, null, R.drawable.np_common_pressed_bottom_bg);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
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
        mLetoolContext = (NpContext) getActivity();

        initializeData();
        initializeViews();
      
        mEyePosition = new EyePosition(mLetoolContext.getActivityContext(), this);
        mThumbnailView.startScatteringAnimation(mOpenCenter, true, true, true);
    }

    private void initializeData() {
        Bundle data = getArguments();
        mIsCameraSource = data.getBoolean(NpMediaActivity.KEY_IS_CAMERA_SOURCE);
        if (mIsCameraSource) {
            if (MediaSetUtils.getBucketsIds().length > 0) {
                mAlbumTitle = getString(R.string.common_photo);
                mVideoPath = new MediaPath(data.getString(NpMediaActivity.KEY_MEDIA_PATH), MediaSetUtils.getBucketsIds()[0]);
                mVideoData = new LocalAlbum(mVideoPath, (NpApp) getActivity().getApplication(), MediaSetUtils.getBucketsIds(),
                        mLetoolContext.isImageBrwosing(),
                        getString(R.string.common_photo));
                mHasDefaultDCIMDirectory = true;
            } else {
                mHasDefaultDCIMDirectory = false;
                return;
            }
        } else {
            mAlbumTitle = data.getString(NpMediaActivity.KEY_ALBUM_TITLE);
            mVideoPath = new MediaPath(data.getString(NpMediaActivity.KEY_MEDIA_PATH), data.getInt(NpMediaActivity.KEY_ALBUM_ID));
            mVideoData = mLetoolContext.getDataManager().getMediaSet(mVideoPath);
            if (mVideoData == null) {
                Utils.fail("MediaSet is null. Path = %s", mVideoPath);
            }
        }
        mVideoDataLoader = new ThumbnailDataLoader(mLetoolContext, mVideoData);
        mVideoDataLoader.setLoadingListener(new MetaDataLoadingListener());
    }

    private void initializeViews() {
        mConfig = ViewConfigs.VideoPage.get(mLetoolContext.getActivityContext());
        ThumbnailLayout layout;
        layout = new ThumbnailLayout(mConfig.videoSpec);
        mThumbnailView = new ThumbnailView(mLetoolContext, layout);
        mThumbnailView.setBackgroundColor(LetoolUtils.intColorToFloatARGBArray(getResources().getColor(R.color.gl_background_color)));
        mThumbnailView.setListener(new ThumbnailView.SimpleListener() {

            @Override
            public void onDown(int index) {
                VideoFragment.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                VideoFragment.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int videoIndex) {
                VideoFragment.this.onSingleTapUp(videoIndex);
            }

            @Override
            public void onLongTap(int videoIndex) {
                VideoFragment.this.onLongTap(videoIndex);
            }
        });
        mRender = new ThumbnailVideoRenderer(mLetoolContext, mThumbnailView, mIsCameraSource);
        layout.setRenderer(mRender);
        mThumbnailView.setThumbnailRenderer(mRender);
        mRender.setModel(mVideoDataLoader);
        mRootPane.addComponent(mThumbnailView);
    }

    private void initBars() {
        NpTopBar topBar = mLetoolContext.getLetoolTopBar();
        topBar.setOnActionMode(NpTopBar.ACTION_BAR_MODE_BROWSE, this);
        topBar.setVisible(View.VISIBLE, false);
        ViewGroup nativeButtons = (ViewGroup) topBar.getActionPanel().findViewById(R.id.navi_buttons);
        if (mIsCameraSource) {
            topBar.setTitleText("");
            nativeButtons.setVisibility(View.VISIBLE);
            topBar.setTitleIcon(R.drawable.ic_drawer);
            TextView naviToPhoto = (TextView) nativeButtons.findViewById(R.id.action_action1);
            naviToPhoto.setText(R.string.common_video);
            naviToPhoto.setEnabled(false);
            TextView naviToGallery = (TextView) nativeButtons.findViewById(R.id.action_action2);
            naviToGallery.setText(R.string.common_movies);
            naviToGallery.setEnabled(true);
            naviToGallery.setOnClickListener(this);
        } else {
            topBar.setTitleText(mAlbumTitle);
            nativeButtons.setVisibility(View.GONE);
            topBar.setTitleIcon(R.drawable.ic_action_previous_item);
        }
        NpBottomBar bottomBar = mLetoolContext.getLetoolBottomBar();
        bottomBar.setVisible(View.GONE, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LLog.i(TAG, "onCreateView" + System.currentTimeMillis());
        initBars();
        mIsSDCardMountedCorreclty = StorageUtils.externalStorageAvailable();
        mHasDefaultDCIMDirectory = MediaSetUtils.getBucketsIds().length > 0;
        if (!mIsSDCardMountedCorreclty) {
            mLetoolContext.showEmptyView(R.drawable.ic_launcher, R.string.common_error_nosdcard);
        } else if (mIsCameraSource && !mHasDefaultDCIMDirectory) {
            mLetoolContext.showEmptyView(R.drawable.ic_no_video, R.string.common_error_nodcim_video);
        } else {
            mLetoolContext.hideEmptyView();
        }
        mGLController = mLetoolContext.getGLController();
        mHandler = new SynchronizedHandler(mGLController) {

            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_VIDEO: {
                        playVideo(message.arg1);
                        break;
                    }
                    default:
                        throw new AssertionError(message.what);
                }
            }
        };
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        LLog.i(TAG, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        LLog.i(TAG, "onResume " + System.currentTimeMillis());
        MobclickAgent.onPageStart(TAG);
        if (!mIsSDCardMountedCorreclty || mIsCameraSource && !mHasDefaultDCIMDirectory) {
            return;
        }

        mGLController.setContentPane(mRootPane);
        mGLController.onResume();
        mGLController.lockRenderThread();
        try {
            mIsActive = true;
            // Set the reload bit here to prevent it exit this page in clearLoadingBit().
            setLoadingBit(BIT_LOADING_RELOAD);
            mVideoDataLoader.resume();
            mRender.resume();
            mRender.setPressedIndex(-1);
            mEyePosition.resume();

        } finally {
            mGLController.unlockRenderThread();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LLog.i(TAG, "onPause");
        MobclickAgent.onPageEnd(TAG);
        mGLController.onPause();
        if (!mIsSDCardMountedCorreclty || mIsCameraSource && !mHasDefaultDCIMDirectory) {
            return;
        }
        mGLController.lockRenderThread();
        try {
            mIsActive = false;
            mVideoDataLoader.pause();
            mRender.pause();
            if (mShowDetails)
                hideDetails();
            DetailsHelper.pause();
            mEyePosition.pause();

        } finally {
            mGLController.unlockRenderThread();
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
        if (mVideoData != null) {
            mVideoData.destroyMediaSet();
        }
        if (GlobalPreference.rememberLastUI(getActivity())) {
            GlobalPreference.setLastUI(getActivity(), GlobalConstants.UI_TYPE_VIDEO_ITEMS);
        }
        LLog.i(TAG, "onDestroy");
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
        if (v.getId() == R.id.action_navi) {
            if (mIsCameraSource) {
                MobclickAgent.onEvent(mLetoolContext.getActivityContext(), StatConstants.EVENT_KEY_SLIDE_MENU);
                mLetoolContext.getSlidingMenu().toggle();
            } else {
                mLetoolContext.popContentFragment();
            }
        } else if (mIsSDCardMountedCorreclty && v.getId() == R.id.action_action2) {
            GalleryFragment f = new GalleryFragment();
            Bundle data = new Bundle();
            data.putString(NpMediaActivity.KEY_MEDIA_PATH, mLetoolContext.getDataManager()
                    .getTopSetPath(mLetoolContext.isImageBrwosing() ? DataManager.INCLUDE_LOCAL_IMAGE_SET_ONLY : DataManager.INCLUDE_LOCAL_VIDEO_SET_ONLY));
            f.setArguments(data);
            mLetoolContext.pushContentFragment(f, this, false);
        }
    }

    private void playVideo(int videoIndex) {
        MediaItem item = mVideoDataLoader.get(videoIndex);
        if (item == null)
            return;
        Context c = mLetoolContext.getActivityContext();
        try {
            Intent intent = new Intent();
            intent.setClass(c, NpMoviePlayActivity.class);
            intent.setDataAndType(Uri.parse(item.getFilePath()), "video/*");
            intent.putExtra(Intent.EXTRA_TITLE, "");
            c.startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, c.getString(R.string.app_name), Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------details-----------------------------------------------------------------------

    private void delete() {
        MediaItem item = mVideoDataLoader.get(mLongPressedIndex);
        SingleDeleteMediaListener cdl = new SingleDeleteMediaListener(getActivity(), item.getPath(), mLetoolContext.getDataManager(),
                new SingleDeleteMediaProgressListener() {

                    @Override
                    public void onConfirmDialogDismissed(boolean confirmed) {
                        if (confirmed) {
                            MobclickAgent.onEvent(mLetoolContext.getActivityContext(), StatConstants.EVENT_KEY_VIDEO_DELETE);
                        }
                    }

                });

        final NpDialog dlg = new NpDialog(getActivity());
        dlg.setTitle(R.string.common_recommend);
        dlg.setOkBtn(R.string.common_ok, cdl, R.drawable.np_common_pressed_left_bg);
        dlg.setCancelBtn(R.string.common_cancel, cdl, R.drawable.np_common_pressed_right_bg);
        dlg.setMessage(mIsCameraSource ? Html.fromHtml(getString(R.string.common_delete_cur_video_tip, item.getName()))
                : Html.fromHtml(getString(R.string.common_delete_cur_movie_tip, item.getName())));
        dlg.show();
    }

    private class MyDetailsSource implements DetailsSource {

        public MyDetailsSource() {
        }

        @Override
        public MediaDetails getDetails() {
            MediaItem item = mVideoDataLoader.get(mLongPressedIndex);
            return item.getDetails();
        }

        @Override
        public int size() {
            return mVideoData != null ? mVideoData.updateMediaSet() : 1;
        }

        @Override
        public int setIndex() {
            return mLongPressedIndex;
        }
    }

    private void showDetails() {
        mShowDetails = true;
        if (mDetailsHelper == null) {
            mDetailsHelper = new DetailsHelper(mLetoolContext, mRootPane, new MyDetailsSource());
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
    }

    public void addMenuItem(List<ActionItem> items, int itemId, CharSequence titleRes) {
        ActionItem item = new ActionItem();
        item.setItemId(itemId);
        item.setTitle(titleRes.toString());
        items.add(item);
    }

    public void addMenuItem(List<ActionItem> items, int itemId, int titleRes) {
        addMenuItem(items, itemId, getString(titleRes));
    }

    static class ViewHolder {

        TextView title;
    }

    private class MenuItemAdapter extends ArrayAdapter<ActionItem> {

        public MenuItemAdapter(Context context, List<ActionItem> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.common_action_item, null);
                holder = new ViewHolder();
                holder.title = (TextView) convertView.findViewById(R.id.title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ActionItem item = getItem(position);
            holder.title.setText(item.getTitle());
            return convertView;
        }
    }

}
