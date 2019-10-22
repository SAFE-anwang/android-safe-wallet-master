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

import javax.annotation.Nullable;

import android.app.AlertDialog;
import android.content.*;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;

import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;

import de.schildbach.wallet.ui.safe.MainActivity;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.R;

import org.bitcoinj.core.RejectMessage;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.WalletTransaction;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractBindServiceActivity extends AbstractWalletActivity {

    private AlertDialog mDialog;

    @Nullable
    private BlockchainService blockchainService;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            blockchainService = ((BlockchainServiceImpl.LocalBinder) binder).getService();
            serviceBinded();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            blockchainService = null;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, BlockchainServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        unbindService(serviceConnection);
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    public BlockchainService getBlockchainService() {
        return blockchainService;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void showRejectDialog(final RejectMessage msg) {
        if (mDialog != null && mDialog.isShowing()) {
            return;
        }
        if (msg.getReasonCode() == RejectMessage.RejectCode.CANDYLIMIT) {
            return;
        }
        final String msgStr = msg.toString();
        String startStr = "Reject: tx";
        String endStr = "for reason";
        int startIndex = msgStr.indexOf(startStr);
        int endIndex = msgStr.indexOf(endStr);
        final String txId = msgStr.substring(startIndex + startStr.length(), endIndex).trim();
        WalletApplication.getInstance().getWallet().killRejectTx(txId);
        if (msgStr.contains("bad-txns-forbid")) {
            final DialogBuilder mBuilder = DialogBuilder.warn(this, R.string.wallet_reject_title);
            mBuilder.setMessage(getString(R.string.hint_dash_reject_msg));
            mBuilder.setPositiveButton(getText(R.string.button_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dashRejectButtonOk();
                }
            });
            mDialog = mBuilder.create();
            mDialog.setCanceledOnTouchOutside(false);
            try {
                mDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if(msgStr.contains("invalid txout unlocked height")){
            final DialogBuilder mBuilder = DialogBuilder.warn(this, R.string.wallet_reject_title);
            mBuilder.setMessage(getString(R.string.hint_lock_height_reject_msg));
            mBuilder.setPositiveButton(getText(R.string.button_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dashRejectButtonOk();
                }
            });
            mDialog = mBuilder.create();
            mDialog.setCanceledOnTouchOutside(false);
            try {
                mDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if(msgStr.contains("more than the total number of candy issued")){
            final DialogBuilder mBuilder = DialogBuilder.warn(this, R.string.wallet_reject_title);
            mBuilder.setMessage(getString(R.string.safe_candy_been_finished));
            mBuilder.setPositiveButton(getText(R.string.button_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dashRejectButtonOk();
                    SafeUtils.deleteCandyRecord(txId);
                }
            });
            mDialog = mBuilder.create();
            mDialog.setCanceledOnTouchOutside(false);
            try {
                mDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            final DialogBuilder mBuilder = DialogBuilder.warn(this, R.string.wallet_reject_title);
            mBuilder.setMessage(getString(R.string.hint_reject_msg, msgStr));
            mBuilder.setPositiveButton(getText(R.string.repair_wallet), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    WalletApplication.getInstance().resetBlockchain();
                    Intent intent = new Intent(AbstractBindServiceActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });
            mDialog = mBuilder.create();
            mDialog.setCanceledOnTouchOutside(false);
            try {
                mDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public abstract void serviceBinded();

    private static final int VERIFY_CODE = 0x01; //申请权限代码

    private PermissionCallBack callBack;

    /**
     * 申请权限
     *
     * @param reqestPermissions
     * @param callBack
     */
    public void verifyPermissions(String reqestPermissions, PermissionCallBack callBack) {
        this.callBack = callBack;
        int checkPermission = ActivityCompat.checkSelfPermission(this, reqestPermissions);
        if (checkPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    AbstractBindServiceActivity.this,
                    new String[]{reqestPermissions},
                    VERIFY_CODE
            );
        } else {
            callBack.onGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (this.VERIFY_CODE == requestCode) {
            if (grantResults != null && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                callBack.onGranted();
            } else {
                // Permission Denied
                callBack.onDenied();
            }
        }
    }

    public interface PermissionCallBack {
        void onGranted();

        void onDenied();
    }

    public void dashRejectButtonOk() {

    }

}
