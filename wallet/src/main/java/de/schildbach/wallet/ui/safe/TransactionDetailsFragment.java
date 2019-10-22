package de.schildbach.wallet.ui.safe;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.GetCandyData;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.EditAddressBookEntryFragment;
import de.schildbach.wallet.ui.safe.bean.AddressBook;
import de.schildbach.wallet.ui.safe.bean.SafeReserve;
import de.schildbach.wallet.ui.safe.utils.AssetCoin;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.*;
import de.schildbach.wallet.R;

import org.bitcoin.safe.SafeProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Date;

/**
 * 交易详情
 *
 * @author zhangmiao
 */
public class TransactionDetailsFragment extends BaseFragment implements View.OnClickListener {

    private CurrencyTextView valueView;
    private TextView stateView;
    private TextView addressView;
    private TextView addView;
    private CurrencyTextView feeView;
    private TextView heightView;
    private TextView timeView;
    private TextView depthBlocks;
    private TextView txIdView;
    private TextView browseView;
    private CardView addressQrCardView;
    private ImageView addressQrView;
    private LinearLayout assetLayout;
    private TextView assetTitle;
    private TextView assetValue;
    private TextView totalAmount;
    private TextView firstAmount;
    private TextView actualAmount;
    private TextView candyAmount;
    private String txId;
    private String assetId;
    private IssueData issue;

    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private Transaction tx;
    private GetCandyData candy;
    private TextView lookTv;
    private TextView unitTv;
    private boolean getCandyFlag = false;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_tx_details;
    }

    @Override
    public void initView() {
        super.initView();
        valueView = (CurrencyTextView) findViewById(R.id.value);
        stateView = (TextView) findViewById(R.id.state);
        addressView = (TextView) findViewById(R.id.acceptor_address);
        addView = (TextView) findViewById(R.id.add);
        feeView = (CurrencyTextView) findViewById(R.id.fee);
        heightView = (TextView) findViewById(R.id.height);
        timeView = (TextView) findViewById(R.id.time);
        depthBlocks = (TextView) findViewById(R.id.depth_blocks);
        txIdView = (TextView) findViewById(R.id.tx_id);
        browseView = (TextView) findViewById(R.id.browse);
        addressQrCardView = (CardView) findViewById(R.id.address_qr_card);
        addressQrCardView.setCardBackgroundColor(Color.WHITE);
        addressQrCardView.setPreventCornerOverlap(false);
        addressQrView = (ImageView) findViewById(R.id.address_qr);
        assetLayout = (LinearLayout) findViewById(R.id.asset_layout);
        assetTitle = (TextView) findViewById(R.id.asset_title);
        assetValue = (TextView) findViewById(R.id.asset_value);
        totalAmount = (TextView) findViewById(R.id.total_amount);
        firstAmount = (TextView) findViewById(R.id.first_amount);
        actualAmount = (TextView) findViewById(R.id.actual_amount);
        candyAmount = (TextView) findViewById(R.id.candy_amount);
        lookTv = (TextView) findViewById(R.id.look);
        unitTv = (TextView) findViewById(R.id.unit);
        addView.setOnClickListener(this);
        browseView.setOnClickListener(this);
        addressQrCardView.setOnClickListener(this);
        lookTv.setOnClickListener(this);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);

        this.application = (WalletApplication) getActivity().getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();

        getActivity().setTitle(getString(R.string.safe_tx_details));
        Bundle args = getArguments();

        txId = args.getString("txId");
        assetId = args.getString("assetId");
        candy = (GetCandyData) args.getSerializable("candy");

        BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
        if (!assetId.equals(SafeConstant.SAFE_FLAG)) {
            try {
                issue = (IssueData) dao.queryForFirst("assetId", assetId);
            } catch (SQLException e) {
                getActivity().finish();
                return;
            }
        }

        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {
                tx = wallet.getTransaction(Sha256Hash.wrap(txId));
                BackgroundThread.postUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshUi();
                    }
                });
            }
        }));

    }

    public void refreshUi() {

        if (getActivity() != null && !getActivity().isFinishing() && tx != null && tx.getConfidence() != null) {

            final TransactionConfidence confidence = tx.getConfidence();
            final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            final Transaction.Purpose purpose = tx.getPurpose();
            final Coin fee = tx.getFee();
            final String[] memo = Formats.sanitizeMemo(tx.getMemo());
            final Date time = tx.getUpdateTime();
            final Coin value = tx.getValue(wallet, assetId);
            final boolean sent = value.signum() < 0;
            final boolean self = WalletUtils.isEntirelySelf(tx, wallet);
            final boolean showFee = sent && fee != null && !fee.isZero();

            Address address;
            if (sent)
                address = WalletUtils.getToAddressOfSent(tx, wallet, assetId);
            else
                address = WalletUtils.getWalletAddressOfReceived(tx, wallet, assetId);

            String addressLabel = address != null
                    ? AddressBookProvider.resolveLabel(getActivity(), address.toBase58()) : null;

            // fee
            if (sent) {
                feeView.setAlwaysSigned(true);
                feeView.setFormat(config.getFormat());
                if (null != fee) {
                    feeView.setAmount(fee.negate());
                }
            } else {
                if (tx.isSafeTx()) {
                    feeView.setText(getString(R.string.safe_no_data));
                } else {
                    if (fee != null && !fee.isZero()) {
                        if (tx.isAssetTransfer()) { //接收的资产转让，手续费为零
                            if (purpose == Transaction.Purpose.KEY_ROTATION || self) { //内部转账
                                feeView.setAlwaysSigned(true);
                                feeView.setFormat(config.getFormat());
                                feeView.setAmount(fee.negate());
                            } else {
                                feeView.setText(getString(R.string.safe_no_data));
                            }
                        } else {
                            feeView.setAlwaysSigned(true);
                            feeView.setFormat(config.getFormat());
                            feeView.setAmount(fee.negate());
                        }
                    } else {
                        feeView.setText(getString(R.string.safe_no_data));
                    }
                }
            }
            if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.DEAD) {
                feeView.setText(getString(R.string.safe_no_data));
            }

            Coin lockInternalValue = Coin.ZERO;
            // address
            if (purpose == Transaction.Purpose.KEY_ROTATION || self) {
                String textInternal = getString(R.string.symbol_internal) + " " + getString(R.string.wallet_transactions_fragment_internal);
                lookTv.setVisibility(View.GONE);
                addressView.setText(textInternal);
                addView.setVisibility(View.GONE);
                if (tx.getLocked(wallet)) {
                    lockInternalValue = tx.getLockValue();
                }
            } else if (purpose == Transaction.Purpose.RAISE_FEE) {
                addressView.setText(null);
                addView.setVisibility(View.GONE);
            } else if (addressLabel != null) {
                addressView.setTypeface(Typeface.DEFAULT_BOLD);
                addressView.setText(addressLabel);
                addView.setText(getString(R.string.button_edit));
                addView.setVisibility(View.VISIBLE);
            } else if (memo != null && memo.length >= 2) {
                addressView.setText(memo[1]);
                addView.setVisibility(View.GONE);
            } else if (address != null) {
                addressView.setTypeface(Typeface.DEFAULT);
                addressView.setText(WalletUtils.formatAddress(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                addView.setText(getString(R.string.button_add));
                addView.setVisibility(View.VISIBLE);
            } else {
                addressView.setText("?");
                addView.setVisibility(View.GONE);
            }

            // time
            timeView.setText(DateUtils.formatDateTime(getActivity(), time.getTime(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));

            txIdView.setText(tx.getHashAsString());

            Uri uri = Uri.withAppendedPath(config.getBlockExplorer(), "tx/" + tx.getHashAsString());
            BitmapDrawable addressQrBitmap = new BitmapDrawable(getResources(), Qr.bitmap(uri.toString()));
            addressQrBitmap.setFilterBitmap(false);
            addressQrView.setImageDrawable(addressQrBitmap);

            int chainHeight;
            if (confidenceType != TransactionConfidence.ConfidenceType.BUILDING) {
                heightView.setText(getString(R.string.safe_no_data));
                depthBlocks.setText(getString(R.string.safe_hint_depth_blocks, 0));
            } else {
                chainHeight = confidence.getAppearedAtChainHeight();
                heightView.setText("" + chainHeight);
                int depthInBlocks = confidence.getDepthInBlocks();
                depthBlocks.setText(getString(R.string.safe_hint_depth_blocks, depthInBlocks));
            }

            stateView.setText(getString(R.string.safe_tx_amount));

            //value
            valueView.setAlwaysSigned(true);

            final Coin showValue;
            if (assetId.equals(SafeConstant.SAFE_FLAG)) {
                if (purpose == Transaction.Purpose.RAISE_FEE) {
                    showValue = fee != null ? fee.negate() : Coin.ZERO;
                } else {
                    showValue = showFee ? value.add(fee) : value;
                }
                valueView.setFormat(config.getFormat().noCode());
                if (showValue.isZero() && !lockInternalValue.isZero()) {
                    valueView.setAmount(lockInternalValue);
                } else {
                    valueView.setAmount(showValue);
                }
            } else {
                showValue = value;
                valueView.setFormat(Constants.getAssetFormat((int) issue.decimals));
                if (purpose == Transaction.Purpose.KEY_ROTATION || self) { //内部转账
                    if (!lockInternalValue.isZero()) { //锁定内部交易
                        valueView.setAmount(new AssetCoin(lockInternalValue.getValue(), (int) issue.decimals));
                    } else { //其他资产内部转账，显示金额
                        valueView.setAmount(new AssetCoin(showValue.getValue(), (int) issue.decimals));
                    }
                } else {
                    valueView.setAmount(new AssetCoin(showValue.getValue(), (int) issue.decimals));
                }
            }
            if (issue != null) {
                unitTv.setText(" (" + issue.assetUnit + ")");
            } else {
                MonetaryFormat btcFormat = config.getMaxPrecisionFormat();
                unitTv.setText(" (" + btcFormat.code() + ")");
            }
            if (candy != null) {
                findViewById(R.id.look).setVisibility(View.GONE);
                findViewById(R.id.fee_title).setVisibility(View.GONE);
                feeView.setVisibility(View.GONE);
                stateView.setText(getString(R.string.asset_count_str));
                valueView.setText(SafeUtils.getAssetAmount(candy.candyAmount, candy.decimals));
                ((TextView) findViewById(R.id.address_tv)).setText(R.string.receive_address_str);
                ((TextView) findViewById(R.id.time_title)).setText(R.string.safe_tx_time);
                String label = address != null ? AddressBookProvider.resolveLabel(getActivity(), candy.address) : null;
                addressView.setText(label != null ? label : WalletUtils.formatHash(candy.address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                addressView.setTypeface(label != null ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                addView.setText(getString(label != null ? R.string.button_edit : R.string.button_add));
                addView.setVisibility(View.VISIBLE);
                getActivity().setTitle(getString(R.string.receive_detail_str));
                return;
            }

            boolean isIssue = false;
            for (TransactionOutput output : tx.getOutputs()) {
                SafeReserve reserve = output.getSafeReserve();
                if (reserve.isIssue()) {
                    SafeProtos.IssueData protoIssue = output.getSafeReserve().getIssueProtos();
                    assetLayout.setVisibility(View.VISIBLE);
                    totalAmount.setVisibility(View.VISIBLE);
                    firstAmount.setVisibility(View.VISIBLE);
                    actualAmount.setVisibility(View.VISIBLE);
                    long decimals = Utils.readUint8(protoIssue.getDecimals().toByteArray(), 0);
                    assetTitle.setText(getString(R.string.safe_issue_asset_title) + ": " + protoIssue.getAssetName().toStringUtf8());
                    totalAmount.setText(getString(R.string.safe_asset_amount) + ": " + SafeUtils.getAssetAmount(protoIssue.getTotalAmount(), decimals));
                    firstAmount.setText(getString(R.string.safe_first_issue_amount) + ": " + SafeUtils.getAssetAmount(protoIssue.getFirstIssueAmount(), decimals));
                    actualAmount.setText(getString(R.string.safe_actual_issue_amount) + ": " + SafeUtils.getAssetAmount(protoIssue.getFirstActualAmount(), decimals));
                    boolean payCandy = protoIssue.getPayCandy();
                    if (payCandy) {
                        candyAmount.setVisibility(View.VISIBLE);
                        candyAmount.setText(getString(R.string.safe_candy_amount) + ": " + SafeUtils.getAssetAmount(protoIssue.getCandyAmount(), decimals));
                    }
                    isIssue = true;
                }
                Address outAddress = output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS);
                if (outAddress != null && SafeConstant.BLACK_HOLE_ADDRESS.equals(outAddress.toBase58())) {
                    assetValue.setVisibility(View.VISIBLE);
                    assetValue.setText(getString(R.string.safe_hint_asset_destroy, config.getFormat().format(output.getValue())));
                }
            }

            if (isIssue) {
                lookTv.setVisibility(View.GONE);
                return;
            }

            for (TransactionOutput output : tx.getOutputs()) {
                SafeReserve reserve = output.getSafeReserve();
                if (reserve.isAddIssue()) {
                    assetLayout.setVisibility(View.VISIBLE);
                    assetTitle.setText(getString(R.string.safe_add_issue) + ": " + issue.assetName);
                    assetValue.setVisibility(View.GONE);
                    return;
                } else if (reserve.isPutCandy()) {
                    assetLayout.setVisibility(View.VISIBLE);
                    assetTitle.setText(getString(R.string.safe_candy_grant) + ": " + issue.assetName);
                    assetValue.setVisibility(View.GONE);
                    return;
                } else if (reserve.isGetCandy()) {
                    getCandyFlag = true;
                    lookTv.setVisibility(View.VISIBLE);
                    assetLayout.setVisibility(View.VISIBLE);
                    assetTitle.setText(getString(R.string.safe_candy_receive) + ": " + issue.assetName);
                    assetValue.setVisibility(View.GONE);
                    return;
                } else if (reserve.isTransfer()) {
                    assetLayout.setVisibility(View.VISIBLE);
                    assetTitle.setText(getString(R.string.safe_candy_transfer) + ": " + issue.assetName);
                    assetValue.setVisibility(View.GONE);
                    return;
                }
            }
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addTransactionConfidenceEventListener(Threading.SAME_THREAD, transactionChangeListener);
        refreshUi();
    }

    @Override
    public void onPause() {
        wallet.removeTransactionConfidenceEventListener(transactionChangeListener);
        wallet.removeChangeEventListener(transactionChangeListener);
        wallet.removeCoinsSentEventListener(transactionChangeListener);
        wallet.removeCoinsReceivedEventListener(transactionChangeListener);
        super.onPause();
    }

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener(DateUtils.SECOND_IN_MILLIS) {
        @Override
        public void onThrottledWalletChanged() {
            refreshUi();
        }
    };

    @Override
    public void onClick(View v) {
        if (CommonUtils.isRepeatClick()) {
            switch (v.getId()) {
                case R.id.add:
                    handleEditAddress();
                    break;
                case R.id.browse:
                    handerBrowseTx();
                    break;
                case R.id.address_qr_card:
                    handleShowQr();
                    break;
                case R.id.look:
                    Intent intent = new Intent(getActivity(), BaseWalletActivity.class);
                    if (getCandyFlag) {
                        intent.putExtra(BaseWalletActivity.CLASS, CandyRecordFragment.class);
                    } else {
                        intent.putExtra(BaseWalletActivity.CLASS, TradeRecordFragment.class);
                        intent.putExtra("issue", issue);
                        intent.putExtra("assetId", assetId);
                    }
                    intent.putExtra("txId", txId);
                    startActivity(intent);
                    break;
            }
        }
    }

    private void handleEditAddress() {
        boolean txSent = tx.getValue(wallet, assetId).signum() < 0;
        Address txAddress;
        if (candy != null) {
            txAddress = Address.fromBase58(Constants.NETWORK_PARAMETERS, candy.address);
        } else {
            txAddress = txSent ? WalletUtils.getToAddressOfSent(tx, wallet, assetId)
                    : WalletUtils.getWalletAddressOfReceived(tx, wallet, assetId);
        }
        EditAddressBookEntryFragment.edit(getFragmentManager(), txAddress);
    }

    private void handerBrowseTx() {
        startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.withAppendedPath(config.getBlockExplorer(), "tx/" + txId)));
    }

    private void handleShowQr() {
        Uri uri = Uri.withAppendedPath(config.getBlockExplorer(), "tx/" + txId);
        Bitmap qrCodeBitmap = Qr.bitmap(uri.toString());
        BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
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

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void addressBookEdit(final AddressBook book) {
        refreshUi();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        BackgroundThread.prepareThread();
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        BackgroundThread.destroyThread();
        super.onDestroy();
    }

}
