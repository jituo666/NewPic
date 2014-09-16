
package com.xjt.newpic.edit.pipeline;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import com.xjt.newpic.R;
import com.xjt.newpic.common.ApiHelper;
import com.xjt.newpic.common.LLog;
import com.xjt.newpic.edit.NpEditActivity;
import com.xjt.newpic.edit.filters.FiltersManager;
import com.xjt.newpic.edit.filters.ImageFilter;
import com.xjt.newpic.edit.imageshow.MasterImage;
import com.xjt.newpic.edit.tools.SaveImage;

import java.io.File;

public class ProcessingService extends Service {

    private static final String TAG = ProcessingService.class.getSimpleName();

    private int mNotificationId;
    private NotificationManager mNotifyMgr = null;
    private NotificationCompat.Builder mBuilder = null;

    private static final boolean SHOW_IMAGE = false;

    private static final String PRESET = "preset";
    private static final String QUALITY = "quality";
    private static final String SELECTED_URI = "selectedUri";
    private static final String SAVING = "saving";
    //    private static final String FLATTEN = "flatten";
    private static final String SIZE_FACTOR = "sizeFactor";
    private static final String EXIT = "exit";

    private final IBinder mBinder = new LocalBinder();
    private NpEditActivity mFiltershowActivity;
    private ProcessingTaskController mProcessingTaskController;

    private RenderingRequestTask mRenderingRequestTask;
    private HighresRenderingRequestTask mHighresRenderingRequestTask;
    private FullresRenderingRequestTask mFullresRenderingRequestTask;

    private UpdatePreviewTask mUpdatePreviewTask;
    private ImageSavingTask mImageSavingTask;

    private boolean mSaving = false;

    public class LocalBinder extends Binder {

        public ProcessingService getService() {
            return ProcessingService.this;
        }
    }

    static {
        System.loadLibrary("native_filters");
    }

    public void setFiltershowActivity(NpEditActivity filtershowActivity) {
        mFiltershowActivity = filtershowActivity;
    }

    public void setOriginalBitmap(Bitmap originalBitmap) {
        if (mUpdatePreviewTask == null) {
            return;
        }
        mUpdatePreviewTask.setOriginal(originalBitmap);
        mHighresRenderingRequestTask.setOriginal(originalBitmap);
        mFullresRenderingRequestTask.setOriginal(originalBitmap);
        mRenderingRequestTask.setOriginal(originalBitmap);
    }

    public void setOriginalBitmapHighres(Bitmap originalHires) {
        mHighresRenderingRequestTask.setOriginalBitmapHighres(originalHires);
    }

    public void setPreviewScaleFactor(float previewScale) {
        LLog.i(TAG, "----------------------setPreviewScaleFactor:" + previewScale);
        mHighresRenderingRequestTask.setPreviewScaleFactor(previewScale);
        mFullresRenderingRequestTask.setPreviewScaleFactor(previewScale);
        mRenderingRequestTask.setPreviewScaleFactor(previewScale);
    }

    public void setHighresPreviewScaleFactor(float highResPreviewScale) {
        LLog.i(TAG, "----------------------setHighresPreviewScaleFactor:" + highResPreviewScale);
        mHighresRenderingRequestTask.setHighresPreviewScaleFactor(highResPreviewScale);
    }

    public void updatePreviewBuffer() {
        mHighresRenderingRequestTask.stop();
        mFullresRenderingRequestTask.stop();
        mUpdatePreviewTask.updatePreview();
    }

    public void postRenderingRequest(RenderingRequest request) {
        mRenderingRequestTask.postRenderingRequest(request);
    }

    public void postHighresRenderingRequest(ImagePreset preset, float scaleFactor, RenderingRequestCaller caller) {
        RenderingRequest request = new RenderingRequest();
        // TODO: use the triple buffer preset as UpdatePreviewTask does instead of creating a copy
        ImagePreset passedPreset = new ImagePreset(preset);
        request.setOriginalImagePreset(preset);
        request.setScaleFactor(scaleFactor);
        request.setImagePreset(passedPreset);
        request.setType(RenderingRequest.HIGHRES_RENDERING);
        request.setCaller(caller);
        mHighresRenderingRequestTask.postRenderingRequest(request);
    }

    public void postFullresRenderingRequest(ImagePreset preset, float scaleFactor, Rect bounds, Rect destination, RenderingRequestCaller caller) {
        RenderingRequest request = new RenderingRequest();
        ImagePreset passedPreset = new ImagePreset(preset);
        request.setOriginalImagePreset(preset);
        request.setScaleFactor(scaleFactor);
        request.setImagePreset(passedPreset);
        request.setType(RenderingRequest.PARTIAL_RENDERING);
        request.setCaller(caller);
        request.setBounds(bounds);
        request.setDestination(destination);
        passedPreset.setPartialRendering(true, bounds);
        mFullresRenderingRequestTask.postRenderingRequest(request);
    }

    public static Intent getSaveIntent(Context context, ImagePreset preset, Uri selectedImageUri,
            int quality, float sizeFactor, boolean needsExit) {
        Intent processIntent = new Intent(context, ProcessingService.class);
        processIntent.putExtra(ProcessingService.SELECTED_URI, selectedImageUri.toString());
        processIntent.putExtra(ProcessingService.QUALITY, quality);
        processIntent.putExtra(ProcessingService.SIZE_FACTOR, sizeFactor);

        processIntent.putExtra(ProcessingService.PRESET, preset.getJsonString(ImagePreset.JASON_SAVED));
        processIntent.putExtra(ProcessingService.SAVING, true);
        processIntent.putExtra(ProcessingService.EXIT, needsExit);
        return processIntent;
    }

    @Override
    public void onCreate() {
        mProcessingTaskController = new ProcessingTaskController(this);
        mImageSavingTask = new ImageSavingTask(this);
        mUpdatePreviewTask = new UpdatePreviewTask();
        mHighresRenderingRequestTask = new HighresRenderingRequestTask();
        mFullresRenderingRequestTask = new FullresRenderingRequestTask();
        mRenderingRequestTask = new RenderingRequestTask();
        mProcessingTaskController.add(mImageSavingTask);
        mProcessingTaskController.add(mUpdatePreviewTask);
        mProcessingTaskController.add(mHighresRenderingRequestTask);
        mProcessingTaskController.add(mFullresRenderingRequestTask);
        mProcessingTaskController.add(mRenderingRequestTask);
        setupPipeline();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void onStart() {
        if (!mSaving && mFiltershowActivity != null) {
            mFiltershowActivity.updateUIAfterServiceStarted();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(SAVING, false)) {
            // we save using an intent to keep the service around after the activity has been destroyed.
            String presetJson = intent.getStringExtra(PRESET);
            String selected = intent.getStringExtra(SELECTED_URI);
            int quality = intent.getIntExtra(QUALITY, 100);
            float sizeFactor = intent.getFloatExtra(SIZE_FACTOR, 1);
            boolean exit = intent.getBooleanExtra(EXIT, false);
            Uri selectedUri = null;
            if (selected != null) {
                selectedUri = Uri.parse(selected);
            }
            ImagePreset preset = new ImagePreset();
            preset.readJsonFromString(presetJson);
            mSaving = true;
            handleSaveRequest(selectedUri, preset, MasterImage.getImage().getHighresImage(), quality, sizeFactor, exit);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        tearDownPipeline();
        mProcessingTaskController.quit();
    }

    public void handleSaveRequest(Uri selectedUri, ImagePreset preset, Bitmap previewImage, int quality, float sizeFactor, boolean exit) {
        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.cancelAll();
        mBuilder = new NotificationCompat.Builder(this);
        if (ApiHelper.AT_LEAST_11)
            mBuilder.setSmallIcon(R.drawable.filtershow_button_fx);
        mBuilder.setContentTitle(getString(R.string.filtershow_notification_label));
        mBuilder.setContentText(getString(R.string.filtershow_notification_message));
        startForeground(mNotificationId, mBuilder.build());
        updateProgress(SaveImage.MAX_PROCESSING_STEPS, 0);

        // Process the image
        mImageSavingTask.saveImage(selectedUri, preset, previewImage, quality, sizeFactor, exit);
    }

    public void updateNotificationWithBitmap(Bitmap bitmap) {
        mBuilder.setLargeIcon(bitmap);
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    public void updateProgress(int max, int current) {
        mBuilder.setProgress(max, current, false);
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    public void completePreviewSaveImage(Uri result, boolean exit) {
        if (exit) {
            mFiltershowActivity.completeSaveImage(result);
        }
    }

    public void completeSaveImage(Uri result, boolean exit) {
        if (SHOW_IMAGE) {
            // TODO: we should update the existing image in Gallery instead
            Intent viewImage = new Intent(Intent.ACTION_VIEW, result);
            viewImage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(viewImage);
        }
        mNotifyMgr.cancel(mNotificationId);
        if (!exit) {
            stopForeground(true);
            stopSelf();
            return;
        }
        stopForeground(true);
        stopSelf();
        if (exit) {
            // terminate now
            mFiltershowActivity.completeSaveImage(result);
        }
    }

    private void setupPipeline() {
        Resources res = getResources();
        FiltersManager.setResources(res);
        CachingPipeline.createRenderscriptContext(this);

        FiltersManager filtersManager = FiltersManager.getManager();
        filtersManager.addLooks(this);
        filtersManager.addBorders(this);
        filtersManager.addTools(this);
        filtersManager.addEffects();

        FiltersManager highresFiltersManager = FiltersManager.getHighresManager();
        highresFiltersManager.addLooks(this);
        highresFiltersManager.addBorders(this);
        highresFiltersManager.addTools(this);
        highresFiltersManager.addEffects();
    }

    private void tearDownPipeline() {
        ImageFilter.resetStatics();
        FiltersManager.getPreviewManager().freeRSFilterScripts();
        FiltersManager.getManager().freeRSFilterScripts();
        FiltersManager.getHighresManager().freeRSFilterScripts();
        FiltersManager.reset();
        CachingPipeline.destroyRenderScriptContext();
    }

}
