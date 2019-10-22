package de.schildbach.wallet.ui.safe.utils;

public class ThreadPoolProxyFactory {

    public static ThreadPoolProxy mNormalThreadPoolProxy;
    public static ThreadPoolProxy mCandyThreadPoolProxy;
    public static ThreadPoolProxy mReceivedThreadPoolProxy;

    public static ThreadPoolProxy getNormalThreadPoolProxy() {
        if (mNormalThreadPoolProxy == null) {
            synchronized (ThreadPoolProxyFactory.class) {
                if (mNormalThreadPoolProxy == null) {
                    mNormalThreadPoolProxy = new ThreadPoolProxy(3, 3);
                }
            }
        }
        return mNormalThreadPoolProxy;
    }

    public static ThreadPoolProxy getCandyThreadPoolProxy() {
        if (mCandyThreadPoolProxy == null) {
            synchronized (ThreadPoolProxyFactory.class) {
                if (mCandyThreadPoolProxy == null) {
                    mCandyThreadPoolProxy = new ThreadPoolProxy(4, 4);
                }
            }
        }
        return mCandyThreadPoolProxy;
    }

    public static ThreadPoolProxy getReceivedThreadPoolProxy() {
        if (mReceivedThreadPoolProxy == null) {
            synchronized (ThreadPoolProxyFactory.class) {
                if (mReceivedThreadPoolProxy == null) {
                    mReceivedThreadPoolProxy = new ThreadPoolProxy(5, 5);
                }
            }
        }
        return mReceivedThreadPoolProxy;
    }

}