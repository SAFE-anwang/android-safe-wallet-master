package de.schildbach.wallet.ui.safe.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/**
 * 需要自己控制生命周期，在这个生命周期内都可以使用这个线程
 */
public class BackgroundThread extends HandlerThread {

    private static BackgroundThread mInstance;
    private static Handler mHandler;
    private static Handler uiHandler;

    public BackgroundThread() {
        super("backgroundThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    }

    public static void prepareThread() {
        if (mInstance == null) {
            mInstance = new BackgroundThread();
            // 创建HandlerThread后一定要记得start()
            mInstance.start();
            // 获取HandlerThread的Looper,创建Handler，通过Looper初始化
            mHandler = new Handler(mInstance.getLooper());
            uiHandler = new Handler(Looper.myLooper());
        }
    }

    /**
     * 如果需要在后台线程做一件事情，那么直接调用post方法，使用非常方便
     */
    public static void post(final Runnable runnable) {
        if (runnable != null && mHandler == null) {
            prepareThread();
        }
        mHandler.post(runnable);
    }

    public static void postDelayed(final Runnable runnable, long nDelay) {
        mHandler.postDelayed(runnable, nDelay);
    }

    public static void postUiThread(final Runnable runnable) {
        if (uiHandler != null) {
            uiHandler.post(runnable);
        }
    }

    /**
     * 退出HandlerThread
     */
    public static void destroyThread() {
        if (mInstance != null) {
            mInstance.quit();
            mHandler.removeCallbacksAndMessages(null);
            uiHandler.removeCallbacksAndMessages(null);
            mInstance = null;
            mHandler = null;
            uiHandler = null;
        }
    }

    /**
     * 防止内存泄漏
     */
    public static class MyBgThread implements Runnable {

        ThreadCallBack callBack;

        public MyBgThread(ThreadCallBack callBack) {
            this.callBack = callBack;
        }

        @Override
        public void run() {
            if (callBack != null) {
                callBack.run();
            }
        }

        public interface ThreadCallBack {
            void run();
        }

    }
}