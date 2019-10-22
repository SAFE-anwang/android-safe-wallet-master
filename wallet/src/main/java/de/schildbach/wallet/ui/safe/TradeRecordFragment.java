package de.schildbach.wallet.ui.safe;


import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.ui.safe.bean.AddressBook;
import de.schildbach.wallet.ui.safe.bean.SafeReserve;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.CommonAdapter;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.ViewHolder;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.RecyclerViewDivider;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.ThrottlingWalletChangeListener;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.CoinDefinition;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * 交易记录
 *
 * @author zk
 */
public class TradeRecordFragment extends BaseFragment {

    private CommonAdapter<TransactionOutput> adapter;
    private RecyclerView recyclerView;
    private TextView empty_tv;
    private ImageView top_img;
    private String txId;
    private Configuration config;
    private Transaction tx;
    private WalletApplication application;
    private Wallet wallet;
    private List<TransactionOutput> transactionOutputs;
    private IssueData issue;
    private String assetId;
    private RelativeLayout data_view;
    private Coin value;
    private boolean isSafe = false;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_trade_record;
    }

    @Override
    public void initView() {
        super.initView();
        top_img = (ImageView) findViewById(R.id.top);
        empty_tv = (TextView) findViewById(R.id.empty_tv);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        data_view = (RelativeLayout) findViewById(R.id.data_view);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new RecyclerViewDivider(getActivity(), LinearLayoutManager.HORIZONTAL));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            //返回当前recyclerview的可见的item数目，也就是datas.length
            //dx是水平滚动的距离，dy是垂直滚动距离，向上滚动的时候为正，向下滚动的时候为负
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();//可见范围内的第一项的位置
                if (firstVisibleItemPosition == 0) {
                    top_img.setVisibility(View.GONE);
                } else {
                    top_img.setVisibility(View.VISIBLE);
                }
            }
        });
        top_img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
        top_img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.safe_lately_tx_details));
        Bundle args = getArguments();
        issue = (IssueData) args.getSerializable("issue");
        txId = args.getString("txId");
        assetId = args.getString("assetId");
        transactionOutputs = new ArrayList<>();
        this.application = (WalletApplication) getActivity().getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        tx = wallet.getTransaction(Sha256Hash.wrap(txId));
        transactionOutputs.addAll(tx.getOutputs());
        final Transaction.Purpose purpose = tx.getPurpose();
        value = tx.getValue(wallet, assetId);
        isSafe = tx.isSafeTx();
        final Coin fee = tx.getFee();
        final boolean sent = value.signum() < 0;
        final boolean showFee = sent && fee != null && !fee.isZero();
        final Coin showValue;
        if (assetId.equals(SafeConstant.SAFE_FLAG)) {
            if (purpose == Transaction.Purpose.RAISE_FEE) {
                showValue = fee != null ? fee.negate() : Coin.ZERO;
            } else {
                showValue = showFee ? value.add(fee) : value;
            }
            value = showValue;
        }
        List<TransactionOutput> tempList = new ArrayList<>();
        Iterator<TransactionOutput> iterator = transactionOutputs.iterator();
        boolean flag = false;
        while (iterator.hasNext()) {
            TransactionOutput output = iterator.next();
            if (output.isMine(wallet) && !sent) {
                tempList.add(output);
            }
            SafeReserve mReserve = output.getSafeReserve();
            if (mReserve.isIssue()) {
                flag = true;
            }
            if (isSafeBlack(output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS).toBase58())) {
                iterator.remove();
            }
        }
        if (tempList.size() > 0 && !flag) {
            transactionOutputs = tempList;
        }
        adapter = new CommonAdapter<TransactionOutput>(R.layout.trade_record_item) {
            @Override
            protected void convert(ViewHolder viewHolder, TransactionOutput item, int position) {
                TextView tvAddress = viewHolder.findViewById(R.id.tv_address);
                TextView assetName = viewHolder.findViewById(R.id.tv_asset_name);
                CurrencyTextView candyAmount = viewHolder.findViewById(R.id.ctv_candy_amount);
                LinearLayout lockLayout = viewHolder.findViewById(R.id.lock_layout);
                TextView lockMonth = viewHolder.findViewById(R.id.lock_month);
                TextView unlockHeight = viewHolder.findViewById(R.id.unlock_height);
                ImageView lock = viewHolder.findViewById(R.id.lock);

                String address = item.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS).toBase58();
                String addressLabel = address != null
                        ? AddressBookProvider.resolveLabel(getActivity(), address) : null;
                tvAddress.setText(addressLabel == null ? address : addressLabel);

                if (isSafe) { //safe转账
                    if (sent) { //发送
                        if (item.isMine(wallet)) { //是否内部地址
                            assetName.setText(getString(R.string.safe_give_tip));
                        } else { //支出
                            assetName.setText(getString(R.string.out_tip));
                        }
                    } else { //接收
                        assetName.setText(getString(R.string.receive_tip));
                    }
                } else { //资产
                    if (item.isMine(wallet)) { //是否内部地址
                        SafeReserve mReserve = item.getSafeReserve();
                        if (mReserve.isAssetChange()) { //资产找零
                            assetName.setText(getString(R.string.asset_give_tip));
                        } else if (mReserve.isPutCandy()) { //发放糖果
                            assetName.setText(getString(R.string.safe_candy_grant));
                        } else if (mReserve.isIssue()) { //发放资产
                            assetName.setText(getString(R.string.safe_issue_asset_title));
                        } else if (mReserve.isAddIssue()) { //追加资产
                            assetName.setText(getString(R.string.receive_tip));
                        } else if (mReserve.isTransfer()) { ///资产转账
                            if (sent) {
                                assetName.setText(getString(R.string.out_tip));
                            } else {
                                assetName.setText(getString(R.string.receive_tip));
                            }
                        } else {
                            assetName.setText(getString(R.string.safe_give_tip));
                        }
                    } else {
                        if (isCandyBlack(item.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS).toBase58())) {
                            assetName.setText(getString(R.string.safe_candy_grant));
                        } else {
                            assetName.setText(getString(R.string.out_tip));
                        }
                    }
                }

                if (item.isSafeTx()) {
                    candyAmount.setFormat(config.getFormat().noCode());
                    candyAmount.setAmount(item.getValue());
                } else {
                    if (issue == null) {
                        try {
                            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
                            issue = (IssueData) dao.queryForFirst("txId", txId);
                        } catch (SQLException e) {
                            getActivity().finish();
                            return;
                        }
                    }
                    candyAmount.setText(SafeUtils.getAssetAmount(item.getValue(), issue.decimals));
                }

                int txHeight = 0;
                TransactionConfidence confidence = tx.getConfidence();
                if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                    txHeight = confidence.getAppearedAtChainHeight();
                }
                if (item.getLockHeight() > 0) { //锁定高度
                    if (SafeConstant.getLastBlockHeight() >= item.getLockHeight()) {
                        lock.setVisibility(View.GONE);
                        lockMonth.setText(getString(R.string.safe_unlocked));
                    }
                    lock.setVisibility(View.VISIBLE);
                    lockLayout.setVisibility(View.VISIBLE);
                    if(txHeight == 0) {
                        lockMonth.setText(getString(R.string.safe_tx_building));
                        unlockHeight.setText(getString(R.string.safe_tx_building));
                        return;
                    }
                    int month;
                    if (txHeight >= Constants.NETWORK_PARAMETERS.getStartSposHeight()) {
                        long diffHeight = item.getLockHeight() - txHeight;
                        BigDecimal decimal = new BigDecimal(diffHeight).divide(new BigDecimal(SafeConstant.SPOS_BLOCKS_PER_MONTH), 0, RoundingMode.HALF_UP);
                        log.info("----sposHeight = {} ---- decimal = {}", diffHeight, decimal.intValue());
                        month = decimal.intValue();
                        lockMonth.setText(getString(R.string.safe_lock_month, decimal.toPlainString()));
                    } else {
                        long diffHeight = item.getLockHeight() - txHeight;
                        BigDecimal decimal = new BigDecimal(diffHeight).divide(new BigDecimal(SafeConstant.BLOCKS_PER_MONTH), 0, RoundingMode.HALF_UP);
                        month = decimal.intValue();
                        log.info("----powHeight = {} ---- decimal = {}", diffHeight, decimal.toPlainString());
                        lockMonth.setText(getString(R.string.safe_lock_month, decimal.toPlainString()));
                    }
                    int startSposHeight = Constants.NETWORK_PARAMETERS.getStartSposHeight();
                    if (txHeight < startSposHeight) {
                        if (item.getLockHeight() > startSposHeight) { //采用POW和SPOS
                            int sposLaveHeight = ((int)item.getLockHeight() - startSposHeight) * (CoinDefinition.TARGET_SPACING / CoinDefinition.SPOS_TARGET_SPACING);
                            int neededHeight = startSposHeight + sposLaveHeight;
                            unlockHeight.setText(getString(R.string.safe_unlock_height, "" + neededHeight));
                        } else { //采用POW
                            unlockHeight.setText(getString(R.string.safe_unlock_height, "" + item.getLockHeight()));
                        }
                    } else { //采用SPOS
                        unlockHeight.setText(getString(R.string.safe_unlock_height, "" + item.getLockHeight()));
                    }
                } else {
                    lock.setVisibility(View.GONE);
                    lockLayout.setVisibility(View.GONE);
                }
            }

            @Override
            protected void onItemClick(View view, TransactionOutput item, int position) {
            }
        };
        adapter.addAll(transactionOutputs);
        recyclerView.setAdapter(adapter);
        if (transactionOutputs.size() == 0) {
            empty_tv.setVisibility(View.VISIBLE);
            data_view.setVerticalGravity(View.GONE);
        } else {
            empty_tv.setVisibility(View.GONE);
            data_view.setVerticalGravity(View.VISIBLE);
        }
    }

    private boolean isSafeBlack(String address) {
        return address.trim().equals(SafeConstant.BLACK_HOLE_ADDRESS);
    }

    private boolean isCandyBlack(String address) {
        return address.trim().equals(SafeConstant.CANDY_BLACK_HOLE_ADDRESS);
    }

    @Override
    public void onResume() {
        super.onResume();
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addCoinsSentEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, transactionChangeListener);
        wallet.addTransactionConfidenceEventListener(Threading.SAME_THREAD, transactionChangeListener);
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
            adapter.notifyDataSetChanged();
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void addressBookEdit(final AddressBook book) {
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

}
