package de.schildbach.wallet.ui.safe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.InputManager;
import de.schildbach.wallet.ui.send.DeriveKeyTask;
import de.schildbach.wallet.R;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * 解密对话框
 * @author zhangmiao
 */
public final class DecryptKeyDialogFragment extends DialogFragment {

    private static final String FRAGMENT_TAG = DecryptKeyDialogFragment.class.getName();

    private static DialogInterface.OnClickListener mOnDismissListener;
    private EditText password;

    public static void show(final FragmentManager fm) {
        show(fm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
    }

    public static void show(final FragmentManager fm, DialogInterface.OnClickListener onDismissListener) {
        final DecryptKeyDialogFragment fragment = new DecryptKeyDialogFragment();
        mOnDismissListener = onDismissListener;
        fragment.show(fm, FRAGMENT_TAG);
    }

    private WalletApplication application;
    private Activity activity;
    private Wallet wallet;

    HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private AlertDialog alertDialog;
    private Button positiveButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.activity = getActivity();
        this.application = (WalletApplication) this.activity.getApplication();
        this.wallet = application.getWallet();

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);
        positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setEnabled(!TextUtils.isEmpty(password.getText()));
        password.addTextChangedListener(privateKeyPasswordListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        password.removeTextChangedListener(privateKeyPasswordListener);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(activity);

        final View view = inflater.inflate(R.layout.decrypt_key_dialog, null);

        password = (EditText) view.findViewById(R.id.private_password);

        final DialogInterface.OnClickListener mListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (!TextUtils.isEmpty(password.getText())) {
                        CommonUtils.showProgressDialog(getActivity(), "", false);
                        new DeriveKeyTask(backgroundHandler, application.scryptIterationsTarget()) {
                            @Override
                            protected void onSuccess(final KeyParameter encryptionKey, final boolean wasChanged) {
                                if (wasChanged) {
                                    application.backupWallet();
                                }
                                EventBus.getDefault().post(encryptionKey);

                            }
                        }.deriveKey(wallet, password.getText().toString());
                    } else {
                        if (mOnDismissListener != null) {
                            mOnDismissListener.onClick(dialog, which);
                        }
                    }
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    if (mOnDismissListener != null) {
                        mOnDismissListener.onClick(dialog, which);
                    }
                }
            }
        };

        final DialogBuilder dialog = new DialogBuilder(activity);
        dialog.setTitle(R.string.encrypt_keys_dialog_title);
        dialog.setView(view);
        dialog.setNegativeButton(R.string.button_cancel, mListener);
        dialog.setPositiveButton(R.string.button_ok, mListener);
        alertDialog = dialog.create();
        return alertDialog;
    }

    private final TextWatcher privateKeyPasswordListener = new TextWatcher() {
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            positiveButton.setEnabled(s.length() > 0);
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void afterTextChanged(final Editable s) {
        }
    };

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        backgroundThread.getLooper().quit();
    }
}
