/*
 * Copyright 2014-2015 the original author or authors.
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

package de.schildbach.wallet.ui.preference;

import java.net.InetAddress;

import android.content.Context;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.view.View;

/**
 * @author Andreas Schildbach
 */
public final class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener {
    private Activity activity;
    private WalletApplication application;
    private Configuration config;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Preference btcPrecisionPreference;
    private EditTextPreference trustedPeerPreference;
    private Preference trustedPeerOnlyPreference;
    private EditTextPreference ownPreference;

    private static final Logger log = LoggerFactory.getLogger(SettingsFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = getActivity();
        this.application = (WalletApplication) this.activity.getApplication();
        this.config = application.getConfiguration();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_settings);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());


        ownPreference = (EditTextPreference) findPreference(Configuration.PREFS_KEY_OWN_NAME);
        ownPreference.getEditText().setSingleLine();

        InputFilter filterEmpty = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend) {
                if (source.equals(" ") || source.toString().contentEquals("\n")) return "";
                else return null;
            }
        };
        ownPreference.getEditText().setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(10), filterEmpty
        });

        ownPreference.getEditText().setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    int len = ownPreference.getEditText().getText().length();
                    ownPreference.getEditText().setSelection(len);
                }
            }
        });

        btcPrecisionPreference = findPreference(Configuration.PREFS_KEY_BTC_PRECISION);
        btcPrecisionPreference.setOnPreferenceChangeListener(this);

        trustedPeerPreference = (EditTextPreference)findPreference(Configuration.PREFS_KEY_TRUSTED_PEER);
        trustedPeerPreference.getEditText().setSingleLine();
        trustedPeerPreference.getEditText().setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    int len = trustedPeerPreference.getEditText().getText().length();
                    trustedPeerPreference.getEditText().setSelection(len);
                }
            }
        });

        trustedPeerPreference.setOnPreferenceChangeListener(this);

        trustedPeerOnlyPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEER_ONLY);
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(this);

        updateTrustedPeer();

        getActivity().setTitle(getString(R.string.button_settings));
    }

    @Override
    public void onDestroy() {
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(null);
        trustedPeerPreference.setOnPreferenceChangeListener(null);
        btcPrecisionPreference.setOnPreferenceChangeListener(null);

        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (preference.equals(btcPrecisionPreference)) {
                    WalletBalanceWidgetProvider.updateWidgets(activity, application.getWallet());
                } else if (preference.equals(trustedPeerPreference)) {
                    application.stopBlockchainService();
                    updateTrustedPeer();
                } else if (preference.equals(trustedPeerOnlyPreference)) {
                    application.stopBlockchainService();
                }
            }
        });

        return true;
    }

    private void updateTrustedPeer() {
        final String trustedPeer = config.getTrustedPeerHost();

        if (trustedPeer == null) {
            trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_summary);
            trustedPeerOnlyPreference.setEnabled(false);
        } else {
            trustedPeerPreference.setSummary(
                    trustedPeer + "\n[" + getString(R.string.preferences_trusted_peer_resolve_progress) + "]");
            trustedPeerOnlyPreference.setEnabled(true);

            new ResolveDnsTask(backgroundHandler) {
                @Override
                protected void onSuccess(final InetAddress address) {
                    trustedPeerPreference.setSummary(trustedPeer);
                    log.info("trusted peer '{}' resolved to {}", trustedPeer, address);
                }

                @Override
                protected void onUnknownHost() {
                    if (getActivity() == null || getActivity().isFinishing()) {
                        return;
                    }
                    trustedPeerPreference.setSummary(trustedPeer + "\n["
                            + getString(R.string.preferences_trusted_peer_resolve_unknown_host) + "]");
                }
            }.resolve(trustedPeer);
        }
    }
}
