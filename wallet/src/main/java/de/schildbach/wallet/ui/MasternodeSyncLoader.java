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


import android.content.Context;
import android.content.AsyncTaskLoader;

import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.MasternodeSyncListener;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.utils.Threading;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author Andreas Schildbach
 */
public class MasternodeSyncLoader extends AsyncTaskLoader<Integer> {

    private MasternodeSync masternodeSync;
    private int masternodeSyncStatus;

    public MasternodeSyncLoader(final Context context, Wallet wallet) {
        super(context);

        this.masternodeSync = wallet.getContext().masternodeSync;
        this.masternodeSyncStatus = masternodeSync.getSyncStatusInt();
        masternodeSync.addEventListener(masternodeSyncListener, Threading.SAME_THREAD);
    }

    public MasternodeSyncLoader(final Context context, org.bitcoinj.core.Context dashContext) {
        super(context);
        this.masternodeSync = dashContext.masternodeSync;
        this.masternodeSyncStatus = masternodeSync.getSyncStatusInt();
        masternodeSync.addEventListener(masternodeSyncListener, Threading.SAME_THREAD);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        safeForceLoad();
    }

    @Override
    protected void onStopLoading() {
        masternodeSync.removeEventListener(masternodeSyncListener);
        super.onStopLoading();
    }

    @Override
    protected void onReset() {
        masternodeSync.removeEventListener(masternodeSyncListener);
        super.onReset();
    }

    @Override
    public Integer loadInBackground() {
        masternodeSyncStatus = masternodeSync.getSyncStatusInt();
        return masternodeSyncStatus;
    }

    private MasternodeSyncListener masternodeSyncListener = new MasternodeSyncListener() {
        @Override
        public void onSyncStatusChanged(int newStatus, double progress) {
            masternodeSyncStatus = newStatus;
            safeForceLoad();
        }
    };

    private void safeForceLoad() {
        try {
            forceLoad();
        } catch (final RejectedExecutionException x) {
        }
    }
}
