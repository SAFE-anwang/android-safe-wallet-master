package de.schildbach.wallet.ui.safe;

import android.content.*;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.db.WalletAssetTx;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * 所有资产余额异步加载
 * @author zhangmiao
 */
public class AllAssetBalanceLoader extends AsyncTaskLoader<List<IssueData>> {

    private Wallet wallet;

    public static boolean INIT_LOADER_FINISH = false;

    public AllAssetBalanceLoader(Context context, Wallet wallet) {
        super(context);
        this.wallet = wallet;
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
    public List<IssueData> loadInBackground() {

        IssueData safeIssue = new IssueData();
        safeIssue.assetId = SafeConstant.SAFE_FLAG;
        safeIssue.assetName = SafeConstant.SAFE_FLAG;
        safeIssue.shortName = SafeConstant.SAFE_FLAG;
        safeIssue.decimals = Coin.SMALLEST_UNIT_EXPONENT;

        List<IssueData> resultList = new ArrayList<>();
        LinkedHashMap<String, IssueData> resultMap = new LinkedHashMap<>();
        try {
            BaseDaoImpl assetTxDao = new BaseDaoImpl(WalletAssetTx.class);
            List<WalletAssetTx> assetTxList = assetTxDao.getDao().queryBuilder().selectColumns("assetId").distinct().query();
            BaseDaoImpl issueDao = new BaseDaoImpl(IssueData.class);
            for (WalletAssetTx item : assetTxList) {
                IssueData issueObj = (IssueData) issueDao.queryForFirst("assetId", item.assetId);
                if (issueObj != null) {
                    resultMap.put(issueObj.assetId, issueObj);
                }
            }
        } catch (SQLException e) {
        }
        //找到未花费到所有输出
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
        List<TransactionOutput> allOutput = wallet.calculateAllSpendCandidatesAndAsset(false, false);
        for (TransactionOutput output : allOutput) {
            if (output.isSafeTx()) {
                safeIssue.balanceList.add(output);
            } else {
                String assetId = output.getCurrentAssetId();
                if (resultMap.containsKey(assetId)) {
                    resultMap.get(assetId).balanceList.add(output);
                }
            }
        }

        Coin safeValue = Coin.ZERO; //SAFE总额
        for (TransactionOutput output : safeIssue.balanceList) {
            safeValue = safeValue.add(output.getValue());
        }
        safeIssue.balance = safeValue;
        resultList.add(0, safeIssue);

        for (IssueData item : resultMap.values()) {
            Coin assetValue = Coin.ZERO; //资产总额
            for (TransactionOutput output : item.balanceList) {
                assetValue = assetValue.add(output.getValue());
            }
            item.balance = assetValue;
            item.balanceList.clear();
            resultList.add(item);
        }
        return resultList;
    }

    private ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            if (INIT_LOADER_FINISH) {
                safeForceLoad();
            }

        }
    };

    private BroadcastReceiver walletChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (INIT_LOADER_FINISH) {
                safeForceLoad();
            }
        }
    };

    private void safeForceLoad() {
        try {
            forceLoad();
        } catch (final RejectedExecutionException e) {
        }
    }
}
