package de.schildbach.wallet.ui.safe;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.Wallet;

import java.sql.SQLException;
import java.util.Map;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.ui.RequestCoinsActivity;
import de.schildbach.wallet.ui.safe.utils.AssetCoin;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.R;

/**
 * 资产顶部页面
 * @author zhangmiao
 */
public class AssetTopFragment extends BaseFragment {

    private CurrencyTextView totalAmount;
    private CurrencyTextView enableAmount;
    private CurrencyTextView waitAmount;
    private CurrencyTextView lockAmount;

    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;

    private String assetId = SafeConstant.SAFE_FLAG;
    private long decimals = Coin.SMALLEST_UNIT_EXPONENT;

    private LoaderManager loaderManager;
    private static final int ID_BALANCE_LOADER = 0;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_asset_top;
    }

    @Override
    public void initView() {
        super.initView();

        totalAmount = (CurrencyTextView) findViewById(R.id.total_amount);
        enableAmount = (CurrencyTextView) findViewById(R.id.enable_amount);
        waitAmount = (CurrencyTextView) findViewById(R.id.wait_amount);
        lockAmount = (CurrencyTextView) findViewById(R.id.lock_amount);

    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        this.application = (WalletApplication) getActivity().getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        loaderManager = getLoaderManager();
    }

    public void refreshUI(String assetId) {
        this.assetId = assetId;
        if (!isSafe()) {
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                IssueData issue = (IssueData) dao.queryForFirst("assetId", assetId);
                this.decimals = issue.decimals;
            } catch (SQLException e) {
                getActivity().finish();
            }
        }
        loaderManager.restartLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
    }

    @Override
    public void onResume() {
        super.onResume();
        loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_BALANCE_LOADER);
        super.onPause();
    }

    private final LoaderManager.LoaderCallbacks<Map<String, Coin>> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<Map<String, Coin>>() {

        @Override
        public Loader<Map<String, Coin>> onCreateLoader(final int id, final Bundle args) {
            return new AssetIdBalanceLoader(getActivity(), wallet, assetId);
        }

        @Override
        public void onLoadFinished(final Loader<Map<String, Coin>> loader, final Map<String, Coin> balance) {
            Coin allCoin = balance.get("allCoin");
            Coin enableCoin = balance.get("availableCoin");
            Coin lockCoin = balance.get("lockCoin");
            Coin waitCoin = allCoin.subtract(enableCoin).subtract(lockCoin);
            if (!isSafe()) {
                totalAmount.setFormat(Constants.getAssetFormat((int) decimals));
                enableAmount.setFormat(Constants.getAssetFormat((int) decimals));
                waitAmount.setFormat(Constants.getAssetFormat((int) decimals));
                lockAmount.setFormat(Constants.getAssetFormat((int) decimals));
                totalAmount.setAmount(new AssetCoin(allCoin.getValue(), (int) decimals));
                enableAmount.setAmount(new AssetCoin(enableCoin.getValue(), (int) decimals));
                waitAmount.setAmount(new AssetCoin(waitCoin.getValue(), (int) decimals));
                lockAmount.setAmount(new AssetCoin(lockCoin.getValue(), (int) decimals));
            } else {
                totalAmount.setFormat(config.getFormat().noCode());
                enableAmount.setFormat(config.getFormat().noCode());
                waitAmount.setFormat(config.getFormat().noCode());
                lockAmount.setFormat(config.getFormat().noCode());
                totalAmount.setAmount(allCoin);
                enableAmount.setAmount(enableCoin);
                if (waitCoin.signum() >= 0) {
                    waitAmount.setAmount(waitCoin);
                } else {
                    waitAmount.setAmount(Coin.ZERO);
                }
                lockAmount.setAmount(lockCoin);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Map<String, Coin>> loader) {

        }

    };

    //是否SAFE
    public boolean isSafe() {
        if (TextUtils.isEmpty(assetId) || assetId.equalsIgnoreCase(SafeConstant.SAFE_FLAG)) {
            assetId = SafeConstant.SAFE_FLAG;
            return true;
        } else {
            return false;
        }
    }

}
