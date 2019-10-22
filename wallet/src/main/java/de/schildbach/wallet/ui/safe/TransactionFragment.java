package de.schildbach.wallet.ui.safe;

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import android.widget.ViewAnimator;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.ui.safe.bean.AddressBook;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.RecyclerViewDivider;
import de.schildbach.wallet.ui.*;
import de.schildbach.wallet.ui.safe.TransactionAdapter.Warning;
import de.schildbach.wallet.util.*;
import de.schildbach.wallet.R;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.sql.SQLException;
import java.util.*;

/**
 * 资产交易列表
 * @author zhangmiao
 */
public class TransactionFragment extends BaseFragment implements LoaderCallbacks<List<Transaction>>,
        TransactionAdapter.OnClickListener, OnSharedPreferenceChangeListener {

    public enum Direction {
        RECEIVED, SENT
    }

    private String assetId;
    private IssueData issue;

    private Direction direction;
    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private ContentResolver resolver;
    private LoaderManager loaderManager;
    private DevicePolicyManager devicePolicyManager;

    private ViewAnimator viewGroup;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private TransactionAdapter adapter;

    private final Handler handler = new Handler();

    private static final int ID_TRANSACTION_LOADER = 0;
    private static final String ARG_DIRECTION = "direction";


    private final ContentObserver addressBookObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            adapter.clearCacheAndNotifyDataSetChanged();
        }
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventBus.getDefault().register(this);
        this.activity = (AbstractWalletActivity) getActivity();
        this.application = (WalletApplication) this.activity.getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.resolver = activity.getContentResolver();
        this.loaderManager = getLoaderManager();
        this.devicePolicyManager = (DevicePolicyManager) application.getSystemService(Context.DEVICE_POLICY_SERVICE);

        setHasOptionsMenu(true);

        Bundle args = getArguments();
        assetId = args.getString("assetId");
        long decimals;
        if(isSafe()){
            decimals = Coin.SMALLEST_UNIT_EXPONENT;
        } else {
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                issue = (IssueData) dao.queryForFirst("assetId", assetId);
            } catch (SQLException e) {
                getActivity().finish();
            }
            decimals = issue.decimals;
        }
        adapter = new TransactionAdapter(assetId, decimals, activity, wallet, this);
        this.direction = null;

    }

    @Override
    public int getLayoutResId() {
        return R.layout.wallet_transactions_fragment;
    }

    @Override
    public void initView() {
        super.initView();
        viewGroup = (ViewAnimator) findViewById(R.id.wallet_transactions_group);
        emptyView = (TextView) findViewById(R.id.wallet_transactions_empty);
        recyclerView = (RecyclerView) findViewById(R.id.wallet_transactions_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.addItemDecoration(new RecyclerViewDivider(activity, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        resolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true,
                addressBookObserver);
        config.registerOnSharedPreferenceChangeListener(this);
        final Bundle args = new Bundle();
        args.putSerializable(ARG_DIRECTION, direction);
        loaderManager.initLoader(ID_TRANSACTION_LOADER, args, this);
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addTransactionConfidenceEventListener(Threading.SAME_THREAD, transactionChangeListener);
        updateView();
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        wallet.removeTransactionConfidenceEventListener(transactionChangeListener);
        wallet.removeChangeEventListener(transactionChangeListener);
        wallet.removeCoinsSentEventListener(transactionChangeListener);
        wallet.removeCoinsReceivedEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        loaderManager.destroyLoader(ID_TRANSACTION_LOADER);

        config.unregisterOnSharedPreferenceChangeListener(this);

        resolver.unregisterContentObserver(addressBookObserver);

        super.onPause();
    }

    @Override
    public void onTransactionClick(final View view, final Transaction tx) {
        if (CommonUtils.isRepeatClick()) {
            TransactionConfidence confidence = tx.getConfidence();
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            if (confidenceType != TransactionConfidence.ConfidenceType.DEAD) {
                Intent intent = new Intent(getActivity(), BaseWalletActivity.class);
                intent.putExtra(BaseWalletActivity.CLASS, TransactionDetailsFragment.class);
                intent.putExtra("txId", tx.getHashAsString());
                intent.putExtra("assetId", assetId);
                startActivity(intent);
            }
        }
    }

    @Override
    public Loader<List<Transaction>> onCreateLoader(final int id, final Bundle args) {
        return new TransactionsLoader(activity, wallet, (Direction) args.getSerializable(ARG_DIRECTION), assetId);
    }

    @Override
    public void onLoadFinished(final Loader<List<Transaction>> loader, final List<Transaction> transactions) {
        final Direction direction = ((TransactionsLoader) loader).getDirection();

        adapter.replace(transactions);

        if (transactions.isEmpty()) {
            viewGroup.setDisplayedChild(1);
            String emptyText = getString(direction == Direction.SENT ? R.string.wallet_transactions_fragment_empty_text_sent : R.string.no_trad_empty_text_received);
            emptyView.setText(WholeStringBuilder.bold(emptyText));
        } else {
            viewGroup.setDisplayedChild(2);
        }
    }

    @Override
    public void onLoaderReset(final Loader<List<Transaction>> loader) {
    }

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener(
            DateUtils.SECOND_IN_MILLIS) {
        @Override
        public void onThrottledWalletChanged() {
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_BTC_PRECISION.equals(key) || Configuration.PREFS_KEY_REMIND_BACKUP.equals(key))
            updateView();
    }

    private void updateView() {
        adapter.setFormat(config.getFormat());
    }

    private Warning warning() {
        final int storageEncryptionStatus = devicePolicyManager.getStorageEncryptionStatus();
        if (config.remindBackup())
            return Warning.BACKUP;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && (storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE
                || storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY))
            return Warning.STORAGE_ENCRYPTION;
        else
            return null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void addressBookEdit(final AddressBook book) {
        if (null != adapter){
            adapter.clearCacheAndNotifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        EventBus.getDefault().unregister(this);
    }

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
