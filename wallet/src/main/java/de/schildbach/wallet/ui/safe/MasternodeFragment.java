package de.schildbach.wallet.ui.safe;

import android.annotation.SuppressLint;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.R;
import org.bitcoinj.core.Context;
import org.bitcoinj.utils.SharedPreferencesUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主节点
 * @author zhangmiao
 */
public class MasternodeFragment extends BaseFragment {

    private WebView mWebView;
    private ProgressBar proBar;
    private static String MASTERNODE_FILE = "MASTER_NODE_FILE";
    private static String MASTERNODE_KEY = "MASTER_NODE_KEY";

    private static final Logger log = LoggerFactory.getLogger(MasternodeFragment.class);

    private SharedPreferencesUtils spUtils = new SharedPreferencesUtils(WalletApplication.getInstance(), MASTERNODE_FILE);

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_masternode;
    }

    @Override
    public void initView() {
        super.initView();
        proBar = (ProgressBar) findViewById(R.id.proBar);
        mWebView = (WebView) findViewById(R.id.webView);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.safe_masternode_title));
        mWebView.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.bg_bright));
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

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return super.onJsConfirm(view, url, message, result);
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                      JsPromptResult result) {
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });
        mWebView.setWebViewClient(new MyWebViewClient());
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setSupportZoom(false); //自适应屏幕
        webSettings.setJavaScriptEnabled(true); //JavaScript可用
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        mWebView.addJavascriptInterface(new JavaScriptAndroid(), "android");
        webSettings.setBuiltInZoomControls(true);
        webSettings.setUseWideViewPort(true);//这个很关键
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);//不使用缓存，只从网络获取数据
        webSettings.setDomStorageEnabled(true);
//        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAllowFileAccess(true);

        mWebView.loadUrl(Constants.getBlockExplorer() + "masterNodeApp");
    }

    class MyWebViewClient extends WebViewClient {

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                       SslError error) {
            handler.proceed();// 接受所有网站的证书
        }


        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;   //android 8.0 必须 return true，或者js调用原生代码不起作用。
        }

    }

    public class JavaScriptAndroid {

        @JavascriptInterface
        public int saveMasternode(final String data) {
            log.info("---------saveMasternode value = {}", data);
            String value = spUtils.get(MASTERNODE_KEY, "[]");
            try {
                JSONArray arrValue = new JSONArray(value);
                JSONObject item = new JSONObject(data);
                arrValue.put(item);
                spUtils.put(MASTERNODE_KEY, arrValue.toString());
                return 1;
            } catch (JSONException e) {
                return 0;
            }

        }

        @JavascriptInterface
        public String getMasternode() {
            String value = spUtils.get(MASTERNODE_KEY, "[]");
            log.info("---------getMasternode value = {}", value);
            return value;
        }

        @JavascriptInterface
        public int deleteMasternode(String data) {
            log.info("---------deleteMasternode value = {}", data);
            String value = spUtils.get(MASTERNODE_KEY, "[]");
            try {
                JSONArray arrRet = new JSONArray();
                JSONArray arrValue = new JSONArray(value);
                JSONObject deleteItem = new JSONObject(data);
                for (int i = 0; i < arrValue.length(); i++) {
                    JSONObject nodeItem = arrValue.optJSONObject(i);
                    if(deleteItem.optString("address").equals(nodeItem.optString("address"))
                            && deleteItem.optString("ip").equals(nodeItem.optString("ip"))){
                        continue;
                    }
                    arrRet.put(nodeItem);
                }
                spUtils.put(MASTERNODE_KEY, arrRet.toString());
                return 1;
            } catch (Exception e) {
                return 0;
            }
        }
    }

}
