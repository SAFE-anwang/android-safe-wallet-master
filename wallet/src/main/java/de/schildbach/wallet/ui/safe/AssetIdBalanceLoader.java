package de.schildbach.wallet.ui.safe;

import android.content.*;
import android.support.v4.content.LocalBroadcastManager;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * 一个资产余额异步加载
 * @author zhangmiao
 */
public class AssetIdBalanceLoader extends AsyncTaskLoader<Map<String, Coin>> {

    private Wallet wallet;
    private String assetId;

    public AssetIdBalanceLoader(Context context, Wallet wallet, String assetId) {
        super(context);
        this.wallet = wallet;
        this.assetId = assetId;
        LocalBroadcastManager.getInstance(this.getContext()).registerReceiver(walletChangeReceiver,
                new IntentFilter(WalletApplication.ACTION_WALLET_REFERENCE_CHANGED));
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addTransactionConfidenceEventListener(Threading.SAME_THREAD, walletChangeListener);
        safeForceLoad();
    }

    @Override
    protected void onStopLoading() {
        LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        wallet.removeTransactionConfidenceEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();
        super.onStopLoading();
    }

    @Override
    protected void onReset() {
        LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        wallet.removeTransactionConfidenceEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();
        super.onReset();
    }

    @Override
    public Map<String, Coin> loadInBackground() {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
        Map<String, Coin> coinMap = new HashMap<>();
        coinMap.put("allCoin", wallet.getBalance(BalanceType.ESTIMATED, assetId, null));
        coinMap.put("availableCoin", wallet.getBalance(BalanceType.AVAILABLE_SPENDABLE, assetId, null));
        coinMap.put("lockCoin", wallet.getLockBalance(BalanceType.ESTIMATED, assetId, null));
        return coinMap;
    }

    private ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            safeForceLoad();
        }
    };

    private BroadcastReceiver walletChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            safeForceLoad();
        }
    };

    private void safeForceLoad() {
        try {
            forceLoad();
        } catch (final RejectedExecutionException e) {
        }
    }
}
