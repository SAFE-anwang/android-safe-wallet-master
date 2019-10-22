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

package de.schildbach.wallet.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

public class BlockchainStateReceiver extends BroadcastReceiver {

    private Context context;
    private BlockchainStateListener listener;

    public BlockchainStateReceiver(Context context, BlockchainStateListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void registerReceiver() {
        if (context != null) {
            LocalBroadcastManager.getInstance(context).registerReceiver(this, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
        }
    }

    public void unregisterReceiver() {
        if (context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener != null) {
            listener.onCallBack(BlockchainState.fromIntent(intent));
        }
    }

    public interface BlockchainStateListener {
        void onCallBack(BlockchainState state);
    }
}
