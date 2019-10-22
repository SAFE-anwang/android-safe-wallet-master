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

import android.app.Activity;
import de.schildbach.wallet.BuildConfig;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.R;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * @author Andreas Schildbach
 */
public final class AboutFragment extends PreferenceFragment {

    private WalletApplication application;

    private static final String KEY_ABOUT_VERSION = "about_version";

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        this.application = (WalletApplication) getActivity().getApplication();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_about);

        findPreference(KEY_ABOUT_VERSION)
                .setSummary(application.packageInfo().versionName + (BuildConfig.DEBUG ? " (debuggable)" : ""));

        getActivity().setTitle(getString(R.string.about_title));

    }
}
