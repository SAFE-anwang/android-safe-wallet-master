package de.schildbach.wallet.ui.safe;

import android.content.*;
import android.support.v4.content.LocalBroadcastManager;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Address;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;

import java.util.concurrent.RejectedExecutionException;

/**
 * 钱包地址异步加载
 * @author zhangmiao
 */
public class CurrentAddressLoader extends AsyncTaskLoader<Address> {

    private Wallet wallet;
    private Configuration config;

    public CurrentAddressLoader(Context context, Wallet wallet, Configuration config) {
        super(context);
        this.wallet = wallet;
        this.config = config;
        LocalBroadcastManager.getInstance(this.getContext()).registerReceiver(walletChangeReceiver,
                new IntentFilter(WalletApplication.ACTION_WALLET_REFERENCE_CHANGED));
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, walletChangeListener);
        config.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        safeForceLoad();
    }

    @Override
    protected void onStopLoading() {
        config.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();
        super.onStopLoading();
    }

    @Override
    protected void onReset() {
        config.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();
        super.onReset();
    }

    @Override
    public Address loadInBackground() {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
        return wallet.currentReceiveAddress();
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

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (Configuration.PREFS_KEY_OWN_NAME.equals(key))
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