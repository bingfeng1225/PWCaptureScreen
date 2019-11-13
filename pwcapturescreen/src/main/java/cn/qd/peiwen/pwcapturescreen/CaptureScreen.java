package cn.qd.peiwen.pwcapturescreen;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.WindowManager;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import cn.qd.peiwen.pwtools.EmptyUtils;
import cn.qd.peiwen.pwtools.FileUtils;

import static android.app.Activity.RESULT_OK;

public class CaptureScreen {
    private int width = 0;
    private int height = 0;
    private int result = 0;
    private int densityDpi = 0;
    private Intent intent = null;
    private Context context = null;
    private boolean busy = false;
    private boolean ready = false;
    private String filePath = null;
    private String fileName = null;
    private ImageReader imageReader = null;
    private ICaptureListener listener = null;
    private HandlerThread handlerThread = null;
    private CaptureHandler captureHandler = null;
    private VirtualDisplay virtualDisplay = null;
    private MediaProjection mediaProjection = null;
    private MediaProjectionManager projectionManager = null;

    public CaptureScreen(Context context) {
        this.context = context;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean isReady() {
        return ready;
    }

    public ICaptureListener getListener() {
        return listener;
    }

    public void setListener(ICaptureListener listener) {
        this.listener = listener;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void captureScreen() {
        if (!this.busy) {
            this.busy = true;
            this.captureHandler.sendEmptyMessage(0);
        }
    }

    public void createHandler() {
        this.handlerThread = new HandlerThread("Capture");
        this.handlerThread.start();
        this.captureHandler = new CaptureHandler(handlerThread.getLooper());
    }

    public void destroyHandler() {
        this.handlerThread.quit();
        this.handlerThread = null;
        this.captureHandler = null;
    }

    public void initCapturePScreen() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        this.width = metrics.widthPixels;
        this.height = metrics.heightPixels;
        this.densityDpi = metrics.densityDpi;
        this.projectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    public Intent createScreenCaptureIntent() {
        return this.projectionManager.createScreenCaptureIntent();
    }

    public boolean capturePermissionResponsed(int result, Intent intent) {
        if (result != RESULT_OK || EmptyUtils.isEmpty(intent)) {
            this.ready = false;
        } else {
            this.ready = true;
            this.intent = intent;
            this.result = result;
        }
        return this.ready;
    }

    private void beginCapture() {
        if (CaptureScreen.this.createMediaProjection()) {
            this.fileName = "" + System.currentTimeMillis() + ".png";
            CaptureScreen.this.createImageReader();
            CaptureScreen.this.createVirtualDisplay();
        } else {
            this.busy = false;
            if (EmptyUtils.isNotEmpty(this.listener)) {
                this.listener.onCaptureFailed();
            }
        }
    }

    private void finishCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        mediaProjection.unregisterCallback(projectionCallback);
        mediaProjection = null;
        this.busy = false;
    }

    private String filePath() {
        return this.filePath + File.separator + this.fileName;
    }

    private void createImageReader() {
        this.imageReader = ImageReader.newInstance(this.width, this.height, PixelFormat.RGBA_8888, 1);
        this.imageReader.setOnImageAvailableListener(new ImageAvailableListener(), this.captureHandler);
    }

    private void createVirtualDisplay() {
        this.virtualDisplay = this.mediaProjection.createVirtualDisplay(
                "SCREEN_CAPTURE", this.width, this.height, this.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                this.imageReader.getSurface(), null, this.captureHandler
        );
    }

    private boolean createMediaProjection() {
        mediaProjection = projectionManager.getMediaProjection(this.result, this.intent);
        if (EmptyUtils.isEmpty(mediaProjection)) {
            return false;
        }
        this.mediaProjection.registerCallback(projectionCallback, this.captureHandler);
        return true;
    }

    private MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            super.onStop();
            CaptureScreen.this.captureHandler.sendEmptyMessage(1);
        }
    };


    private class CaptureHandler extends Handler {
        public CaptureHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0: {
                    CaptureScreen.this.beginCapture();
                    break;
                }
                case 1: {
                    String filepath = filePath();
                    if (!FileUtils.isFileExist(filepath)) {
                        if (EmptyUtils.isNotEmpty(listener)) {
                            listener.onCaptureFailed();
                        }
                    } else {
                        if (EmptyUtils.isNotEmpty(listener)) {
                            listener.onCaptureSuccessed(filepath);
                        }
                    }
                    CaptureScreen.this.finishCapture();
                    break;
                }
            }
        }
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private boolean capture = true;

        public ImageAvailableListener() {

        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (this.capture) {
                this.capture = false;
                this.saveImage(reader);
            }
        }

        private void saveImage(ImageReader reader) {
            Image image = null;
            Bitmap bitmap = null;
            FileOutputStream fos = null;
            try {
                image = reader.acquireLatestImage();
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * CaptureScreen.this.width;
                // create bitmap
                bitmap = Bitmap.createBitmap(CaptureScreen.this.width + rowPadding / pixelStride, CaptureScreen.this.height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                // write bitmap to a file
                fos = new FileOutputStream(filePath());
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
                if (image != null) {
                    image.close();
                }
                CaptureScreen.this.mediaProjection.stop();
            }
        }
    }
}
