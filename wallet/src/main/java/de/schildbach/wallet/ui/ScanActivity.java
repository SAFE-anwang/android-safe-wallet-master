/*
 * Copyright 2012-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

import de.schildbach.wallet.camera.CameraManager;
import de.schildbach.wallet.R;

public class ScanActivity extends Activity implements SurfaceHolder.Callback,
        CompoundButton.OnCheckedChangeListener {
    public static final String INTENT_EXTRA_RESULT = "result";
    public static final int FromGalleryRequestCode = 1606;

    private static final long VIBRATE_DURATION = 50L;
    private static final long AUTO_FOCUS_INTERVAL_MS = 2500L;

    private final CameraManager cameraManager = new CameraManager();
    protected ScannerView scannerView;
    private SurfaceHolder surfaceHolder;
    protected FrameLayout flOverlayContainer;
    protected ImageButton ibtnGallery;
    private Vibrator vibrator;
    private HandlerThread cameraThread;
    private Handler cameraHandler;

    private boolean fromGallery;

    private static boolean DISABLE_CONTINUOUS_AUTOFOCUS = Build.MODEL.equals("GT-I9100") //
            // Galaxy S2
            || Build.MODEL.equals("SGH-T989") // Galaxy S2
            || Build.MODEL.equals("SGH-T989D") // Galaxy S2 X
            || Build.MODEL.equals("SAMSUNG-SGH-I727") // Galaxy S2 Skyrocket
            || Build.MODEL.equals("GT-I9300") // Galaxy S3
            || Build.MODEL.equals("GT-N7000"); // Galaxy Note

    private static final Logger log = LoggerFactory.getLogger(ScanActivity.class);

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        setContentView(R.layout.scan_activity);
        flOverlayContainer = (FrameLayout) findViewById(R.id.fl_overlay_container);
        scannerView = (ScannerView) findViewById(R.id.scan_activity_mask);
        ibtnGallery = (ImageButton) findViewById(R.id.ibtn_gallery);
        ibtnGallery.setOnClickListener(galleryClick);
        ((CheckBox) findViewById(R.id.cbx_torch)).setOnCheckedChangeListener(this);
        fromGallery = false;
    }

    public void setOverlay(View v) {
        flOverlayContainer.removeAllViews();
        flOverlayContainer.addView(v, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    public void setOverlay(int resource) {
        setOverlay(LayoutInflater.from(this).inflate(resource, null));
    }

    @Override
    protected void onResume() {
        super.onResume();

        cameraThread = new HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND);
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        final SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scan_activity_preview);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        cameraHandler.post(openRunnable);
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder, final int format,
                               final int width, final int height) {
    }

    @Override
    protected void onPause() {
        cameraHandler.post(closeRunnable);

        surfaceHolder.removeCallback(this);

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:
                // don't launch camera app
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                cameraHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cameraManager
                                .setTorch(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                    }
                });
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    public void handleResult(final Result scanResult, Bitmap thumbnailImage,
                             final float thumbnailScaleFactor) {
        vibrate();
        // superimpose dots to highlight the key features of the qr code
        final ResultPoint[] points = scanResult.getResultPoints();
        if (points != null && points.length > 0) {
            final Paint paint = new Paint();
            paint.setColor(getResources().getColor(R.color.scan_result_dots));
            paint.setStrokeWidth(10.0f);

            final Canvas canvas = new Canvas(thumbnailImage);
            canvas.scale(thumbnailScaleFactor, thumbnailScaleFactor);
            for (final ResultPoint point : points)
                canvas.drawPoint(point.getX(), point.getY(), paint);
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        thumbnailImage = Bitmap.createBitmap(thumbnailImage, 0, 0,
                thumbnailImage.getWidth(), thumbnailImage.getHeight(), matrix,
                false);
        scannerView.drawResultBitmap(thumbnailImage);

        final Intent result = getIntent();
        result.putExtra(INTENT_EXTRA_RESULT, scanResult.getText());
        setResult(RESULT_OK, result);

        // delayed finish
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    public void vibrate() {
        vibrator.vibrate(VIBRATE_DURATION);
    }

    private final Runnable openRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                final Camera camera = cameraManager.open(surfaceHolder,
                        !DISABLE_CONTINUOUS_AUTOFOCUS);

                final Rect framingRect = cameraManager.getFrame();
                final Rect framingRectInPreview = cameraManager
                        .getFramePreview();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerView.setFraming(framingRect,
                                framingRectInPreview);
                    }
                });

                final String focusMode = camera.getParameters().getFocusMode();
                final boolean nonContinuousAutoFocus = Camera.Parameters.FOCUS_MODE_AUTO
                        .equals(focusMode)
                        || Camera.Parameters.FOCUS_MODE_MACRO.equals(focusMode);

                if (nonContinuousAutoFocus)
                    cameraHandler.post(new AutoFocusRunnable(camera));

                cameraHandler.post(fetchAndDecodeRunnable);
            } catch (final Exception x) {
                log.info("problem opening camera", x);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing())
                            WarnDialogFragment.newInstance(R.string.scan_camera_problem_dialog_title, getString(R.string.scan_camera_problem_dialog_message))
                                    .show(getFragmentManager(), "dialog");
                    }
                });
            }
        }
    };

    private final Runnable closeRunnable = new Runnable() {
        @Override
        public void run() {
            cameraManager.close();

            // cancel background thread
            cameraHandler.removeCallbacksAndMessages(null);
            cameraThread.quit();
        }
    };

    @Override
    public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
        if(buttonView.getId() == R.id.cbx_torch){
            if(cameraHandler == null){
                return;
            }
            cameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    cameraManager
                            .setTorch(isChecked);
                }
            });
        }
    }

    private final class AutoFocusRunnable implements Runnable {
        private final Camera camera;

        public AutoFocusRunnable(final Camera camera) {
            this.camera = camera;
        }

        @Override
        public void run() {
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(final boolean success,
                                        final Camera camera) {
                    // schedule again
                    cameraHandler.postDelayed(AutoFocusRunnable.this,
                            AUTO_FOCUS_INTERVAL_MS);
                }
            });
        }
    }

    private View.OnClickListener galleryClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            fromGallery = true;
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, FromGalleryRequestCode);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (requestCode == FromGalleryRequestCode) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private final Runnable fetchAndDecodeRunnable = new Runnable() {
        private final QRCodeReader reader = new QRCodeReader();
        private final Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType,
                Object>(DecodeHintType.class);

        @Override
        public void run() {
            if (fromGallery) {
                cameraHandler.postDelayed(fetchAndDecodeRunnable, 500);
                return;
            }
            cameraManager.requestPreviewFrame(new PreviewCallback() {
                @Override
                public void onPreviewFrame(final byte[] data, final Camera camera) {
                    decode(data);
                }
            });
        }

        private void decode(final byte[] data) {
            final PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data);
            final BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK,
                        new ResultPointCallback() {
                            @Override
                            public void foundPossibleResultPoint(
                                    final ResultPoint dot) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        scannerView.addDot(dot);
                                    }
                                });
                            }
                        });
                final Result scanResult = reader.decode(bitmap, hints);
                if (!resultValid(scanResult.getText())) {
                    cameraHandler.post(fetchAndDecodeRunnable);
                    return;
                }
                final int thumbnailWidth = source.getThumbnailWidth();
                final int thumbnailHeight = source.getThumbnailHeight();
                final float thumbnailScaleFactor = (float) thumbnailWidth
                        / source.getWidth();

                final Bitmap thumbnailImage = Bitmap.createBitmap(
                        thumbnailWidth, thumbnailHeight,
                        Bitmap.Config.ARGB_8888);
                thumbnailImage.setPixels(source.renderThumbnail(), 0,
                        thumbnailWidth, 0, 0, thumbnailWidth, thumbnailHeight);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleResult(scanResult, thumbnailImage,
                                thumbnailScaleFactor);
                    }
                });
            } catch (final Exception x) {
                cameraHandler.post(fetchAndDecodeRunnable);
            } finally {
                reader.reset();
            }
        }
    };

    public boolean resultValid(String result) {
        return true;
    }

    public final void startScan() {
        cameraHandler.post(fetchAndDecodeRunnable);
    }

    public void finish() {
        super.finish();
    }

    private String decodeQrCodeFromBitmap(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int[] pixels = new int[width * height];
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
        bmp.recycle();
        bmp = null;
        QRCodeReader reader = new QRCodeReader();
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(new
                    RGBLuminanceSource(width, height, pixels))), hints);
            return result.getText();
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (ChecksumException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static class WarnDialogFragment extends DialogFragment {
        public static WarnDialogFragment newInstance(final int titleResId, final String message) {
            final WarnDialogFragment fragment = new WarnDialogFragment();
            final Bundle args = new Bundle();
            args.putInt("title", titleResId);
            args.putString("message", message);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Bundle args = getArguments();
            final DialogBuilder dialog = DialogBuilder.warn(getActivity(), args.getInt("title"));
            dialog.setMessage(args.getString("message"));
            dialog.singleDismissButton(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    getActivity().finish();
                }
            });
            return dialog.create();
        }

        @Override
        public void onCancel(final DialogInterface dialog) {
            getActivity().finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setContentView(R.layout.fragment_empty);
    }

}
