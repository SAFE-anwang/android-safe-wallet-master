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

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import android.content.*;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import de.schildbach.wallet.ui.safe.CurrentAddressLoader;

import org.bitcoinj.core.Address;
import org.bitcoinj.uri.BitcoinURI;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.safe.NewAddressLoader;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * @author Andreas Schildbach
 */
public final class WalletAddressFragment extends Fragment implements NfcAdapter.CreateNdefMessageCallback {

    private Activity activity;
    private WalletApplication application;
    private Configuration config;
    private LoaderManager loaderManager;
    private View view;

    @Nullable
    private NfcAdapter nfcAdapter;

    private TextView walletAddress;
    private ImageView currentAddressQrView;
    private TextView copyAddress, generateAddress;
    private View loadingView;
    private ProgressBar loadingBar;
    private TextView loadingEmpty;

    private BitmapDrawable currentAddressQrBitmap = null;
    private AddressAndLabel currentAddressQrAddress = null;
    private final AtomicReference<String> currentAddressUriRef = new AtomicReference<String>();

    private static final int ID_ADDRESS_LOADER = 0;
    public static boolean flag = false;
    private boolean generateNewAddress = false;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.activity = getActivity();
        this.application = (WalletApplication) this.activity.getApplication();
        this.config = application.getConfiguration();
        this.loaderManager = getLoaderManager();
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);

        if (nfcAdapter != null && nfcAdapter.isEnabled())
            nfcAdapter.setNdefPushMessageCallback(this, activity);

        loaderManager.initLoader(ID_ADDRESS_LOADER, null, addressLoaderCallbacks);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.wallet_address_fragment, container, false);

        currentAddressQrView = (ImageView) view.findViewById(R.id.bitcoin_address_qr);
        walletAddress = (TextView) view.findViewById(R.id.wallet_address);
        copyAddress = (TextView) view.findViewById(R.id.copy_address);
        generateAddress = (TextView) view.findViewById(R.id.generate_address);
        loadingView = (FrameLayout) view.findViewById(R.id.loading_view);
        loadingBar = (ProgressBar) view.findViewById(R.id.loading_bar);
        loadingEmpty = (TextView) view.findViewById(R.id.loading_empty);

        final CardView currentAddressQrCardView = (CardView) view.findViewById(R.id.bitcoin_address_qr_card);
        currentAddressQrCardView.setCardBackgroundColor(Color.WHITE);
        currentAddressQrCardView.setPreventCornerOverlap(false);
        currentAddressQrCardView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if(currentAddressQrAddress != null){
                    WalletAddressDialogFragment.show(getFragmentManager(), currentAddressQrAddress.address, currentAddressQrAddress.label);
                }
            }
        });
        copyAddress.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData mClipData = ClipData.newPlainText("safe", walletAddress.getText());
                cm.setPrimaryClip(mClipData);
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast( getString(R.string.copy_success));
            }
        });
        generateAddress.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (flag) {
                    new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.address_new_tips));
                } else {
                    generateNewAddress = true;
                    loaderManager.restartLoader(ID_ADDRESS_LOADER, null, addressLoaderCallbacks);
                }
            }
        });
        return view;
    }

    private void updateView() {
        currentAddressQrView.setImageDrawable(currentAddressQrBitmap);
    }


    private final LoaderCallbacks<Address> addressLoaderCallbacks = new LoaderManager.LoaderCallbacks<Address>() {
        @Override
        public Loader<Address> onCreateLoader(final int id, final Bundle args) {
            if (generateNewAddress) {
                generateNewAddress = false;
                return new NewAddressLoader(activity, application.getWallet(), config);
            } else {
                return new CurrentAddressLoader(activity, application.getWallet(), config);
            }
        }

        @Override
        public void onLoadFinished(final Loader<Address> loader, final Address currentAddress) {

            currentAddressQrAddress = new AddressAndLabel(currentAddress, config.getOwnName());
            final String addressStr = BitcoinURI.convertToBitcoinURI(currentAddress, null,
                    currentAddressQrAddress.label, null, null);

            currentAddressQrBitmap = new BitmapDrawable(getResources(), Qr.bitmap(addressStr));
            currentAddressQrBitmap.setFilterBitmap(false);

            currentAddressUriRef.set(addressStr);

            walletAddress.setText(currentAddress.toBase58());

            updateView();
        }

        @Override
        public void onLoaderReset(final Loader<Address> loader) {
        }
    };

    @Override
    public NdefMessage createNdefMessage(final NfcEvent event) {
        final String uri = currentAddressUriRef.get();
        if (uri != null)
            return new NdefMessage(new NdefRecord[]{NdefRecord.createUri(uri)});
        else
            return null;
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_ADDRESS_LOADER);
        super.onDestroy();
    }

    public void setEmptyView(CharSequence text){
        loadingView.setVisibility(View.VISIBLE);
        loadingBar.setVisibility(View.GONE);
        loadingEmpty.setVisibility(View.VISIBLE);
        loadingEmpty.setText(text);
    }

    public void setLoading(){
        loadingView.setVisibility(View.VISIBLE);
        loadingBar.setVisibility(View.VISIBLE);
        loadingEmpty.setVisibility(View.GONE);
    }

    public void setLoadFinish(){
        loadingView.setVisibility(View.GONE);
        loadingBar.setVisibility(View.GONE);
        loadingEmpty.setVisibility(View.GONE);
    }

}
