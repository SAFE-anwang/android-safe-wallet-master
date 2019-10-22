package de.schildbach.wallet.ui.safe;

import android.content.*;
import android.text.format.DateUtils;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.WalletAssetTx;
import de.schildbach.wallet.util.*;

import android.support.v4.content.LocalBroadcastManager;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * 交易记录异步加载
 *
 * @author zhangmiao
 */
public class TransactionsLoader extends AsyncTaskLoader<List<Transaction>> {

    private Wallet wallet;
    private TransactionFragment.Direction direction;
    private String assetId;

    public TransactionsLoader(Context context, Wallet wallet, @Nullable TransactionFragment.Direction direction, String assetId) {
        super(context);
        this.wallet = wallet;
        this.direction = direction;
        this.assetId = assetId;
        LocalBroadcastManager.getInstance(this.getContext()).registerReceiver(walletChangeReceiver,
                new IntentFilter(WalletApplication.ACTION_WALLET_REFERENCE_CHANGED));
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, walletChangeListener);
        walletChangeListener.onReorganize(null); // trigger at least one reload
        safeForceLoad();
    }

    public @Nullable
    TransactionFragment.Direction getDirection() {
        return direction;
    }

    @Override
    protected void onStopLoading() {
        LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();
        super.onStopLoading();
    }

    @Override
    protected void onReset() {
        LocalBroadcastManager.getInstance(this.getContext()).unregisterReceiver(walletChangeReceiver);
        wallet.removeChangeEventListener(walletChangeListener);
        wallet.removeCoinsSentEventListener(walletChangeListener);
        wallet.removeCoinsReceivedEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();
        super.onReset();
    }

    @Override
    public List<Transaction> loadInBackground() {
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
        final Set<Transaction> transactions = wallet.getTransactions(true);
        final List<Transaction> filteredTransactions = new ArrayList<Transaction>(transactions.size());

        if (assetId.equals(SafeConstant.SAFE_FLAG)) { //safe交易
            for (final Transaction tx : transactions) {
                if (tx.isSafeTx() || tx.isIssue()) {
//                    final boolean sent = tx.getValue(wallet).signum() < 0;
//                    final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;
//                    if ((direction == TransactionFragment.Direction.RECEIVED && !sent && !isInternal) || direction == null
//                            || (direction == TransactionFragment.Direction.SENT && sent && !isInternal)) {
//                        filteredTransactions.add(tx);
//                    }
                    filteredTransactions.add(tx);
                }
            }
        } else { //资产交易
            List<String> txList = new ArrayList<>();
            BaseDaoImpl assetTxDao = new BaseDaoImpl(WalletAssetTx.class);
            try {
                List<WalletAssetTx> assetTxList = assetTxDao.queryForAll();
                for (WalletAssetTx item : assetTxList) {
                    if (assetId.equals(item.assetId) && !txList.contains(item.txId)) {//这里需要去重
                        txList.add(item.txId);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            for (final Transaction tx : transactions) {
                String txId = tx.getHashAsString();
                if (txList.contains(txId)) {
//                    final boolean sent = tx.getValue(wallet).signum() < 0;
//                    final boolean isInternal = tx.getPurpose() == Purpose.KEY_ROTATION;
//                    if ((direction == TransactionFragment.Direction.RECEIVED && !sent && !isInternal) || direction == null
//                            || (direction == TransactionFragment.Direction.SENT && sent && !isInternal)) {
//                        filteredTransactions.add(tx);
//                    }
                    filteredTransactions.add(tx);
                }
            }
        }
        Collections.sort(filteredTransactions, TRANSACTION_COMPARATOR);
        return filteredTransactions;
    }

    private ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener(
            DateUtils.SECOND_IN_MILLIS, true, true, false) {
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

    private static final Comparator<Transaction> TRANSACTION_COMPARATOR = new Comparator<Transaction>() {
        @Override
        public int compare(final Transaction tx1, final Transaction tx2) {
            final boolean pending1 = tx1.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
            final boolean pending2 = tx2.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;

            if (pending1 != pending2)
                return pending1 ? -1 : 1;

            final Date updateTime1 = tx1.getUpdateTime();
            final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
            final Date updateTime2 = tx2.getUpdateTime();
            final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

            if (time1 != time2)
                return time1 > time2 ? -1 : 1;

            return tx1.getHash().compareTo(tx2.getHash());
        }
    };

}