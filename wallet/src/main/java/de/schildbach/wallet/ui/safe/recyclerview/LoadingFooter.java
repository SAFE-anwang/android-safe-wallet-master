package de.schildbach.wallet.ui.safe.recyclerview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewStub;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import de.schildbach.wallet.R;

/**
 * 点击底部加载
 */
public class LoadingFooter extends RelativeLayout {

    protected State mState = State.LoadComplete;
    private View mLoadingView;
    private View mNetworkErrorView;
    private View mTheEndView;
    private ProgressBar mLoadingProgress;
    private TextView mLoadingText;

    public LoadingFooter(Context context) {
        super(context);
        init(context);
    }

    public LoadingFooter(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LoadingFooter(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void init(Context context) {
        inflate(context, R.layout.common_list_footer, this);
        setOnClickListener(null);
        setState(State.LoadComplete, true);
    }

    public State getState() {
        return mState;
    }

    public void setState(State status ) {
        setState(status, true);
    }

    /**
     * 设置状态
     * @param status
     * @param show 是否展示当前View
     */
    public void setState(State status, boolean show) {
        if (mState == status) {
            return;
        }
        mState = status;
        switch (status) {
            case LoadComplete:
                setOnClickListener(null);
                if (mLoadingView != null) {
                    mLoadingView.setVisibility(GONE);
                }
                if (mTheEndView != null) {
                    mTheEndView.setVisibility(GONE);
                }
                if (mNetworkErrorView != null) {
                    mNetworkErrorView.setVisibility(GONE);
                }
                break;
            case Loading:
                setOnClickListener(null);
                if (mTheEndView != null) {
                    mTheEndView.setVisibility(GONE);
                }
                if (mNetworkErrorView != null) {
                    mNetworkErrorView.setVisibility(GONE);
                }
                if (mLoadingView == null) {
                    ViewStub viewStub = (ViewStub) findViewById(R.id.loading_viewstub);
                    mLoadingView = viewStub.inflate();
                    mLoadingProgress = (ProgressBar) mLoadingView.findViewById(R.id.loading_progress);
                    mLoadingText = (TextView) mLoadingView.findViewById(R.id.loading_text);
                } else {
                    mLoadingView.setVisibility(VISIBLE);
                }
                mLoadingView.setVisibility(show ? VISIBLE : GONE);
                mLoadingProgress.setVisibility(View.VISIBLE);
                mLoadingText.setText(R.string.loadmore_loading);
                break;
            case TheEnd:
                setOnClickListener(null);
                if (mLoadingView != null) {
                    mLoadingView.setVisibility(GONE);
                }
                if (mNetworkErrorView != null) {
                    mNetworkErrorView.setVisibility(GONE);
                }
                if (mTheEndView == null) {
                    ViewStub viewStub = (ViewStub) findViewById(R.id.end_viewstub);
                    mTheEndView = viewStub.inflate();
                } else {
                    mTheEndView.setVisibility(VISIBLE);
                }
                mTheEndView.setVisibility(show ? VISIBLE : GONE);
                break;
            case NetWorkError:
                if (mLoadingView != null) {
                    mLoadingView.setVisibility(GONE);
                }
                if (mTheEndView != null) {
                    mTheEndView.setVisibility(GONE);
                }
                if (mNetworkErrorView == null) {
                    ViewStub viewStub = (ViewStub) findViewById(R.id.network_error_viewstub);
                    mNetworkErrorView = viewStub.inflate();
                } else {
                    mNetworkErrorView.setVisibility(VISIBLE);
                }
                mNetworkErrorView.setVisibility(show ? VISIBLE : GONE);

                break;
            default:
                break;
        }
    }

    public enum State {
        LoadComplete,
        TheEnd,
        Loading,
        NetWorkError
    }

}