package de.schildbach.wallet.ui.safe;

import android.content.DialogInterface;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.format.DateUtils;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainStateReceiver;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.send.SendCoinsOfflineTask;
import de.schildbach.wallet.R;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;

/**
 * 转账基础交易
 *
 * @author zhangmiao
 */
public abstract class SendBaseFragment extends BaseFragment {

    public AbstractBindServiceActivity activity;
    public WalletApplication application;
    public Configuration config;
    public Wallet wallet;

    protected PaymentIntent paymentIntent;
    private Transaction sentTransaction = null;
    private SendRequest sendRequest;

    private Handler handler = new Handler();
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private State state = null;

    @Nullable
    private BlockchainState blockchainState = null;
    private BlockchainStateReceiver blockchainStateReceiver = null;

    private static final long BLOCKCHAIN_UPTODATE_THRESHOLD_MS = DateUtils.HOUR_IN_MILLIS;

    public Handler getHandler() {
        return handler;
    }

    private enum State {
        DECRYPTING,
        SIGNING,
        SENDING,
        SENT,
        FAILED
    }

    public enum Failed {
        InsufficientMoney,
        InvalidEncry,
        EmptyWallet,
        Exception,
    }

    private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(final TransactionConfidence confidence, final TransactionConfidence.Listener.ChangeReason reason) {
            if (getActivity() == null || getActivity().isFinishing()) return;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isResumed())
                        return;
                    TransactionConfidence confidence = sentTransaction.getConfidence();

                    TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
                    int numBroadcastPeers = confidence.numBroadcastPeers();

                    if (state == SendBaseFragment.State.SENDING) {
                        if (confidenceType == TransactionConfidence.ConfidenceType.DEAD) {
                            setState(State.FAILED);
                        } else if (numBroadcastPeers > 0 || confidenceType == TransactionConfidence.ConfidenceType.BUILDING) {
                            setState(State.SENT);
                            countDownTimer.cancel();
                        }
                    }

                    if (reason == ChangeReason.SEEN_PEERS && confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
                        android.content.Context context = getActivity().getApplicationContext();
                        // play sound effect
                        final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers, "raw", context.getPackageName());
                        if (soundResId > 0)
                            RingtoneManager.getRingtone(context, Uri.parse("android.resource://" + context.getPackageName() + "/" + soundResId)).play();
                    }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.activity = (AbstractBindServiceActivity) getActivity();
        this.application = (WalletApplication) getActivity().getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
    }

    @Override
    public void serviceBinded() {
        super.serviceBinded();
        blockchainState = activity.getBlockchainService().getBlockchainState();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        paymentIntent = PaymentIntent.blank();
        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        EventBus.getDefault().register(this);
        blockchainStateReceiver = new BlockchainStateReceiver(getActivity(), new BlockchainStateReceiver.BlockchainStateListener() {
            @Override
            public void onCallBack(BlockchainState state) {
                blockchainState = state;
                if(!getBlockSync()){
                    isSyncFinish();
                }
            }
        });
    }

    public void sendTx(final SendRequest sendRequest) {
        this.sendRequest = sendRequest;
        if (wallet.isEncrypted()) {
            DecryptKeyDialogFragment.show(getFragmentManager(), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    failed(null);
                }
            });
            setState(State.DECRYPTING);
        } else {
            signAndSendPayment(null, this.sendRequest);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void sendPayment(KeyParameter encryptionKey) {
        signAndSendPayment(encryptionKey, this.sendRequest);
    }

    protected void signAndSendPayment(KeyParameter encryptionKey, SendRequest sendRequest) {
        setState(State.SIGNING);
        sendRequest.aesKey = encryptionKey;
        new SendCoinsOfflineTask(wallet, backgroundHandler) {
            @Override
            protected void onSuccess(final Transaction transaction) {
                setState(State.SENDING);
                sentTransaction = transaction;
                sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
                application.broadcastTransaction(sentTransaction);
                countDownTimer.start();
            }

            @Override
            protected void onInsufficientMoney(final InsufficientMoneyException e) {
                if (paymentIntent.appCommand == SafeConstant.CMD_ADD_ISSUE) {
                    setState(State.FAILED, Failed.InsufficientMoney);
                    return;
                } else {
                    setState(State.FAILED);
                }
                MonetaryFormat btcFormat = config.getMaxPrecisionFormat();
                DialogBuilder dialog = DialogBuilder.warn(getActivity(),
                        R.string.send_coins_fragment_insufficient_money_title);
                StringBuilder msg = new StringBuilder();
                msg.append("\n");
                if (e.useTxLimit) {
                    msg.append(getString(R.string.safe_tx_send_limit));
                } else {
                    msg.append(getString(R.string.send_coins_fragment_insufficient_money_msg1, btcFormat.format(e.missing)));
                }
                dialog.setMessage(msg);
                dialog.setNegativeButton(R.string.button_dismiss, null);
                dialog.setCancelable(false);
                dialog.show();
            }

            @Override
            protected void onInvalidEncryptionKey() {
                setState(State.FAILED);
                DialogBuilder dialog = DialogBuilder.warn(getActivity(),
                        R.string.import_export_keys_dialog_failure_title);
                dialog.setMessage(R.string.safe_hint_pin_error);
                dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DecryptKeyDialogFragment.show(getFragmentManager());
                        setState(State.DECRYPTING);
                    }
                });
                dialog.setNegativeButton(R.string.button_cancel, null);
                dialog.setCancelable(false);
                dialog.show();
            }

            @Override
            protected void onEmptyWalletFailed() {
                setState(State.FAILED);
                DialogBuilder dialog = DialogBuilder.warn(getActivity(),
                        R.string.send_coins_fragment_empty_wallet_failed_title);
                dialog.setMessage(R.string.send_coins_fragment_hint_empty_wallet_failed);
                dialog.setPositiveButton(R.string.button_dismiss, null);
                dialog.setCancelable(false);
                dialog.show();
            }

            @Override
            protected void onFailure(Exception e) {
                setState(State.FAILED);
                DialogBuilder dialog = DialogBuilder.warn(getActivity(), R.string.send_coins_error_msg);
                dialog.setMessage(e.toString());
                dialog.setPositiveButton(R.string.button_dismiss, null);
                dialog.setCancelable(false);
                dialog.show();
            }

        }.sendCoinsOffline(sendRequest);

    }

    @Override
    public void onResume() {
        super.onResume();
        blockchainStateReceiver.registerReceiver();
    }

    @Override
    public void onPause() {
        blockchainStateReceiver.unregisterReceiver();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();
        backgroundHandler.removeCallbacksAndMessages(null);
        if (sentTransaction != null)
            sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);
        EventBus.getDefault().unregister(this);
        countDownTimer.cancel();
        super.onDestroy();
    }

    public void setState(final State mState) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                state = mState;
                if (state == State.SIGNING) {
                    sending();
                } else if (state == State.SENT) {
                    sent();
                } else if (state == State.FAILED) {
                    failed(null);
                }
            }
        });
    }

    public void setState(final State mState, final Failed failed) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                state = mState;
                if (state == State.SIGNING) {
                    sending();
                } else if (state == State.SENT) {
                    sent();
                } else if (state == State.FAILED) {
                    failed(failed);
                }
            }
        });
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

    protected void showBlockSyncDilog() {
        final DialogBuilder dialog = new DialogBuilder(getActivity());
        dialog.setTitle(R.string.safe_comm_title);
        dialog.setMessage(getString(R.string.send_coins_fragment_hint_replaying));
        dialog.setPositiveButton(R.string.button_ok, null);
        dialog.setCancelable(false);
        dialog.show();
    }

    public abstract void sending();

    public abstract void sent();

    public abstract void failed(Failed failed);

    private CountDownTimer countDownTimer = new CountDownTimer(2000, 1000) {
        @Override
        public void onTick(long millisUntilFinished) {
        }

        @Override
        public void onFinish() {
            setState(State.SENT);
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void showRejectDialog(final RejectMessage msg) {
        setState(State.FAILED);
        countDownTimer.cancel();
    }

    public abstract void isSyncFinish();

}