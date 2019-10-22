package de.schildbach.wallet.ui.safe;

import android.app.Activity;
import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.ui.CurrencyTextView;
import de.schildbach.wallet.ui.safe.utils.AssetCoin;
import de.schildbach.wallet.util.Formats;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

import org.bitcoinj.core.*;
import org.bitcoinj.core.Transaction.Purpose;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.*;

/**
 * 交易记录适配器
 *
 * @author zhangmiao
 */
public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public enum Warning {
        BACKUP, STORAGE_ENCRYPTION
    }

    private final String assetId;
    private final long decimals;
    private final Activity context;
    private final LayoutInflater inflater;
    private final Wallet wallet;

    @Nullable
    private final OnClickListener onClickListener;

    private final List<Transaction> transactions = new ArrayList<Transaction>();

    private MonetaryFormat format;

    private final int colorError;
    private final String textCoinBase;
    private final String textInternal;

    private Map<Sha256Hash, TransactionCacheEntry> transactionCache = new HashMap<>();

    protected static final Logger log = LoggerFactory.getLogger(TransactionAdapter.class);


    private static class TransactionCacheEntry {
        private final Coin value;
        private final boolean sent;
        private final boolean self;
        private final boolean showFee;
        private final boolean lockTx;
        private final boolean sealed;
        @Nullable
        private final Address address;
        @Nullable
        private final String addressLabel;


        private TransactionCacheEntry(
                final Coin value,
                final boolean sent,
                final boolean self,
                final boolean showFee,
                final @Nullable Address address,
                final @Nullable String addressLabel,
                final boolean lockTx,
                final boolean sealed) {
            this.value = value;
            this.sent = sent;
            this.self = self;
            this.showFee = showFee;
            this.address = address;
            this.addressLabel = addressLabel;
            this.lockTx = lockTx;
            this.sealed = sealed;
        }
    }

    public TransactionAdapter(
            String assetId,
            long decimals,
            final Activity context,
            final Wallet wallet,
            final @Nullable OnClickListener onClickListener) {
        this.assetId = assetId;
        this.decimals = decimals;
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.wallet = wallet;
        this.onClickListener = onClickListener;
        colorError = ContextCompat.getColor(context, R.color.fg_error);
        textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
        textInternal = context.getString(R.string.symbol_internal) + " " + context.getString(R.string.wallet_transactions_fragment_internal);
        setHasStableIds(true);
    }

    public void setFormat(final MonetaryFormat format) {
        this.format = format;
        notifyDataSetChanged();
    }

    public void clear() {
        transactions.clear();
        notifyDataSetChanged();
    }

    public void replace(final Transaction tx) {
        transactions.clear();
        transactions.add(tx);
        notifyDataSetChanged();
    }

    public void replace(final Collection<Transaction> transactions) {
        this.transactions.clear();
        this.transactions.addAll(transactions);
        notifyDataSetChanged();
    }

    public void clearCacheAndNotifyDataSetChanged() {
        transactionCache.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = transactions.size();
        return count + 1;
    }

    @Override
    public long getItemId(int position) {
        if (position == RecyclerView.NO_POSITION)
            return RecyclerView.NO_ID;
        if (position == 0) {
            return RecyclerView.NO_ID;
        } else {
            return WalletUtils.longHash(transactions.get(position - 1).getHash());
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (viewType == 0) {
            return new FragmentViewHolder(inflater.inflate(R.layout.listrow_fgt_asset_top, parent, false));
        } else {
            return new TransactionViewHolder(inflater.inflate(R.layout.listrow_asset_tx, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof FragmentViewHolder) {
            final FragmentViewHolder fragmentHolder = (FragmentViewHolder) holder;
            fragmentHolder.fragment.refreshUI(assetId);
        } else if (holder instanceof TransactionViewHolder) {
            final TransactionViewHolder transactionHolder = (TransactionViewHolder) holder;
            final Transaction tx = transactions.get(position - 1);
            transactionHolder.bind(tx);
            transactionHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    onClickListener.onTransactionClick(v, tx);
                }
            });
        }
    }

    public interface OnClickListener {

        void onTransactionClick(View view, Transaction tx);

    }

    private class FragmentViewHolder extends RecyclerView.ViewHolder {

        AssetTopFragment fragment;

        public FragmentViewHolder(View itemView) {
            super(itemView);
            fragment = (AssetTopFragment) context.getFragmentManager().findFragmentById(R.id.asset_top_fragment);
        }
    }

    private class TransactionViewHolder extends RecyclerView.ViewHolder {

        ImageView sent;
        TextView timeView;
        TextView addressView;
        CurrencyTextView valueView;
        ImageView lock;
        TextView tvState;

        private TransactionViewHolder(final View itemView) {
            super(itemView);
            sent = (ImageView) itemView.findViewById(R.id.sent);
            timeView = (TextView) itemView.findViewById(R.id.transaction_row_time);
            addressView = (TextView) itemView.findViewById(R.id.transaction_row_address);
            valueView = (CurrencyTextView) itemView.findViewById(R.id.transaction_row_value);
            lock = (ImageView) itemView.findViewById(R.id.lock);
            tvState = (TextView) itemView.findViewById(R.id.tv_state);
        }

        private void bind(final Transaction tx) {
            final TransactionConfidence confidence = tx.getConfidence();
            final ConfidenceType confidenceType = confidence.getConfidenceType();
            final boolean isCoinBase = tx.isCoinBase();
            final Purpose purpose = tx.getPurpose();
            final Coin fee = tx.getFee();
            final String[] memo = Formats.sanitizeMemo(tx.getMemo());

            TransactionCacheEntry txCache = transactionCache.get(tx.getHash());
            if (txCache == null) {
                final Coin value = tx.getValue(wallet, assetId);
                final boolean sent = value.signum() < 0;
                final boolean self = WalletUtils.isEntirelySelf(tx, wallet);
                final boolean showFee = sent && fee != null && !fee.isZero();
                final Address address;
                if (sent)
                    address = WalletUtils.getToAddressOfSent(tx, wallet, assetId);
                else
                    address = WalletUtils.getWalletAddressOfReceived(tx, wallet, assetId);
                String addressLabel = address != null
                        ? AddressBookProvider.resolveLabel(context, address.toBase58()) : null;

                boolean lockTx = tx.getLocked(wallet);
                boolean sealed = tx.isDashUnspent(sent);
                txCache = new TransactionCacheEntry(value, sent, self, showFee, address, addressLabel, lockTx, sealed);
                transactionCache.put(tx.getHash(), txCache);
            }

            if (confidenceType == ConfidenceType.BUILDING) {
                if (confidence.getDepthInBlocks() < 6) {
                    tvState.setText(String.format(context.getString(R.string.safe_hint_depth_blocks), confidence.getDepthInBlocks()));
                    tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_gray));
                    tvState.setTextColor(ContextCompat.getColor(context, R.color.white));
                } else {
                    if (purpose == Purpose.KEY_ROTATION || txCache.self) { //内部转账
                        tvState.setText(context.getString(R.string.sent));
                        tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_red));
                        tvState.setTextColor(ContextCompat.getColor(context, R.color.vivid_red));
                    } else {
                        if (txCache.sent) {
                            tvState.setText(context.getString(R.string.sent));
                            tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_red));
                            tvState.setTextColor(ContextCompat.getColor(context, R.color.vivid_red));
                        } else {
                            if (txCache.sealed) {
                                tvState.setText(context.getString(R.string.sealed));
                                tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_red));
                                tvState.setTextColor(ContextCompat.getColor(context, R.color.vivid_red));
                            } else {
                                tvState.setText(context.getString(R.string.received));
                                tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_green));
                                tvState.setTextColor(ContextCompat.getColor(context, R.color.fg_password_strength_strong));
                            }
                        }
                    }
                }
            } else if (confidenceType == ConfidenceType.DEAD) {
                tvState.setText(R.string.safe_hint_invalid);
                tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_red));
                tvState.setTextColor(ContextCompat.getColor(context, R.color.vivid_red));
            } else {
                tvState.setText(String.format(context.getString(R.string.safe_hint_depth_blocks), 0));
                tvState.setBackground(ContextCompat.getDrawable(context, R.drawable.text_shape_gray));
                tvState.setTextColor(ContextCompat.getColor(context, R.color.white));
            }

            if (purpose == Purpose.KEY_ROTATION || txCache.self) { //内部转账
                sent.setImageResource(R.drawable.safe_left);
            } else {
                if (txCache.sent) {
                    sent.setImageResource(R.drawable.safe_left);
                } else {
                    sent.setImageResource(R.drawable.safe_right);
                }
            }

            final int textColor, valueColor;

            if (confidenceType == ConfidenceType.DEAD) {
                textColor = colorError;
                valueColor = colorError;
                sent.setImageResource(R.drawable.ic_close_red_24dp);
            } else {
                textColor = ContextCompat.getColor(context, R.color.fg_less_significant);
                valueColor = ContextCompat.getColor(context, R.color.fg_significant);
            }

            // time
            final Date time = tx.getUpdateTime();
            timeView.setText(DateUtils.getRelativeTimeSpanString(context, time.getTime()));

            Coin lockInternalValue = Coin.ZERO;
            // address
            if (isCoinBase) {
                addressView.setTextColor(textColor);
                addressView.setText(textCoinBase);
            } else if (purpose == Purpose.KEY_ROTATION || txCache.self) {
                addressView.setTextColor(textColor);
                addressView.setText(textInternal);
                if (txCache.lockTx) {
                    lockInternalValue = tx.getLockValue();
                }
            } else if (purpose == Purpose.RAISE_FEE) {
                addressView.setText(null);
            } else if (txCache.addressLabel != null) {
                addressView.setTextColor(textColor);
                addressView.setText(txCache.addressLabel);
            } else if (memo != null && memo.length >= 2) {
                addressView.setTextColor(textColor);
                addressView.setText(memo[1]);
            } else if (txCache.address != null) {
                addressView.setTextColor(textColor);
                addressView.setText(WalletUtils.formatAddress(txCache.address, Constants.ADDRESS_FORMAT_GROUP_SIZE,
                        Constants.ADDRESS_FORMAT_LINE_SIZE));
            } else {
                addressView.setTextColor(textColor);
                addressView.setText("?");
            }

            // value
            valueView.setAlwaysSigned(true);
            final Coin value;
            valueView.setTextColor(valueColor);
            if (assetId.equals(SafeConstant.SAFE_FLAG)) { //SAFE资产
                if (purpose == Purpose.RAISE_FEE) {
                    value = fee != null ? fee.negate() : Coin.ZERO;
                } else {
                    value = txCache.showFee ? txCache.value.add(fee) : txCache.value;
                }
                valueView.setFormat(format);
                if (purpose == Purpose.KEY_ROTATION || txCache.self) { //内部转账
                    if (!lockInternalValue.isZero()) { //锁定内部交易
                        valueView.setAmount(lockInternalValue);
                    } else { //SAFE内部转账，只显示费用
                        valueView.setAmount(fee != null ? fee.negate() : Coin.ZERO);
                    }
                } else { //正常转账
                    valueView.setAmount(value);
                }
            } else { //其他资产
                value = txCache.value;
                valueView.setFormat(Constants.getAssetFormat((int) decimals));
                if (purpose == Purpose.KEY_ROTATION || txCache.self) { //内部转账
                    if (!lockInternalValue.isZero()) { //锁定内部交易
                        valueView.setAmount(new AssetCoin(lockInternalValue.getValue(), (int) decimals));
                    } else { //其他资产内部转账，显示金额
                        valueView.setAmount(new AssetCoin(value.getValue(), (int) decimals));
                    }
                } else {
                    valueView.setAmount(new AssetCoin(value.getValue(), (int) decimals));
                }
            }

            if (confidenceType == ConfidenceType.DEAD) {
                valueView.setVisibility(View.VISIBLE);
                if (assetId.equals(SafeConstant.SAFE_FLAG)) {
                    valueView.setAmount(Coin.ZERO);
                } else {
                    valueView.setAmount(new AssetCoin(0, (int) decimals));
                }
            }
            if (txCache.lockTx) {
                lock.setVisibility(View.VISIBLE);
            } else {
                lock.setVisibility(View.GONE);
            }

        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return 0;
        } else {
            return 1;
        }
    }
}
