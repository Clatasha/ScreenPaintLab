package com.clatasha.screenpaintlab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Diagnostic-only MediaProjection probe. Does not override FLAG_SECURE.
 * The extra ProbeEvent images in Pictures/ScreenPaintLab record the
 * foreground service lifecycle without relying on inaccessible system logs.
 */
public final class ScreenProbeService extends Service {
    public static final String ACTION_START = "com.clatasha.screenpaintlab.START_PROBE";
    public static final String ACTION_STOP = "com.clatasha.screenpaintlab.STOP_PROBE";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    private static final String CHANNEL = "probe_status";
    private static final String TAG = "ScreenPaintLab";

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private long lastSaveTime = 0;
    private int sessionCount = 0;
    private boolean stopping = false;
    private boolean firstFrameErrorLogged = false;
    private String endReason = "Service stopped (reason not determined)";
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() {
            endReason = "Android ended MediaProjection session";
            writeEvent("Capture revoked / stopped", endReason);
            stopSelf();
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            endReason = "Service restarted without capture grant";
            writeEvent("Capture not started", endReason);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            endReason = "User requested Stop";
            writeEvent("Manual stop", endReason);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || projection != null) return START_NOT_STICKY;
        createChannel();
        Notification notice = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen Paint Lab: probe active")
                .setContentText("Diagnostic frames every 2 seconds (up to 40).")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(201, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(201, notice);
        }
        try {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent token;
            if (Build.VERSION.SDK_INT >= 33) {
                token = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
            } else {
                token = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            }
            if (token == null || resultCode != android.app.Activity.RESULT_OK) {
                throw new IllegalStateException("Missing screen-capture grant");
            }
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(resultCode, token);
            if (projection == null) throw new IllegalStateException("Android did not return a MediaProjection");
            thread = new HandlerThread("Capture frames");
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            projection.registerCallback(projectionCallback, handler);
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(r -> {
                Image frame = null;
                try {
                    frame = r.acquireLatestImage();
                    if (frame == null || stopping) return;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastSaveTime < 2000 || sessionCount >= 40) return;
                    lastSaveTime = now;
                    Bitmap bitmap = extractBitmap(frame);
                    if (bitmap != null) {
                        boolean saved = saveBitmap(bitmap, "Probe");
                        bitmap.recycle();
                        if (saved) {
                            sessionCount++;
                            getSharedPreferences("probe", MODE_PRIVATE).edit()
                                    .putInt("saved_count",
                                      getSharedPreferences("probe", MODE_PRIVATE).getInt("saved_count", 0) + 1)
                                    .apply();
                        }
                    }
                    if (sessionCount >= 40) {
                        endReason = "Probe automatically reached 40 frames";
                        writeEvent("Frame limit reached", endReason);
                        stopSelf();
                    }
                } catch (Exception exception) {
                    Log.e(TAG, "Frame capture failure", exception);
                    if (!firstFrameErrorLogged) {
                        firstFrameErrorLogged = true;
                        writeEvent("Frame error", exception.getClass().getSimpleName() + ": " + safeMessage(exception));
                    }
                } finally {
                    if (frame != null) frame.close();
                }
            }, handler);
            display = projection.createVirtualDisplay("Screen Paint Lab", width, height,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
            writeEvent("Capture started", "Screen-capture permission granted; virtual display initialized.");
        } catch (Exception exception) {
            endReason = "Capture start failed: " + exception.getClass().getSimpleName();
            Log.e(TAG, endReason, exception);
            writeEvent("Start failure", endReason + ": " + safeMessage(exception));
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) return "(no details)";
        return message.length() > 75 ? message.substring(0, 75) : message;
    }

    /** Saves a diagnostic IMAGE in Gallery; never includes any screen pixels. */
    private void writeEvent(String title, String detail) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(1080, 330, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(20, 27, 39));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(95, 223, 216));
            paint.setTextSize(44);
            paint.setFakeBoldText(true);
            canvas.drawText("SCREEN PAINT LAB  |  CAPTURE EVENT", 38, 66, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(49);
            canvas.drawText(title, 38, 142, paint);
            paint.setTextSize(27);
            paint.setColor(Color.rgb(203, 217, 230));
            int max = Math.min(detail.length(), 65);
            canvas.drawText(detail.substring(0, max), 38, 210, paint);
            if (detail.length() > max) {
                canvas.drawText(detail.substring(max, Math.min(detail.length(), max + 65)), 38, 253, paint);
            }
            paint.setTextSize(23);
            canvas.drawText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()), 38, 302, paint);
            saveBitmap(bitmap, "ProbeEvent");
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Could not save diagnostic event", e);
        }
    }

    private Bitmap extractBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth(), height = image.getHeight();
        int pixelStride = plane.getPixelStride(), rowStride = plane.getRowStride();
        int paddedWidth = width + (rowStride - pixelStride * width) / pixelStride;
        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        padded.copyPixelsFromBuffer(buffer);
        Bitmap crop = Bitmap.createBitmap(padded, 0, 0, width, height);
        if (crop != padded) padded.recycle();
        return crop;
    }

    private boolean saveBitmap(Bitmap bitmap, String prefix) {
        String when = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, prefix + "_" + when + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/ScreenPaintLab");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        android.net.Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("Image write failed");
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Could not save image", ex);
            getContentResolver().delete(uri, null, null);
            return false;
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Screen probe", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        stopping = true;
        writeEvent("Capture service ended", endReason);
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
            reader = null;
        }
        if (display != null) { display.release(); display = null; }
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
            projection = null;
        }
        if (thread != null) { thread.quitSafely(); thread = null; }
        super.onDestroy();
    }
}
