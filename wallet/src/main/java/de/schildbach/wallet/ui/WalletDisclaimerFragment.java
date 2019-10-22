/*
 * Copyright 2013-2015 the original author or authors.
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

import java.util.Set;

import javax.annotation.Nullable;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainState.Impediment;
import de.schildbach.wallet.service.BlockchainStateReceiver;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public final class WalletDisclaimerFragment extends Fragment implements OnSharedPreferenceChangeListener {

    private Configuration config;

    @Nullable
    private BlockchainState blockchainState = null;
    private BlockchainStateReceiver blockchainStateReceiver;

    private TextView messageView;


    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        final WalletApplication application = (WalletApplication) activity.getApplication();
        this.config = application.getConfiguration();
        blockchainStateReceiver = new BlockchainStateReceiver(getActivity(), new BlockchainStateReceiver.BlockchainStateListener() {
            @Override
            public void onCallBack(BlockchainState state) {
                blockchainState = state;
                updateView();
            }
        });
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        messageView = (TextView) inflater.inflate(R.layout.wallet_disclaimer_fragment, container);

        messageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                HelpDialogFragment.page(getFragmentManager(), R.string.help_safety);
            }
        });

        return messageView;
    }

    @Override
    public void onResume() {
        super.onResume();

        config.registerOnSharedPreferenceChangeListener(this);

        blockchainStateReceiver.registerReceiver();

        updateView();
    }

    @Override
    public void onPause() {

        config.unregisterOnSharedPreferenceChangeListener(this);

        blockchainStateReceiver.unregisterReceiver();

        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_DISCLAIMER.equals(key))
            updateView();
    }

    private void updateView() {
        if (!isResumed())
            return;

        final boolean showDisclaimer = config.getDisclaimerEnabled();

        int progressResId = 0;
        if (blockchainState != null) {
            final Set<Impediment> impediments = blockchainState.impediments;
            if (impediments.contains(Impediment.STORAGE))
                progressResId = R.string.blockchain_state_progress_problem_storage;
            else if (impediments.contains(Impediment.NETWORK))
                progressResId = R.string.blockchain_state_progress_problem_network;
        }

        final SpannableStringBuilder text = new SpannableStringBuilder();
        if (progressResId != 0)
            text.append(Html.fromHtml("<b>" + getString(progressResId) + "</b>"));
        if (progressResId != 0 && showDisclaimer)
            text.append('\n');
        if (showDisclaimer)
            text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_safety)));
        messageView.setText(text);

        final View view = getView();
        final ViewParent parent = view.getParent();
        final View fragment = parent instanceof FrameLayout ? (FrameLayout) parent : view;
        fragment.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
    }

}
