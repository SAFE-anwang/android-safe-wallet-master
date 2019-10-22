package de.schildbach.wallet.ui.safe.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;

/**
 * 公用方法
 * @author zhangmiao
 */

public class CommonUtils {

    private static long lastClickTime;
    private static LockProgressDialog dialog;
    private static final int MIN_CLICK_DELAY_TIME = 500;

    public static boolean isRepeatClick() {
        boolean flag = false;
        long curClickTime = System.currentTimeMillis();
        if ((curClickTime - lastClickTime) >= MIN_CLICK_DELAY_TIME) {
            flag = true;
        }
        lastClickTime = curClickTime;
        return flag;
    }

    public static void showProgressDialog(Context mContext, final String showStr, final boolean cancelable) {
        if (mContext != null) {
            if (dialog == null) {
                dialog = new LockProgressDialog(mContext.getApplicationContext());
                dialog.setTitle(showStr);
                dialog.setCancelable(cancelable);
                dialog.setCanceledOnTouchOutside(cancelable);
                dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface d, int keyCode, KeyEvent event) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            if(cancelable) {
                                dialog.dismiss();
                                dialog = null;
                            }
                        }
                        return false;
                    }
                });
                dialog.show();
            } else {
                if (!dialog.isShowing()) {
                    dialog.show();
                }
            }
        }
    }


    public static void showProgressDialog(Context mContext, final String showStr) {
        showProgressDialog(mContext, showStr, false);
    }

    public static void dismissProgressDialog(Context mContext) {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    /**
     * 获取屏幕宽度
     */
    public static int getScreenW(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        return w;
    }

    /**
     * 获取屏幕高度
     */
    public static int getScreenH(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int h = dm.heightPixels;
        return h;
    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dip2px(Context context, float dpValue) {
        Resources r = context.getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dpValue, r.getDisplayMetrics());
        return (int) px;
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    

}
