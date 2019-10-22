/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.send;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import javax.annotation.Nullable;

import android.Manifest;
import android.content.*;
import android.content.Context;
import android.os.*;
import android.os.Process;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.widget.*;

import com.google.protobuf.ByteString;

import de.schildbach.wallet.BitcoinIntegration;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.ui.safe.MainActivity;
import de.schildbach.wallet.ui.safe.SendBaseFragment;
import de.schildbach.wallet.ui.safe.bean.SafeReserve;
import de.schildbach.wallet.ui.safe.listview.CommonAdapter;
import de.schildbach.wallet.ui.safe.listview.ViewHolder;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.EditTextJudgeNumber;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.*;

import org.bitcoin.protocols.payments.Protos.Payment;
import org.bitcoin.safe.SafeProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.Wallet.CouldNotAdjustDownwards;
import org.bitcoinj.wallet.Wallet.DustySendRequested;
import org.bitcoinj.wallet.WalletTransaction;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;


import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.data.DynamicFeeLoader;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.data.PaymentIntent.Standard;
import de.schildbach.wallet.offline.DirectPaymentTask;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainStateReceiver;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.AddressAndLabel;
import de.schildbach.wallet.ui.CurrencyAmountView;
import de.schildbach.wallet.ui.CurrencyCalculatorLink;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StreamInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.ScanActivity;
import de.schildbach.wallet.ui.TransactionsAdapter;
import de.schildbach.wallet.R;
import fr.cryptohash.SHA256;

import android.app.Activity;
import android.app.LoaderManager;
import android.bluetooth.BluetoothAdapter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.app.LoaderManager.LoaderCallbacks;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends Fragment {

    private AbstractBindServiceActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private ContentResolver contentResolver;
    private LoaderManager loaderManager;
    private FragmentManager fragmentManager;
    @Nullable
    private BluetoothAdapter bluetoothAdapter;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private View payeeGroup;
    private TextView payeeNameView;
    private TextView payeeVerifiedByView;
    private AutoCompleteTextView receivingAddressView;
    private ReceivingAddressViewAdapter receivingAddressViewAdapter;
    private ReceivingAddressLoaderCallbacks receivingAddressLoaderCallbacks;
    private View receivingStaticView;
    private TextView receivingStaticAddressView;
    private TextView receivingStaticLabelView;
    private ImageView receivingStaticDeleteView;
    private View amountGroup;
    private CurrencyCalculatorLink amountCalculatorLink;
    private CheckBox directPaymentEnableView;
    private CheckBox instantXenable;
    private final int defaultLockValue = 6;

    //锁定功能和资产转账
    private CheckBox lockTx; //锁定开关
    private LinearLayout monthLayout; //锁定布局
    private AddSubEditText monthEdit; //锁定月份
    private IssueData issue = null; //转账资产信息
    private IssueData scanIssue = null; //扫描资产信息
    private String assetId = SafeConstant.SAFE_FLAG; //资产ID
    private CurrencyAmountView btcAmountView;
    private EditTextJudgeNumber editTextJudgeNumber;
    private boolean isFinishActivity = true;
    public IssueData getIssue() {
        return issue;
    }
    public String getAssetId() {
        return assetId;
    }
    //锁定功能和资产转账

    private TextView pasteAddress;
    private TextView scanAddress;

    private TextView hintView;
    private TextView directPaymentMessageView;
    private FrameLayout sentTransactionView;
    private TransactionsAdapter sentTransactionAdapter;
    private RecyclerView.ViewHolder sentTransactionViewHolder;
    private View privateKeyPasswordViewGroup;
    private EditText privateKeyPasswordView, send_coins_amount_btc_edittext;
    private View privateKeyBadPasswordView;
    private Button viewGo;

    @Nullable
    private State state = null;
    private PaymentIntent paymentIntent = null;
    private FeeCategory feeCategory = FeeCategory.NORMAL;
    private AddressAndLabel validatedAddress = null;

    @Nullable
    private Map<FeeCategory, Coin> fees = null;
    @Nullable
    private BlockchainState blockchainState = null;
    private BlockchainStateReceiver blockchainStateReceiver;

    private Transaction sentTransaction = null;
    private Boolean directPaymentAck = null;

    private Transaction dryrunTransaction;
    private Exception dryrunException;

    private static final int ID_DYNAMIC_FEES_LOADER = 0;
    private static final int ID_RECEIVING_ADDRESS_BOOK_LOADER = 1;

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST = 1;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT = 2;

    private static final long BLOCKCHAIN_UPTODATE_THRESHOLD_MS = DateUtils.HOUR_IN_MILLIS;

    private static final Logger log = LoggerFactory.getLogger(SendCoinsFragment.class);

    private List<AddressAndLabel> labelList = new ArrayList<>();

    private enum State {
        REQUEST_PAYMENT_REQUEST,
        INPUT,
        DECRYPTING,
        SIGNING,
        SENDING,
        SENT,
        FAILED
    }

    private final class ReceivingAddressListener
            implements OnFocusChangeListener, TextWatcher, AdapterView.OnItemClickListener {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                validateReceivingAddress();
                updateView();
            }
        }

        @Override
        public void afterTextChanged(final Editable s) {
            if (s.length() > 0) {
                validateReceivingAddress();
            } else {
                handler.post(dryrunRunnable);
                updateView();
            }
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }

        @Override
        public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
            validatedAddress = receivingAddressViewAdapter.getItem(position);
            receivingAddressView.setText(null);
        }
    }

    private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

    private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener() {
        @Override
        public void changed() {
            if (!getBlockSync()) {
                updateView();
                handler.post(dryrunRunnable);
            }
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
        }
    };

    private final TextWatcher privateKeyPasswordListener = new TextWatcher() {
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            privateKeyBadPasswordView.setVisibility(View.INVISIBLE);
            updateView();
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {
        }
    };

    private final ContentObserver contentObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            updateView();
        }
    };

    private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(final TransactionConfidence confidence,
                                        final TransactionConfidence.Listener.ChangeReason reason) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isResumed())
                        return;

                    TransactionConfidence confidence = sentTransaction.getConfidence();
                    ConfidenceType confidenceType = confidence.getConfidenceType();
                    int numBroadcastPeers = confidence.numBroadcastPeers();

                    if (state == State.SENDING) {
                        if (confidenceType == ConfidenceType.DEAD) {
                            setState(State.FAILED);
                        } else if (numBroadcastPeers > 0 || confidenceType == ConfidenceType.BUILDING) {
                            setState(State.SENT);
                            if(!isFinishActivity){
                                countDownTimer.cancel();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (activity != null && !activity.isFinishing()) {
                                            activity.finish();
                                        }
                                    }
                                }, 500);
                            }
                        }
                    }

                    if (reason == ChangeReason.SEEN_PEERS && confidenceType == ConfidenceType.PENDING) {
                        // play sound effect
                        final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers,
                                "raw", activity.getPackageName());
                        android.content.Context context = getActivity().getApplicationContext();
                        if (soundResId > 0)
                            RingtoneManager.getRingtone(context, Uri.parse("android.resource://" + context.getPackageName() + "/" + soundResId)).play();
                    }

                    updateView();
                }
            });
        }
    };

    private final LoaderCallbacks<Map<FeeCategory, Coin>> dynamicFeesLoaderCallbacks = new LoaderManager.LoaderCallbacks<Map<FeeCategory, Coin>>() {
        @Override
        public Loader<Map<FeeCategory, Coin>> onCreateLoader(final int id, final Bundle args) {
            return new DynamicFeeLoader(activity);
        }

        @Override
        public void onLoadFinished(final Loader<Map<FeeCategory, Coin>> loader, final Map<FeeCategory, Coin> data) {
            fees = data;
            updateView();
            handler.post(dryrunRunnable);
        }

        @Override
        public void onLoaderReset(final Loader<Map<FeeCategory, Coin>> loader) {
        }
    };

    private class ReceivingAddressLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        private final Context context;

        public ReceivingAddressLoaderCallbacks(final Context context) {
            this.context = checkNotNull(context);
        }

        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            return new CursorLoader(context, AddressBookProvider.contentUri(context.getPackageName()), null,
                    AddressBookProvider.SELECTION_QUERY, new String[]{""}, null);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, Cursor cursor) {
            try {
                labelList.clear();
                if (cursor.moveToFirst()) {
                    do {
                        String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
                        String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
                        AddressAndLabel item = new AddressAndLabel(Constants.NETWORK_PARAMETERS, address, label);
                        labelList.add(item);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
                receivingAddressViewAdapter = new ReceivingAddressViewAdapter(activity, R.layout.address_book_row, labelList);
                receivingAddressView.setAdapter(receivingAddressViewAdapter);
                receivingAddressView.setThreshold(1);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {

        }
    }

    private final class ReceivingAddressViewAdapter extends CommonAdapter<AddressAndLabel> implements Filterable {

        private List<AddressAndLabel> mOriginalList = new ArrayList<>();

        public ReceivingAddressViewAdapter(Context context, int layoutResId, List<AddressAndLabel> data) {
            super(context, layoutResId, data);
            mOriginalList.addAll(data);
        }

        @Override
        protected void convert(ViewHolder viewHolder, AddressAndLabel item, int position) {
            final TextView labelView = (TextView) viewHolder.findViewById(R.id.address_book_row_label);
            labelView.setText(item.label);
            final TextView addressView = (TextView) viewHolder.findViewById(R.id.address_book_row_address);
            addressView.setText(WalletUtils.formatHash(item.address.toBase58(), Constants.ADDRESS_FORMAT_GROUP_SIZE,
                    Constants.ADDRESS_FORMAT_LINE_SIZE));
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    List<AddressAndLabel> filterList = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        filterList.addAll(mOriginalList);
                        filterResults.values = filterList;
                        filterResults.count = filterList.size();
                    } else {
                        for (AddressAndLabel item : mOriginalList) {
                            String address = item.address.toBase58();
                            String label = item.label;
                            if (address.contains(constraint) || label.contains(constraint)) {
                                filterList.add(item);
                            }
                        }
                        filterResults.values = filterList;
                        filterResults.count = filterList.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    setData((List<AddressAndLabel>) results.values);
                    if (results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            };
        }
    }

    private final DialogInterface.OnClickListener activityDismissListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            activity.finish();
        }
    };

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        this.activity = (AbstractBindServiceActivity) activity;
        this.application = (WalletApplication) this.activity.getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.contentResolver = activity.getContentResolver();
        this.loaderManager = getLoaderManager();
        this.fragmentManager = getFragmentManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        blockchainStateReceiver = new BlockchainStateReceiver(getActivity(), new BlockchainStateReceiver.BlockchainStateListener() {
            @Override
            public void onCallBack(BlockchainState state) {
                blockchainState = state;
                updateView();
            }
        });

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            Intent intent = activity.getIntent();
            assetId = intent.getStringExtra("assetId");
            if (isSafe()) {
                String assetTitle = SafeConstant.SAFE_FLAG.toUpperCase() + getString(R.string.send_coins_activity_title);
                getActivity().setTitle(assetTitle);
            } else {
                BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
                try {
                    issue = (IssueData) dao.queryForFirst("assetId", assetId);
                } catch (SQLException e) {
                    getActivity().finish();
                }
                String assetTitle = issue.assetName + getString(R.string.send_coins_activity_title);
                getActivity().setTitle(assetTitle);
            }

            final String action = intent.getAction();
            final Uri intentUri = intent.getData();
            final String scheme = intentUri != null ? intentUri.getScheme() : null;
            final String mimeType = intent.getType();

            if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
                    && intentUri != null && "safe".equals(scheme)) {
                initStateFromBitcoinUri(intentUri);
            } else if ((NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
                    && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType)) {
                final NdefMessage ndefMessage = (NdefMessage) intent
                        .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
                final byte[] ndefMessagePayload = Nfc.extractMimePayload(PaymentProtocol.MIMETYPE_PAYMENTREQUEST,
                        ndefMessage);
                initStateFromPaymentRequest(mimeType, ndefMessagePayload);
            } else if ((Intent.ACTION_VIEW.equals(action))
                    && PaymentProtocol.MIMETYPE_PAYMENTREQUEST.equals(mimeType)) {
                final byte[] paymentRequest = BitcoinIntegration.paymentRequestFromIntent(intent);
                if (intentUri != null)
                    initStateFromIntentUri(mimeType, intentUri);
                else if (paymentRequest != null)
                    initStateFromPaymentRequest(mimeType, paymentRequest);
                else
                    throw new IllegalArgumentException();
            } else if (intent.hasExtra(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT)) {
                initStateFromIntentExtras(intent.getExtras());
            } else {
                updateStateFrom(PaymentIntent.blank());
            }
        }
        EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.send_coins_fragment, container);

        payeeGroup = view.findViewById(R.id.send_coins_payee_group);

        payeeNameView = (TextView) view.findViewById(R.id.send_coins_payee_name);
        payeeVerifiedByView = (TextView) view.findViewById(R.id.send_coins_payee_verified_by);

        receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
        receivingAddressLoaderCallbacks = new ReceivingAddressLoaderCallbacks(activity);
        receivingAddressView.setOnFocusChangeListener(receivingAddressListener);
        receivingAddressView.addTextChangedListener(receivingAddressListener);
        receivingAddressView.setOnItemClickListener(receivingAddressListener);

        receivingStaticView = view.findViewById(R.id.send_coins_receiving_static);
        receivingStaticAddressView = (TextView) view.findViewById(R.id.send_coins_receiving_static_address);
        receivingStaticLabelView = (TextView) view.findViewById(R.id.send_coins_receiving_static_label);
        receivingStaticDeleteView = (ImageView) view.findViewById(R.id.send_coins_receiving_satatic_delete);

        amountGroup = view.findViewById(R.id.send_coins_amount_group);

        btcAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_btc);
        if (isSafe()) {
            btcAmountView.setAssetIdAndDecimals(assetId, Coin.SMALLEST_UNIT_EXPONENT);
            btcAmountView.setCurrencySymbol(config.getFormat().code());
            btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
            btcAmountView.setHintFormat(config.getFormat());
            int frontCount, decimals;
            if (Coin.MILLICOIN.equals(config.getBtcBase())) {
                frontCount = Coin.SMALLEST_UNIT_EXPONENT + 3;
                decimals = 5;
            } else if (Coin.MICROCOIN.equals(config.getBtcBase())) {
                frontCount = Coin.SMALLEST_UNIT_EXPONENT + 6;
                decimals = 2;
            } else {
                frontCount = Coin.SMALLEST_UNIT_EXPONENT;
                decimals = Coin.SMALLEST_UNIT_EXPONENT;
            }
            editTextJudgeNumber = new EditTextJudgeNumber(btcAmountView.getTextView(), frontCount, decimals, true);
            btcAmountView.getTextView().addTextChangedListener(editTextJudgeNumber);
        } else {
            btcAmountView.setAssetIdAndDecimals(assetId, issue.decimals);
            btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
            btcAmountView.setHintFormat(config.getMaxPrecisionFormat());
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                IssueData issue = (IssueData) dao.queryForFirst("assetId", assetId);
                int frontCount = String.valueOf(issue.totalAmount).length();
                int decimals = (int) issue.decimals;
                editTextJudgeNumber = new EditTextJudgeNumber(btcAmountView.getTextView(), frontCount - decimals, decimals, true);
                btcAmountView.getTextView().addTextChangedListener(editTextJudgeNumber);
            } catch (SQLException e) {
            }
        }

        final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_local);
        localAmountView.setInputFormat(Constants.LOCAL_FORMAT);
        localAmountView.setHintFormat(Constants.LOCAL_FORMAT);
        amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);
        amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());

        directPaymentEnableView = (CheckBox) view.findViewById(R.id.send_coins_direct_payment_enable);
        directPaymentEnableView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                if (paymentIntent.isBluetoothPaymentUrl() && isChecked && !bluetoothAdapter.isEnabled()) {
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT);
                }
            }
        });

        instantXenable = (CheckBox) view.findViewById(R.id.send_coins_instantx_enable);
        if (isSafe()) {
            instantXenable.setVisibility(config.getInstantXEnabled() == true && wallet.getContext().sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED) ? View.VISIBLE : View.INVISIBLE);
        } else {
            instantXenable.setVisibility(View.GONE);
        }
        instantXenable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                updateView();
                handler.post(dryrunRunnable);
                if (isChecked) {
                    lockTx.setChecked(false);
                }
            }
        });

        lockTx = (CheckBox) view.findViewById(R.id.lock_tx);
        lockTx.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                monthLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                updateView();
                handler.post(dryrunRunnable);
                if (isChecked) {
                    instantXenable.setChecked(false);
                }
            }
        });
        monthLayout = (LinearLayout) view.findViewById(R.id.month_layout);
        monthLayout.setVisibility(lockTx.isChecked() ? View.VISIBLE : View.GONE);
        monthEdit = (AddSubEditText) view.findViewById(R.id.month);
        monthEdit.setNumChangeListener(new AddSubEditText.OnNumChangeListener() {
            @Override
            public void onNumChange(Integer num) {
                if (num > AddSubEditText.MAX_VALUE) {
                    monthEdit.setValue(AddSubEditText.MAX_VALUE);
                    monthEdit.setSelection(String.valueOf(AddSubEditText.MAX_VALUE).length());
                    return;
                }
                if (num == 0) {
                    monthEdit.setValue(defaultLockValue);
                    monthEdit.setSelection(1);
                    return;
                }
                updateView();
                handler.post(dryrunRunnable);
            }
        });
        pasteAddress = (TextView) view.findViewById(R.id.paste_address);
        pasteAddress.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    String text = clip.getItemAt(0).coerceToText(getActivity()).toString();
                    if (!TextUtils.isEmpty(text)) {
                        paymentIntent = PaymentIntent.blank();
                        receivingAddressView.setText(text);
                        validateReceivingAddress();
                        updateView();
                    }
                }
            }
        });
        scanAddress = (TextView) view.findViewById(R.id.scan_address);
        scanAddress.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CommonUtils.isRepeatClick()) {
                    if (state != State.SIGNING && state != State.SENDING)
                        handleScan();
                }
            }
        });

        hintView = (TextView) view.findViewById(R.id.send_coins_hint);

        directPaymentMessageView = (TextView) view.findViewById(R.id.send_coins_direct_payment_message);

        sentTransactionView = (FrameLayout) view.findViewById(R.id.send_coins_sent_transaction);
        sentTransactionAdapter = new TransactionsAdapter(activity, wallet, false, application.maxConnectedPeers(),
                null);
        sentTransactionViewHolder = sentTransactionAdapter.createTransactionViewHolder(sentTransactionView);
        sentTransactionView.addView(sentTransactionViewHolder.itemView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        privateKeyPasswordViewGroup = view.findViewById(R.id.send_coins_private_key_password_group);
        privateKeyPasswordView = (EditText) view.findViewById(R.id.send_coins_private_key_password);
        send_coins_amount_btc_edittext = (EditText) view.findViewById(R.id.send_coins_amount_btc_edittext);
        privateKeyBadPasswordView = view.findViewById(R.id.send_coins_private_key_bad_password);

        viewGo = (Button) view.findViewById(R.id.send_coins_go);
        viewGo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (CommonUtils.isRepeatClick()) {
                    validateReceivingAddress();

                    if (everythingPlausible())
                        handleGo();
                    else
                        requestFocusFirst();

                    updateView();
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
    }

    @Override
    public void onResume() {
        super.onResume();

        contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true,
                contentObserver);

        amountCalculatorLink.setListener(amountsListener);
        privateKeyPasswordView.addTextChangedListener(privateKeyPasswordListener);

        loaderManager.initLoader(ID_DYNAMIC_FEES_LOADER, null, dynamicFeesLoaderCallbacks);
        loaderManager.initLoader(ID_RECEIVING_ADDRESS_BOOK_LOADER, null, receivingAddressLoaderCallbacks);
        blockchainStateReceiver.registerReceiver();

        updateView();
        handler.post(dryrunRunnable);
    }

    public void serviceBinded() {
        blockchainState = activity.getBlockchainService().getBlockchainState();
        updateView();
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_BOOK_LOADER);
        loaderManager.destroyLoader(ID_DYNAMIC_FEES_LOADER);
        blockchainStateReceiver.unregisterReceiver();

        privateKeyPasswordView.removeTextChangedListener(privateKeyPasswordListener);
        amountCalculatorLink.setListener(null);

        contentResolver.unregisterContentObserver(contentObserver);

        super.onPause();
    }

    @Override
    public void onDetach() {
        handler.removeCallbacksAndMessages(null);
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();
        if (sentTransaction != null)
            sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);
        EventBus.getDefault().unregister(this);
        countDownTimer.cancel();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        saveInstanceState(outState);
    }

    private void saveInstanceState(final Bundle outState) {
        outState.putSerializable("state", state);
        outState.putParcelable("payment_intent", paymentIntent);
        outState.putSerializable("fee_category", feeCategory);
        if (validatedAddress != null)
            outState.putParcelable("validated_address", validatedAddress);

        outState.putString("assetId", assetId);

        if (sentTransaction != null)
            outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());
        if (directPaymentAck != null)
            outState.putBoolean("direct_payment_ack", directPaymentAck);
    }

    private void restoreInstanceState(final Bundle savedInstanceState) {
        state = (State) savedInstanceState.getSerializable("state");

        paymentIntent = (PaymentIntent) savedInstanceState.getParcelable("payment_intent");
        feeCategory = (FeeCategory) savedInstanceState.getSerializable("fee_category");
        validatedAddress = savedInstanceState.getParcelable("validated_address");

        assetId = savedInstanceState.getString("assetId");

        if (!isSafe()) {
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                issue = (IssueData) dao.queryForFirst("assetId", assetId);
            } catch (SQLException e) {
                getActivity().finish();
            }
        }

        if (savedInstanceState.containsKey("sent_transaction_hash")) {
            sentTransaction = wallet
                    .getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
            sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
        }
        if (savedInstanceState.containsKey("direct_payment_ack"))
            directPaymentAck = savedInstanceState.getBoolean("direct_payment_ack");
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                onActivityResultResumed(requestCode, resultCode, intent);
            }
        });
    }

    private void onActivityResultResumed(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                new StringInputParser(input) {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        send_coins_amount_btc_edittext.setText(null);
                        setState(null);
                        updateStateFrom(paymentIntent);
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                        cannotClassify(input);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        dialog(activity, null, R.string.button_scan, R.string.address_book_options_scan_invalid, "");
                    }
                }.parse();
            }
        } else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST) {
            if (paymentIntent.isBluetoothPaymentRequestUrl())
                requestPaymentRequest();
        } else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH_FOR_DIRECT_PAYMENT) {
            if (paymentIntent.isBluetoothPaymentUrl())
                directPaymentEnableView.setChecked(resultCode == Activity.RESULT_OK);
        }
    }

    private Menu menu;

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.send_coins_fragment_options, menu);
        super.onCreateOptionsMenu(menu, inflater);
        this.menu = menu;
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        if (feeCategory == FeeCategory.NORMAL)
            menu.findItem(R.id.send_coins_options_fee_category_normal).setChecked(true);
        else if (feeCategory == FeeCategory.PRIORITY)
            menu.findItem(R.id.send_coins_options_fee_category_priority).setChecked(true);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send_coins_options_fee_category_normal:
                handleFeeCategory(FeeCategory.NORMAL);
                menu.findItem(R.id.send_coins_options_fee_category_normal).setChecked(true);
                return true;
            case R.id.send_coins_options_fee_category_priority:
                handleFeeCategory(FeeCategory.PRIORITY);
                menu.findItem(R.id.send_coins_options_fee_category_priority).setChecked(true);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void validateReceivingAddress() {
        try {
            final String addressStr = receivingAddressView.getText().toString().trim();
            if (!addressStr.isEmpty()
                    && Constants.NETWORK_PARAMETERS.equals(Address.getParametersFromAddress(addressStr))) {
                final String label = AddressBookProvider.resolveLabel(activity, addressStr);
                validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, addressStr, label);
                receivingAddressView.setText(null);
            }
        } catch (final AddressFormatException e) {
        }
    }

    private void handleCancel() {
        if (state == null || state.compareTo(State.INPUT) <= 0)
            activity.setResult(Activity.RESULT_CANCELED);

        activity.finish();
    }

    private boolean isPayeePlausible() {
        if (paymentIntent.hasOutputs())
            return true;
        if (validatedAddress != null)
            return true;
        return false;
    }

    private boolean isAmountPlausible() {
        if (dryrunTransaction != null)
            return dryrunException == null;
        else if (paymentIntent.mayEditAmount())
            return amountCalculatorLink.hasAmount();
        else
            return paymentIntent.hasAmount();
    }

    private boolean isPasswordPlausible() {
        if (!wallet.isEncrypted())
            return true;
        return !privateKeyPasswordView.getText().toString().trim().isEmpty();
    }

    private boolean everythingPlausible() {
        return state == State.INPUT && isPayeePlausible() && isAmountPlausible() && isPasswordPlausible();
    }

    private void requestFocusFirst() {
        if (!isPayeePlausible())
            receivingAddressView.requestFocus();
        else if (!isAmountPlausible())
            amountCalculatorLink.requestFocus();
        else if (!isPasswordPlausible())
            privateKeyPasswordView.requestFocus();
        else if (everythingPlausible())
            viewGo.requestFocus();
        else
            log.warn("unclear focus");
    }

    private void handleGo() {

        if (isSafe() && instantXenable.isChecked() && isSixConfirm()) {
            final DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            if (SafeConstant.getLastBlockHeight() >= Constants.NETWORK_PARAMETERS.getStartSposHeight()) {
                dialog.setMessage(getString(R.string.need_sixe_confirm, 15 * (60 / CoinDefinition.SPOS_TARGET_SPACING)));
            } else {
                dialog.setMessage(getString(R.string.need_sixe_confirm, 6));
            }
            dialog.setPositiveButton(R.string.button_ok, null);
            dialog.show();
            return;
        }

        if (issue != null) {
            Transaction tx = wallet.getTransaction(Sha256Hash.wrap(issue.txId));
            if (tx != null) {
                TransactionConfidence confidence = tx.getConfidence();
                TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
                if (confidenceType != TransactionConfidence.ConfidenceType.BUILDING) {
                    new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_tx_unconfirmed));
                    return;
                }
            }
        }

        CommonUtils.showProgressDialog(activity, null, false);
        privateKeyBadPasswordView.setVisibility(View.INVISIBLE);
        if (wallet.isEncrypted()) {
            new DeriveKeyTask(backgroundHandler, application.scryptIterationsTarget()) {
                @Override
                protected void onSuccess(final KeyParameter encryptionKey, final boolean wasChanged) {
                    if (wasChanged)
                        application.backupWallet();
                    signAndSendPayment(encryptionKey);
                }
            }.deriveKey(wallet, privateKeyPasswordView.getText().toString().trim());
            setState(State.DECRYPTING);
        } else {
            signAndSendPayment(null);
        }
    }

    private void signAndSendPayment(final KeyParameter encryptionKey) {
        setState(State.SIGNING);
        SendRequest sendRequest;

        int month = 0;
        if (lockTx.isChecked()) {
            month = monthEdit.getNumValue();
        }

        if (isSafe()) {

            Address address = validatedAddress != null ? validatedAddress.address : null;
            PaymentIntent finalPaymentIntent = paymentIntent.mergeWithEditedValues(amountCalculatorLink.getAmount(), address);
            Coin finalAmount = finalPaymentIntent.getAmount();
            boolean usingInstantSend = instantXenable.isChecked();

            finalPaymentIntent.setInstantX(usingInstantSend);

            sendRequest = finalPaymentIntent.toSendRequest(SafeUtils.getLockHeight(month));
            sendRequest.appCommand = 0;
            sendRequest.useInstantSend = usingInstantSend;
            sendRequest.emptyWallet = paymentIntent.mayEditAmount()
                    && finalAmount.equals(wallet.getBalance(BalanceType.AVAILABLE, assetId, null));
            sendRequest.feePerKb = fees.get(feeCategory);
            sendRequest.feePerKb = sendRequest.useInstantSend ? TransactionLockRequest.MIN_FEE : sendRequest.feePerKb;
            sendRequest.memo = paymentIntent.memo;
            sendRequest.aesKey = encryptionKey;
            if (usingInstantSend)
                sendRequest.ensureMinRequiredFee = true;
            else if (feeCategory == FeeCategory.ECONOMIC || feeCategory == FeeCategory.ZERO)
                sendRequest.ensureMinRequiredFee = false;  //Allow for below the reference fee transactions
            else sendRequest.ensureMinRequiredFee = true;

        } else {

            try {
                sendRequest = getAssetSendRequest();
                if (sendRequest == null) {
                    CommonUtils.dismissProgressDialog(activity);
                    return;
                }
                sendRequest.aesKey = encryptionKey;
            } catch (InsufficientMoneyAssetException e) {
                CommonUtils.dismissProgressDialog(activity);
                return;
            }

        }
        new SendCoinsOfflineTask(wallet, backgroundHandler) {
            @Override
            protected void onSuccess(final Transaction transaction) {
                CommonUtils.dismissProgressDialog(activity);
                sentTransaction = transaction;

                setState(State.SENDING);

                sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

                final Address refundAddress = paymentIntent.standard == Standard.BIP70
                        ? wallet.freshAddress(KeyPurpose.REFUND) : null;
                final Payment payment = PaymentProtocol.createPaymentMessage(
                        Arrays.asList(new Transaction[]{sentTransaction}), amountCalculatorLink.getAmount(), refundAddress, null,
                        paymentIntent.payeeData);

                if (directPaymentEnableView.isChecked())
                    directPay(payment);

                application.broadcastTransaction(sentTransaction);

                final ComponentName callingActivity = activity.getCallingActivity();
                if (callingActivity != null) {
                    log.info("returning result to calling activity: {}", callingActivity.flattenToString());

                    final Intent result = new Intent();
                    BitcoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
                    if (paymentIntent.standard == Standard.BIP70)
                        BitcoinIntegration.paymentToResult(result, payment.toByteArray());
                    activity.setResult(Activity.RESULT_OK, result);
                }
                countDownTimer.start();
            }

            private void directPay(final Payment payment) {
                final DirectPaymentTask.ResultCallback callback = new DirectPaymentTask.ResultCallback() {
                    @Override
                    public void onResult(final boolean ack) {
                        directPaymentAck = ack;

                        if (state == State.SENDING)
                            setState(State.SENT);

                        updateView();
                    }

                    @Override
                    public void onFail(final int messageResId, final Object... messageArgs) {
                        final DialogBuilder dialog = DialogBuilder.warn(activity,
                                R.string.send_coins_fragment_direct_payment_failed_title);
                        dialog.setMessage(paymentIntent.paymentUrl + "\n" + getString(messageResId, messageArgs)
                                + "\n\n" + getString(R.string.send_coins_fragment_direct_payment_failed_msg));
                        dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                directPay(payment);
                            }
                        });
                        dialog.setNegativeButton(R.string.button_dismiss, null);
                        dialog.setCancelable(false);
                        dialog.show();
                    }
                };

                if (paymentIntent.isHttpPaymentUrl()) {
                    new DirectPaymentTask.HttpPaymentTask(backgroundHandler, callback, paymentIntent.paymentUrl,
                            application.httpUserAgent()).send(payment);
                } else if (paymentIntent.isBluetoothPaymentUrl() && bluetoothAdapter != null
                        && bluetoothAdapter.isEnabled()) {
                    new DirectPaymentTask.BluetoothPaymentTask(backgroundHandler, callback, bluetoothAdapter,
                            Bluetooth.getBluetoothMac(paymentIntent.paymentUrl)).send(payment);
                }
            }

            @Override
            protected void onInsufficientMoney(final InsufficientMoneyException e) {
                CommonUtils.dismissProgressDialog(activity);
                setState(State.INPUT);
                final MonetaryFormat btcFormat = config.getFormat();
                final DialogBuilder dialog = DialogBuilder.warn(activity,
                        R.string.send_coins_fragment_insufficient_money_title);
                final StringBuilder msg = new StringBuilder();
                Coin maxSafe = Coin.valueOf(100000000000L);
                if (e.useInstantOutTooMax) {
                    msg.append(getString(R.string.send_coins_instantx_out_too_max, btcFormat.format(maxSafe)));
                } else if (e.useInstantInTooMax) {
                    msg.append(getString(R.string.send_coins_instantx_in_too_max,
                            btcFormat.format(((InsufficientMoneyException) dryrunException).missing), btcFormat.format(maxSafe)));
                } else if (e.useTxLimit) {
                    msg.append(getString(R.string.safe_tx_send_limit));
                } else {
                    msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg1, btcFormat.format(e.missing)));
                }
                dialog.setMessage(msg);
                dialog.setPositiveButton(R.string.button_dismiss, null);
                dialog.setCancelable(false);
                dialog.show();
            }

            @Override
            protected void onInvalidEncryptionKey() {
                CommonUtils.dismissProgressDialog(activity);
                setState(State.INPUT);
                privateKeyBadPasswordView.setVisibility(View.VISIBLE);
                privateKeyPasswordView.requestFocus();
            }

            @Override
            protected void onEmptyWalletFailed() {
                CommonUtils.dismissProgressDialog(activity);
                setState(State.INPUT);
                DialogBuilder dialog = DialogBuilder.warn(activity,
                        R.string.send_coins_fragment_empty_wallet_failed_title);
                dialog.setMessage(R.string.send_coins_fragment_hint_empty_wallet_failed);
                dialog.setPositiveButton(R.string.button_dismiss, null);
                dialog.setCancelable(false);
                dialog.show();
            }

            @Override
            protected void onFailure(Exception ex) {
                CommonUtils.dismissProgressDialog(activity);
                setState(State.FAILED);
                DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_error_msg);
                if (ex instanceof Wallet.ExceededMaxTransactionSize) {
                    dialog.setMessage(R.string.transaction_size_failed);
                } else {
                    dialog.setMessage(ex.toString());
                }
                dialog.setPositiveButton(R.string.button_dismiss, null);
                dialog.setCancelable(false);
                dialog.show();
            }
        }.sendCoinsOffline(sendRequest); // send asynchronously
    }

    public SendRequest getAssetSendRequest() throws InsufficientMoneyAssetException {
        paymentIntent.setInstantX(false);
        SendRequest sendRequest;
        int month = 0;
        if (lockTx.isChecked()) {
            month = monthEdit.getNumValue();
        }
        SafeProtos.CommonData transferData = getTransferProtos();
        byte[] vReserve;
        try {
            vReserve = SafeUtils.serialReserve(SafeConstant.CMD_TRANSFER, issue.appId, transferData.toByteArray());
        } catch (Exception e) {
            DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            dialog.setMessage(e.getMessage());
            dialog.setPositiveButton(R.string.button_ok, null);
            dialog.setCancelable(false);
            dialog.show();
            return null;
        }

        Address address;
        if (validatedAddress != null) {
            address = validatedAddress.address;
        } else {
            if (paymentIntent.hasAddress()) {
                address = paymentIntent.getAddress();
            } else {
                return null;
            }
        }

        PaymentIntent.Output paymentIntentOutput = PaymentIntent.buildOutPut(Coin.valueOf(transferData.getAmount()), address, vReserve);
        PaymentIntent finalPaymentIntent = paymentIntent.getPaymentIntentOutput(new PaymentIntent.Output[]{paymentIntentOutput});
        Coin finalAmount = finalPaymentIntent.getAmount();
        sendRequest = finalPaymentIntent.toSendRequest(SafeUtils.getLockHeight(month));

        //---------处理资产找零---------
        List<TransactionOutput> allOutput = wallet.calculateAllSpendCandidates(true, sendRequest.missingSigsMode == Wallet.MissingSigsMode.THROW, assetId, null);
        List<TransactionOutput> candidates = new ArrayList<>();
        for (TransactionOutput output : allOutput) { //过滤锁定的金额
            if (output.getLockHeight() != 0 && SafeConstant.getLastBlockHeight() < output.getLockHeight()) {
                continue;
            } else {
                candidates.add(output);
            }
        }
        CoinSelection bestCoinSelection = wallet.getCoinSelector().select(finalAmount, candidates);
        for (TransactionOutput output : bestCoinSelection.gathered) {
            sendRequest.tx.addInput(output);
        }
        Coin bestValue = bestCoinSelection.valueGathered;
        if (bestValue.compareTo(finalAmount) > 0) {
            Coin changeValue = bestValue.subtract(finalAmount);
            SafeProtos.CommonData changeData = getAssetChangeProtos(changeValue.getValue());
            byte[] changeReserve;
            try {
                changeReserve = SafeUtils.serialReserve(SafeConstant.CMD_ASSET_CHANGE, issue.appId, changeData.toByteArray());
            } catch (Exception e) {
                DialogBuilder dialog = new DialogBuilder(getActivity());
                dialog.setTitle(R.string.safe_comm_title);
                dialog.setMessage(e.getMessage());
                dialog.setPositiveButton(R.string.button_ok, null);
                dialog.setCancelable(false);
                dialog.show();
                return null;
            }
            Address transAddress = getAssetAddress(issue.txId);
            if (transAddress == null) { //是否有管理员地址
                transAddress = wallet.currentReceiveAddress();
            }
            Script script = ScriptBuilder.createOutputScript(transAddress);
            TransactionOutput output = new TransactionOutput(Constants.NETWORK_PARAMETERS, sendRequest.tx, changeValue, script.getProgram(), 0, changeReserve);
            sendRequest.tx.addOutput(output);
            int assetSignSize = wallet.estimateBytesForSigning(bestCoinSelection); //试算资产找零交易签名的大小
            sendRequest.assetSignSize = assetSignSize;
        } else if (bestValue.compareTo(finalAmount) < 0) {
            setState(State.INPUT);
            Coin missing = finalAmount.subtract(bestValue);
            throw new InsufficientMoneyAssetException(missing);
        }
        //---------处理资产找零---------
        sendRequest.appCommand = SafeConstant.CMD_TRANSFER;
        sendRequest.useInstantSend = false;
        sendRequest.emptyWallet = false;
        sendRequest.feePerKb = fees.get(feeCategory);
        sendRequest.memo = paymentIntent.memo;
        sendRequest.ensureMinRequiredFee = true;
        return sendRequest;
    }

    private boolean isSixConfirm() {
        Map<Sha256Hash, Transaction> unspentTx = wallet.getTransactionPool(WalletTransaction.Pool.UNSPENT);
        Map<Sha256Hash, Transaction> pendingTx = wallet.getTransactionPool(WalletTransaction.Pool.PENDING);
        Map<Sha256Hash, Transaction> mTx = new HashMap<>();
        mTx.putAll(unspentTx);
        mTx.putAll(pendingTx);
        for (Transaction tx : mTx.values()) {
            if (SafeConstant.getLastBlockHeight() >= Constants.NETWORK_PARAMETERS.getStartSposHeight()) {
                if (tx.getConfidence().getDepthInBlocks() < 15 * (60 / CoinDefinition.SPOS_TARGET_SPACING)) {
                    return true;
                }
            } else {
                if (tx.getConfidence().getDepthInBlocks() < 6) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleScan() {

        activity.verifyPermissions(Manifest.permission.CAMERA, new AbstractBindServiceActivity.PermissionCallBack() {
            @Override
            public void onGranted() {
                startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
            }

            @Override
            public void onDenied() {

            }
        });
    }

    private void handleFeeCategory(final FeeCategory feeCategory) {
        this.feeCategory = feeCategory;
        updateView();
        handler.post(dryrunRunnable);
    }

    private void handleEmpty() {
        Coin available = wallet.getBalance(BalanceType.AVAILABLE);
        amountCalculatorLink.setBtcAmount(available);
        updateView();
        handler.post(dryrunRunnable);
    }

    private Runnable dryrunRunnable = new Runnable() {
        @Override
        public void run() {
            if (state == State.INPUT)
                executeDryrun();
            updateView();
        }

        private void executeDryrun() {
            dryrunTransaction = null;
            dryrunException = null;
            if (fees != null) {
                if (isSafe()) {
                    try {
                        Coin amount = amountCalculatorLink.getAmount();
                        if(amount == null){
                            return;
                        }
                        Address dummy = wallet.currentReceiveAddress();
                        SendRequest sendRequest = paymentIntent.mergeWithEditedValues(amount, dummy).toSendRequest(0);
                        sendRequest.useInstantSend = instantXenable.isChecked();
                        sendRequest.signInputs = false;
                        sendRequest.emptyWallet = paymentIntent.mayEditAmount() && amount.equals(wallet.getBalance(BalanceType.AVAILABLE, assetId, null));
                        sendRequest.feePerKb = fees.get(feeCategory);
                        sendRequest.feePerKb = sendRequest.useInstantSend ? TransactionLockRequest.MIN_FEE : sendRequest.feePerKb;
                        if (sendRequest.useInstantSend)
                            sendRequest.ensureMinRequiredFee = true;
                        else if (feeCategory == FeeCategory.ECONOMIC || feeCategory == FeeCategory.ZERO)
                            sendRequest.ensureMinRequiredFee = false;  //Allow for below the reference fee transactions
                        else sendRequest.ensureMinRequiredFee = true;
                        wallet.completeTx(sendRequest);
                        dryrunTransaction = sendRequest.tx;
                    } catch (final Exception e) {
                        dryrunException = e;
                    }
                } else {
                    try {
                        String amount = amountCalculatorLink.activeTextView().getText().toString();
                        if(TextUtils.isEmpty(amount)){
                            return;
                        }
                        SendRequest sendRequest = getAssetSendRequest();
                        if (sendRequest == null) return;
                        sendRequest.signInputs = false;
                        wallet.completeTx(sendRequest);
                        dryrunTransaction = sendRequest.tx;
                    } catch (Exception e) {
                        dryrunException = e;
                    }
                }
            }
        }
    };

    private void setState(final State state) {
        this.state = state;
        activity.invalidateOptionsMenu();
        updateView();
    }

    private void updateView() {
        if (!isResumed())
            return;

        if (paymentIntent != null) {
            final MonetaryFormat btcFormat = config.getMaxPrecisionFormat();

            getView().setVisibility(View.VISIBLE);

            if (paymentIntent.hasPayee()) {
                payeeNameView.setVisibility(View.VISIBLE);
                payeeNameView.setText(paymentIntent.payeeName);

                payeeVerifiedByView.setVisibility(View.VISIBLE);
                final String verifiedBy = paymentIntent.payeeVerifiedBy != null ? paymentIntent.payeeVerifiedBy
                        : getString(R.string.send_coins_fragment_payee_verified_by_unknown);
                payeeVerifiedByView.setText(Constants.CHAR_CHECKMARK + String.format(getString(R.string.send_coins_fragment_payee_verified_by), verifiedBy));
            } else {
                payeeNameView.setVisibility(View.GONE);
                payeeVerifiedByView.setVisibility(View.GONE);
            }

            if (paymentIntent.hasOutputs()) {
                payeeGroup.setVisibility(View.VISIBLE);
                receivingAddressView.setVisibility(View.GONE);
                receivingStaticView.setVisibility(
                        !paymentIntent.hasPayee() || paymentIntent.payeeVerifiedBy == null ? View.VISIBLE : View.GONE);

                receivingStaticLabelView.setText(paymentIntent.memo);

                if (paymentIntent.hasAddress()) {
                    receivingStaticAddressView.setText(WalletUtils.formatAddress(paymentIntent.getAddress(),
                            Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                    //添加更换地址功能
                    clearAddress();

                } else {
                    receivingStaticAddressView.setText(R.string.send_coins_fragment_receiving_address_complex);
                }
            } else if (validatedAddress != null) {
                payeeGroup.setVisibility(View.VISIBLE);
                receivingAddressView.setVisibility(View.GONE);
                receivingStaticView.setVisibility(View.VISIBLE);

                receivingStaticAddressView.setText(WalletUtils.formatAddress(validatedAddress.address,
                        Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                //添加更换地址功能
                clearAddress();

                String addressBookLabel = AddressBookProvider.resolveLabel(activity,
                        validatedAddress.address.toBase58());
                final String staticLabel;
                if (addressBookLabel != null)
                    staticLabel = addressBookLabel;
                else if (validatedAddress.label != null)
                    staticLabel = validatedAddress.label;
                else
                    staticLabel = getString(R.string.address_unlabeled);
                receivingStaticLabelView.setText(staticLabel);
                receivingStaticLabelView.setTextColor(getResources()
                        .getColor(validatedAddress.label != null ? R.color.fg_significant : R.color.fg_insignificant));
            } else if (paymentIntent.standard == null) {
                payeeGroup.setVisibility(View.VISIBLE);
                receivingStaticView.setVisibility(View.GONE);
                receivingAddressView.setVisibility(View.VISIBLE);
            } else {
                payeeGroup.setVisibility(View.GONE);
            }

            receivingAddressView.setEnabled(state == State.INPUT);

            amountGroup.setVisibility(paymentIntent.hasAmount() || (state != null && state.compareTo(State.INPUT) >= 0)
                    ? View.VISIBLE : View.GONE);
            amountCalculatorLink.setEnabled(state == State.INPUT && paymentIntent.mayEditAmount());

            final boolean directPaymentVisible;
            if (paymentIntent.hasPaymentUrl()) {
                if (paymentIntent.isBluetoothPaymentUrl())
                    directPaymentVisible = bluetoothAdapter != null;
                else
                    directPaymentVisible = !Constants.BUG_OPENSSL_HEARTBLEED;
            } else {
                directPaymentVisible = false;
            }
            directPaymentEnableView.setVisibility(directPaymentVisible ? View.VISIBLE : View.GONE);
            directPaymentEnableView.setEnabled(state == State.INPUT);

            hintView.setVisibility(View.GONE);

            if (state == State.INPUT) {
                if (paymentIntent.mayEditAddress() && validatedAddress == null
                        && !receivingAddressView.getText().toString().trim().isEmpty()) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_receiving_address_error);
                } else if (getBlockSync()) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_hint_replaying);
                } else if (dryrunException != null) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_error));
                    hintView.setVisibility(View.VISIBLE);
                    if (dryrunException instanceof DustySendRequested)
                        hintView.setText(getString(R.string.send_coins_fragment_hint_dusty_send));
                    else if (dryrunException instanceof InsufficientMoneyException) {
                        boolean useInstantOutTooMax = ((InsufficientMoneyException) dryrunException).useInstantOutTooMax;
                        boolean useInstantInTooMax = ((InsufficientMoneyException) dryrunException).useInstantInTooMax;
                        boolean useTxLimit = ((InsufficientMoneyException) dryrunException).useTxLimit;
                        Coin maxSafe = Coin.valueOf(100000000000L);
                        if (useInstantOutTooMax) {
                            hintView.setText(getString(R.string.send_coins_instantx_out_too_max, btcFormat.format(maxSafe)));
                        } else if (useInstantInTooMax) {
                            hintView.setText(getString(R.string.send_coins_instantx_in_too_max,
                                    btcFormat.format(((InsufficientMoneyException) dryrunException).missing), btcFormat.format(maxSafe)));
                        } else if (useTxLimit) {
                            hintView.setText(getString(R.string.safe_tx_send_limit));
                        } else {
                            hintView.setText(getString(R.string.send_coins_fragment_hint_insufficient_money,
                                    btcFormat.format(((InsufficientMoneyException) dryrunException).missing)));
                        }
                    } else if (dryrunException instanceof InsufficientMoneyAssetException) {
                        Coin missing = ((InsufficientMoneyAssetException) dryrunException).missing;
                        String msg = SafeUtils.getAssetAmount(missing, issue.decimals);
                        hintView.setText(getString(R.string.send_asset_insufficient_money, issue.assetName, msg + issue.assetUnit));
                    } else if (dryrunException instanceof CouldNotAdjustDownwards) {
                        hintView.setText(getString(R.string.send_coins_fragment_hint_empty_wallet_failed));
                    } else if (dryrunException instanceof CouldNotAdjustDownwards) {
                        hintView.setText(getString(R.string.send_coins_fragment_hint_empty_wallet_failed));
                    } else {
//                        hintView.setText(dryrunException.toString());
                    }
                } else if (dryrunTransaction != null && dryrunTransaction.getFee() != null) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_insignificant));
                    hintView.setVisibility(View.VISIBLE);
                    final int hintResId;
                    if (feeCategory == FeeCategory.ECONOMIC && !instantXenable.isChecked())
                        hintResId = R.string.send_coins_fragment_hint_fee_economic;
                    else if (feeCategory == FeeCategory.PRIORITY && !instantXenable.isChecked())
                        hintResId = R.string.send_coins_fragment_hint_fee_priority;
                    else if (feeCategory == FeeCategory.ZERO && !instantXenable.isChecked())
                        hintResId = R.string.send_coins_fragment_hint_fee_zero;
                    else
                        hintResId = R.string.send_coins_fragment_hint_fee;
                    hintView.setText(getString(hintResId, btcFormat.format(dryrunTransaction.getFee())));
                } else if (paymentIntent.mayEditAddress() && validatedAddress != null
                        && wallet.isPubKeyHashMine(validatedAddress.address.getHash160())) {
                    hintView.setTextColor(getResources().getColor(R.color.fg_insignificant));
                    hintView.setVisibility(View.VISIBLE);
                    hintView.setText(R.string.send_coins_fragment_receiving_address_own);
                }
            }

            if (sentTransaction != null) {
                sentTransactionView.setVisibility(View.VISIBLE);
                sentTransactionAdapter.setFormat(btcFormat);
                if (isSafe()) {
                    sentTransactionAdapter.replace(sentTransaction, assetId, Coin.SMALLEST_UNIT_EXPONENT);
                } else {
                    sentTransactionAdapter.replace(sentTransaction, assetId, issue.decimals);
                }
                sentTransactionAdapter.bindViewHolder(sentTransactionViewHolder, 0);
            } else {
                sentTransactionView.setVisibility(View.GONE);
            }

            if (directPaymentAck != null) {
                directPaymentMessageView.setVisibility(View.VISIBLE);
                directPaymentMessageView.setText(directPaymentAck ? R.string.send_coins_fragment_direct_payment_ack
                        : R.string.send_coins_fragment_direct_payment_nack);
            } else {
                directPaymentMessageView.setVisibility(View.GONE);
            }

            if (everythingPlausible() && dryrunTransaction != null && fees != null
                    && !getBlockSync()) {
                viewGo.setEnabled(true);
            } else {
                viewGo.setEnabled(false);
            }

            final boolean privateKeyPasswordViewVisible = (state == State.INPUT || state == State.DECRYPTING)
                    && wallet.isEncrypted();
            privateKeyPasswordViewGroup.setVisibility(privateKeyPasswordViewVisible ? View.VISIBLE : View.GONE);
            privateKeyPasswordView.setEnabled(state == State.INPUT);

            // focus linking
            final int activeAmountViewId = amountCalculatorLink.activeTextView().getId();
            receivingAddressView.setNextFocusDownId(activeAmountViewId);
            receivingAddressView.setNextFocusForwardId(activeAmountViewId);
            amountCalculatorLink.setNextFocusId(
                    privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : R.id.send_coins_go);
            privateKeyPasswordView.setNextFocusUpId(activeAmountViewId);
            privateKeyPasswordView.setNextFocusDownId(R.id.send_coins_go);
            privateKeyPasswordView.setNextFocusForwardId(R.id.send_coins_go);
            viewGo.setNextFocusUpId(
                    privateKeyPasswordViewVisible ? R.id.send_coins_private_key_password : activeAmountViewId);
        } else {
            getView().setVisibility(View.GONE);
        }
    }

    private void initStateFromIntentExtras(final Bundle extras) {
        final PaymentIntent paymentIntent = extras.getParcelable(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT);
        final FeeCategory feeCategory = (FeeCategory) extras
                .getSerializable(SendCoinsActivity.INTENT_EXTRA_FEE_CATEGORY);

        if (feeCategory != null) {
            log.info("got fee category {}", feeCategory);
            this.feeCategory = feeCategory;
        }

        updateStateFrom(paymentIntent);
    }

    private void initStateFromBitcoinUri(final Uri bitcoinUri) {
        final String input = bitcoinUri.toString();

        new StringInputParser(input) {
            @Override
            protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                updateStateFrom(paymentIntent);
            }

            @Override
            protected void handlePrivateKey(final VersionedChecksummedBytes key) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs) {
                dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
            }
        }.parse();
    }

    private void initStateFromPaymentRequest(final String mimeType, final byte[] input) {
        new BinaryInputParser(mimeType, input) {
            @Override
            protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                updateStateFrom(paymentIntent);
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs) {
                dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
            }
        }.parse();
    }

    private void initStateFromIntentUri(final String mimeType, final Uri bitcoinUri) {
        try {
            final InputStream is = contentResolver.openInputStream(bitcoinUri);

            new StreamInputParser(mimeType, is) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    updateStateFrom(paymentIntent);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(activity, activityDismissListener, 0, messageResId, messageArgs);
                }
            }.parse();
        } catch (final FileNotFoundException x) {
            throw new RuntimeException(x);
        }
    }

    private void updateStateFrom(final PaymentIntent paymentIntent) {

        this.paymentIntent = paymentIntent;

        validatedAddress = null;
        directPaymentAck = null;

        // delay these actions until fragment is resumed
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (paymentIntent.hasPaymentRequestUrl() && paymentIntent.isBluetoothPaymentRequestUrl()) {
                    if (bluetoothAdapter.isEnabled())
                        requestPaymentRequest();
                    else
                        // ask for permission to enable bluetooth
                        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                                REQUEST_CODE_ENABLE_BLUETOOTH_FOR_PAYMENT_REQUEST);
                } else if (paymentIntent.hasPaymentRequestUrl() && paymentIntent.isHttpPaymentRequestUrl()
                        && !Constants.BUG_OPENSSL_HEARTBLEED) {
                    requestPaymentRequest();
                } else {
                    setState(State.INPUT);

                    receivingAddressView.setText(null);

                    if (TextUtils.isEmpty(paymentIntent.assetName) || SafeConstant.SAFE_FLAG.equalsIgnoreCase(paymentIntent.assetName)) { //扫描的是SAFE
                        if (!paymentIntent.hasAddress() || isSafe()) {
                            amountCalculatorLink.setBtcAmount(paymentIntent.getAmount());
                        } else {
                            if (paymentIntent.getAmount() != null && !paymentIntent.getAmount().isZero()) {
                                String reqName, sendName;
                                if (!TextUtils.isEmpty(paymentIntent.assetName)) {
                                    reqName = paymentIntent.assetName;
                                } else {
                                    reqName = SafeConstant.SAFE_FLAG.toUpperCase();
                                }
                                if (isSafe()) {
                                    sendName = SafeConstant.SAFE_FLAG.toUpperCase();
                                } else {
                                    sendName = issue.assetName;
                                }
                                DialogBuilder dialog = DialogBuilder.warn(activity, R.string.safe_comm_title);
                                dialog.setMessage(getString(R.string.safe_request_asset_send_assset_error, reqName, sendName));
                                dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                });
                                dialog.setCancelable(false);
                                dialog.show();
                            }
                        }
                    } else { //扫描的是资产
                        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
                            @Override
                            public void run() {
                                BaseDaoImpl assetDao = new BaseDaoImpl(IssueData.class);
                                try {
                                    scanIssue = (IssueData) assetDao.queryForFirst("assetName", paymentIntent.assetName);
                                    if (scanIssue != null) {
                                        if (scanIssue.assetId.equalsIgnoreCase(assetId)) {
                                            Coin value = paymentIntent.getAmount();
                                            if (value != null) {
                                                BigDecimal valueDecimal = new BigDecimal(value.getValue());
                                                BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(scanIssue.decimals));
                                                final String realValue = valueDecimal.divide(realDecimal).toString();
                                                BackgroundThread.postUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        amountCalculatorLink.activeTextView().setText(realValue);
                                                    }
                                                });
                                            }
                                        } else {
                                            String reqName, sendName;
                                            if (!TextUtils.isEmpty(paymentIntent.assetName)) {
                                                reqName = paymentIntent.assetName;
                                            } else {
                                                reqName = SafeConstant.SAFE_FLAG.toUpperCase();
                                            }
                                            if (isSafe()) {
                                                sendName = SafeConstant.SAFE_FLAG.toUpperCase();
                                            } else {
                                                sendName = issue.assetName;
                                            }
                                            DialogBuilder dialog = DialogBuilder.warn(activity, R.string.safe_comm_title);
                                            dialog.setMessage(getString(R.string.safe_request_asset_send_assset_error, reqName, sendName));
                                            dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {

                                                }
                                            });
                                            dialog.setCancelable(false);
                                            dialog.show();
                                        }
                                    } else {
//                                        DialogBuilder dialog = DialogBuilder.warn(activity, R.string.safe_comm_title);
//                                        dialog.setMessage(getString(R.string.hint_asset_not_exist));
//                                        dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
//                                            @Override
//                                            public void onClick(DialogInterface dialog, int which) {
//
//                                            }
//                                        });
//                                        dialog.setCancelable(false);
//                                        dialog.show();
                                    }
                                } catch (SQLException e) {
                                }
                            }
                        }));
                    }

                    if (paymentIntent.isBluetoothPaymentUrl())
                        directPaymentEnableView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
                    else if (paymentIntent.isHttpPaymentUrl())
                        directPaymentEnableView.setChecked(!Constants.BUG_OPENSSL_HEARTBLEED);

                    lockTx.setChecked(false);
                    monthEdit.setValue(defaultLockValue);

                    if (isSafe()) {
                        instantXenable.setChecked(paymentIntent.getUseInstantSend());
                    } else {
                        paymentIntent.setInstantX(false);
                        instantXenable.setChecked(false);
                    }

                    requestFocusFirst();
                    updateView();
                    handler.post(dryrunRunnable);
                }
            }
        });
    }

    private void requestPaymentRequest() {
        final String host;
        if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
            host = Uri.parse(paymentIntent.paymentRequestUrl).getHost();
        else
            host = Bluetooth.decompressMac(Bluetooth.getBluetoothMac(paymentIntent.paymentRequestUrl));

        ProgressDialogFragment.showProgress(fragmentManager,
                getString(R.string.send_coins_fragment_request_payment_request_progress, host));
        setState(State.REQUEST_PAYMENT_REQUEST);

        final RequestPaymentRequestTask.ResultCallback callback = new RequestPaymentRequestTask.ResultCallback() {
            @Override
            public void onPaymentIntent(final PaymentIntent paymentIntent) {
                ProgressDialogFragment.dismissProgress(fragmentManager);

                if (SendCoinsFragment.this.paymentIntent.isExtendedBy(paymentIntent)) {
                    // success
                    setState(State.INPUT);
                    updateStateFrom(paymentIntent);
                    updateView();
                    handler.post(dryrunRunnable);
                } else {
                    final StringBuilder reasons = new StringBuilder();
                    if (!SendCoinsFragment.this.paymentIntent.equalsAddress(paymentIntent))
                        reasons.append("address");
                    if (!SendCoinsFragment.this.paymentIntent.equalsAmount(paymentIntent))
                        reasons.append(reasons.length() == 0 ? "" : ", ").append("amount");
                    if (reasons.length() == 0)
                        reasons.append("unknown");

                    final DialogBuilder dialog = DialogBuilder.warn(activity,
                            R.string.send_coins_fragment_request_payment_request_failed_title);
                    dialog.setMessage(getString(R.string.send_coins_fragment_request_payment_request_wrong_signature)
                            + "\n\n" + reasons);
                    dialog.singleDismissButton(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            handleCancel();
                        }
                    });
                    dialog.setCancelable(false);
                    dialog.show();

                    log.info("BIP72 trust check failed: {}", reasons);
                }
            }

            @Override
            public void onFail(final int messageResId, final Object... messageArgs) {
                ProgressDialogFragment.dismissProgress(fragmentManager);

                final DialogBuilder dialog = DialogBuilder.warn(activity,
                        R.string.send_coins_fragment_request_payment_request_failed_title);
                dialog.setMessage(getString(messageResId, messageArgs));
                dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        requestPaymentRequest();
                    }
                });
                dialog.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        if (!paymentIntent.hasOutputs())
                            handleCancel();
                        else
                            setState(State.INPUT);
                    }
                });
                dialog.setCancelable(false);
                dialog.show();
            }
        };

        if (!Bluetooth.isBluetoothUrl(paymentIntent.paymentRequestUrl))
            new RequestPaymentRequestTask.HttpRequestTask(backgroundHandler, callback, application.httpUserAgent())
                    .requestPaymentRequest(paymentIntent.paymentRequestUrl);
        else
            new RequestPaymentRequestTask.BluetoothRequestTask(backgroundHandler, callback, bluetoothAdapter)
                    .requestPaymentRequest(paymentIntent.paymentRequestUrl);
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

    //封装转账Protos
    public SafeProtos.CommonData getTransferProtos() {
        String remarks = "Asset transfer";
        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);
        String amount = amountCalculatorLink.activeTextView().getText().toString();
        BigDecimal amtDecimal = new BigDecimal(amount);
        BigDecimal assetDecimal = amtDecimal.multiply(new BigDecimal(SafeUtils.getRealDecimals(issue.decimals)));
        return SafeProtos.CommonData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setAmount(assetDecimal.longValue())
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .build();
    }

    //封装找零Protos
    public SafeProtos.CommonData getAssetChangeProtos(long assetChangeAmount) {
        String remarks = "Asset change";
        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        return SafeProtos.CommonData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setAmount(assetChangeAmount)
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .build();
    }

    //清除地址
    public void clearAddress() {
        receivingStaticDeleteView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStateFrom(PaymentIntent.blank());
                clearLastData();
                updateView();
            }
        });
    }

    private void clearLastData() {
        validatedAddress = null;
        lockTx.setChecked(false);
        instantXenable.setChecked(false);
        monthEdit.setValue(defaultLockValue);
        privateKeyPasswordView.setText(null);
        dryrunTransaction = null;
        dryrunException = null;
    }

    public Address getAssetAddress(String txId) {
        Transaction tx = wallet.getTransaction(Sha256Hash.wrap(txId));
        if (tx != null) {
            for (TransactionOutput output : tx.getOutputs()) {
                SafeReserve reserve = SafeUtils.parseReserve(output.getReserve());
                if (reserve.isIssue()) {
                    return output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS);
                }
            }
        }
        return null;
    }

    public boolean getBlockSync() {
        if (blockchainState != null && blockchainState.bestChainDate != null) {
            final long blockchainLag = System.currentTimeMillis() - blockchainState.bestChainDate.getTime();
            final boolean blockSync = blockchainLag >= BLOCKCHAIN_UPTODATE_THRESHOLD_MS;
            if (blockSync) {
                return true;
            }
        }
        return false;
    }

    private CountDownTimer countDownTimer = new CountDownTimer(2000, 1000) {
        @Override
        public void onTick(long millisUntilFinished) {
        }

        @Override
        public void onFinish() {
            if (activity != null && !activity.isFinishing()) {
                activity.finish();
            }
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void showRejectDialog(final RejectMessage msg) {
        this.isFinishActivity = false;
        countDownTimer.cancel();
    }

//    public void changeSafe() {
//        issue = null;
//        assetId = SafeConstant.SAFE_FLAG;
//        String assetTitle = SafeConstant.SAFE_FLAG.toUpperCase() + getString(R.string.send_coins_activity_title);
//        getActivity().setTitle(assetTitle);
//        btcAmountView.setAssetIdAndDecimals(assetId, Coin.SMALLEST_UNIT_EXPONENT);
//        btcAmountView.setCurrencySymbol(config.getFormat().code());
//        btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
//        btcAmountView.setHintFormat(config.getFormat());
//        btcAmountView.getTextView().removeTextChangedListener(editTextJudgeNumber);
//        editTextJudgeNumber = new EditTextJudgeNumber(btcAmountView.getTextView(), Coin.SMALLEST_UNIT_EXPONENT, Coin.SMALLEST_UNIT_EXPONENT, true);
//        btcAmountView.getTextView().addTextChangedListener(editTextJudgeNumber);
//        amountCalculatorLink.setBtcAmount(paymentIntent.getAmount());
//    }
//
//    public void changeAsset() {
//        if (scanIssue != null) {
//            this.issue = scanIssue;
//            this.assetId = scanIssue.assetId;
//            String assetTitle = scanIssue.assetName + getString(R.string.send_coins_activity_title);
//            getActivity().setTitle(assetTitle);
//            btcAmountView.setAssetIdAndDecimals(assetId, scanIssue.decimals);
//            btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
//            btcAmountView.setHintFormat(config.getMaxPrecisionFormat());
//            int frontCount = String.valueOf(scanIssue.totalAmount).length();
//            int decimals = (int) scanIssue.decimals;
//            btcAmountView.getTextView().removeTextChangedListener(editTextJudgeNumber);
//            editTextJudgeNumber = new EditTextJudgeNumber(btcAmountView.getTextView(), frontCount - decimals, decimals, true);
//            btcAmountView.getTextView().addTextChangedListener(editTextJudgeNumber);
//            Coin value = paymentIntent.getAmount();
//            if (value != null) {
//                BigDecimal valueDecimal = new BigDecimal(value.getValue());
//                BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(scanIssue.decimals));
//                final String realValue = valueDecimal.divide(realDecimal).toString();
//                amountCalculatorLink.activeTextView().setText(realValue);
//            }
//        }
//    }

}
