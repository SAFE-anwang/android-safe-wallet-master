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

package de.schildbach.wallet.ui;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import android.text.TextUtils;
import android.widget.*;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.EditTextJudgeNumber;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.ExchangeRate;
import de.schildbach.wallet.data.ExchangeRatesLoader;
import de.schildbach.wallet.data.ExchangeRatesProvider;
import de.schildbach.wallet.offline.AcceptBluetoothService;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.BitmapFragment;
import de.schildbach.wallet.util.Bluetooth;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.support.v7.widget.CardView;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * @author Andreas Schildbach
 */
public final class RequestCoinsFragment extends Fragment {

    private AbstractBindServiceActivity activity;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private ClipboardManager clipboardManager;

    private ImageView qrView;
    private BitmapDrawable qrCodeBitmap;
    private TextView requestAddress;
    private TextView copyUri,copy;

    private Address address;
    private CurrencyCalculatorLink amountCalculatorLink;

    //zhangmiao update asset send
    @Nullable
    private IssueData issue;
    private String assetId = SafeConstant.SAFE_FLAG;
    //zhangmiao update asset send

    private static final Logger log = LoggerFactory.getLogger(RequestCoinsFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractBindServiceActivity) activity;
        this.application = (WalletApplication) this.activity.getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        Intent intent = activity.getIntent();
        assetId = intent.getStringExtra("assetId");
        if(!isSafe()){
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                issue = (IssueData) dao.queryForFirst("assetId", assetId);
            } catch (SQLException e) {
                getActivity().finish();
            }
        }

        getActivity().setTitle(getString(R.string.receipt_code));
        BackgroundThread.prepareThread();

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.request_coins_fragment, container, false);

        qrView = (ImageView) view.findViewById(R.id.request_coins_qr);

        final CardView qrCardView = (CardView) view.findViewById(R.id.request_coins_qr_card);
        qrCardView.setCardBackgroundColor(Color.WHITE);
        qrCardView.setPreventCornerOverlap(false);
        qrCardView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                BitmapFragment.show(getFragmentManager(), qrCodeBitmap.getBitmap());
            }
        });

        requestAddress = (TextView) view.findViewById(R.id.request_address);

        copyUri = (TextView) view.findViewById(R.id.copy_uri);
        copy = (TextView) view.findViewById(R.id.copy);
        copy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCopy();
            }
        });

        copyUri.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCopyUri();
            }
        });

        final CurrencyAmountView btcAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_btc);
        if (isSafe()) {
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
            btcAmountView.getTextView().addTextChangedListener(new EditTextJudgeNumber(btcAmountView.getTextView(), frontCount, decimals,true));
        } else {
            btcAmountView.setAssetIdAndDecimals(assetId, issue.decimals);
            btcAmountView.setInputFormat(config.getMaxPrecisionFormat());
            btcAmountView.setHintFormat(config.getMaxPrecisionFormat());
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                IssueData issue = (IssueData) dao.queryForFirst("assetId", assetId);
                int frontCount = String.valueOf(issue.totalAmount).length();
                int decimals = (int)issue.decimals;
                btcAmountView.getTextView().addTextChangedListener(new EditTextJudgeNumber(btcAmountView.getTextView(), frontCount - decimals, decimals,true));
            } catch (SQLException e) {
            }
        }

        final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.request_coins_amount_local);
        localAmountView.setInputFormat(Constants.LOCAL_FORMAT);
        localAmountView.setHintFormat(Constants.LOCAL_FORMAT);
        amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);
        amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());

        return view;
    }

    @Override
    public void onActivityCreated(@android.support.annotation.Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                address = wallet.freshReceiveAddress();
                BackgroundThread.postUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateView();
                    }
                });
            }
        }));
    }

    @Override
    public void onResume() {
        super.onResume();
        amountCalculatorLink.setListener(new CurrencyAmountView.Listener() {
            @Override
            public void changed() {
                updateView();
            }

            @Override
            public void focusChanged(final boolean hasFocus) {
            }
        });
        updateView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
        BackgroundThread.destroyThread();
    }

    @Override
    public void onPause() {
        amountCalculatorLink.setListener(null);
        super.onPause();
    }

    private void handleCopy() {
        if (address != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("SAFE address", address.toBase58()));
            new Toast(activity).toast(R.string.request_coins_clipboard_address_msg);
        }
    }

    private void handleCopyUri() {
        if (address != null) {
            final Uri request = Uri.parse(determineBitcoinRequestStr());
            clipboardManager.setPrimaryClip(ClipData.newRawUri("SAFE payment request", request));
            log.info("payment request copied to clipboard: {}", request);
            new Toast(activity).toast(R.string.request_coins_clipboard_msg);
        }

    }

    private void updateView() {
        if (address == null || !isResumed())
            return;
        final String bitcoinRequest = determineBitcoinRequestStr();
        qrCodeBitmap = new BitmapDrawable(getResources(), Qr.bitmap(bitcoinRequest));
        qrCodeBitmap.setFilterBitmap(false);
        qrView.setImageDrawable(qrCodeBitmap);
        requestAddress.setText(address.toBase58());
    }

    private String determineBitcoinRequestStr() {
        final String amount = getAmount();
        final String ownName = config.getOwnName();
        String assetName = null;
        if (!isSafe()) {
            assetName = issue.assetName;
        }
        return BitcoinURI.convertToBitcoinURI(address, amount, ownName, null, assetName);
    }

    public String getAmount() {
        if (isSafe()) {
            if (amountCalculatorLink.getAmount() != null) {
                Coin amount = amountCalculatorLink.getAmount();
                return SafeUtils.getAssetAmount(amount, 8);
            } else {
                return null;
            }
        } else {
            return amountCalculatorLink.activeTextView().getText().toString();
        }
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
