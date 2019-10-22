package de.schildbach.wallet.ui.safe;

import android.net.http.SslError;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.webkit.*;
import android.widget.ImageView;
import android.widget.ProgressBar;

import de.schildbach.wallet.R;

/**
 * 论坛
 * @author zhangmiao
 */
public class ForumActivity extends AppCompatActivity {

    private WebView mWebView;
    private ProgressBar proBar;
    private ImageView image;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_forum);
        image = (ImageView) findViewById(R.id.image);
        proBar = (ProgressBar) findViewById(R.id.proBar);
        mWebView = (WebView) findViewById(R.id.webView);
        initData(savedInstanceState);
    }

    public void initData(Bundle savedInstanceState) {
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mWebView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_bright));
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    proBar.setVisibility(View.VISIBLE);
                    proBar.setProgress(newProgress);
                } else {
                    proBar.setProgress(100);
                    proBar.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            proBar.setVisibility(View.GONE);
                        }
                    }, 500);
                }
                super.onProgressChanged(view, newProgress);
            }
        });
        mWebView.setWebViewClient(new MyWebViewClient());
        WebSettings webSettings = mWebView.getSettings();
        //自适应屏幕
        webSettings.setSupportZoom(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setUseWideViewPort(true);//这个很关键
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAllowFileAccess(true);
        mWebView.loadUrl("http://www.anwang.org");
    }

    class MyWebViewClient extends WebViewClient {

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                       SslError error) {
            handler.proceed();// 接受所有网站的证书
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            finish();
        }
    }
}
