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

package de.schildbach.wallet.ui.send;

import static com.google.common.base.Preconditions.checkState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionedChecksummedBytes;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ComparisonChain;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.DynamicFeeLoader;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.ProgressDialogFragment;
import de.schildbach.wallet.ui.ScanActivity;
import de.schildbach.wallet.ui.TransactionsAdapter;
import de.schildbach.wallet.util.MonetarySpannable;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public class SweepWalletFragment extends Fragment {
	private AbstractBindServiceActivity activity;
	private WalletApplication application;
	private Configuration config;
	private LoaderManager loaderManager;
	private FragmentManager fragmentManager;

	private final Handler handler = new Handler();
	private HandlerThread backgroundThread;
	private Handler backgroundHandler;

	private State state = State.DECODE_KEY;
	private VersionedChecksummedBytes privateKeyToSweep = null;
	@Nullable
	private Map<FeeCategory, Coin> fees = null;
	private Wallet walletToSweep = null;
	private Transaction sentTransaction = null;

	private TextView messageView;
	private View passwordViewGroup;
	private EditText passwordView;
	private View badPasswordView;
	private TextView balanceView;
	private View hintView;
	private FrameLayout sweepTransactionView;
	private TransactionsAdapter sweepTransactionAdapter;
	private RecyclerView.ViewHolder sweepTransactionViewHolder;
	private Button viewGo;
	private Button viewCancel;
	private FloatingActionButton viewFabScanQr;

	private MenuItem reloadAction;
	private MenuItem scanAction;

	private static final int ID_DYNAMIC_FEES_LOADER = 0;

	private static final int REQUEST_CODE_SCAN = 0;

	private enum State {
		DECODE_KEY, // ask for password
		CONFIRM_SWEEP, // displays balance and asks for confirmation
		PREPARATION, SENDING, SENT, FAILED // sending states
	}

	private static final Logger log = LoggerFactory.getLogger(SweepWalletFragment.class);

	private final LoaderCallbacks<Map<FeeCategory, Coin>> dynamicFeesLoaderCallbacks = new LoaderManager.LoaderCallbacks<Map<FeeCategory, Coin>>() {
		@Override
		public Loader<Map<FeeCategory, Coin>> onCreateLoader(final int id, final Bundle args) {
			return new DynamicFeeLoader(activity);
		}

		@Override
		public void onLoadFinished(final Loader<Map<FeeCategory, Coin>> loader, final Map<FeeCategory, Coin> data) {
			fees = data;
			updateView();
		}

		@Override
		public void onLoaderReset(final Loader<Map<FeeCategory, Coin>> loader) {
		}
	};

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);

		this.activity = (AbstractBindServiceActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.loaderManager = getLoaderManager();
		this.fragmentManager = getFragmentManager();
	}

	@Override
	public void onActivityCreated(@android.support.annotation.Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		initFloatingButton();
	}

	private void initFloatingButton()
	{
		viewFabScanQr = (FloatingActionButton) this.activity.findViewById(R.id.fab_scan_qr);
		final PackageManager pm = this.activity.getPackageManager();
		boolean hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
		viewFabScanQr.setVisibility(hasCamera ? View.VISIBLE : View.GONE);
		viewFabScanQr.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				handleScan();
			}
		});
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
		setHasOptionsMenu(true);

		backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());

		if (savedInstanceState != null) {
			restoreInstanceState(savedInstanceState);
		} else {
			final Intent intent = activity.getIntent();

			if (intent.hasExtra(SweepWalletActivity.INTENT_EXTRA_KEY)) {
				privateKeyToSweep = (VersionedChecksummedBytes) intent
						.getSerializableExtra(SweepWalletActivity.INTENT_EXTRA_KEY);

				// delay until fragment is resumed
				handler.post(maybeDecodeKeyRunnable);
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
							 final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.sweep_wallet_fragment, container);

		messageView = (TextView) view.findViewById(R.id.sweep_wallet_fragment_message);

		passwordViewGroup = view.findViewById(R.id.sweep_wallet_fragment_password_group);
		passwordView = (EditText) view.findViewById(R.id.sweep_wallet_fragment_password);
		badPasswordView = view.findViewById(R.id.sweep_wallet_fragment_bad_password);

		balanceView = (TextView) view.findViewById(R.id.sweep_wallet_fragment_balance);

		hintView = view.findViewById(R.id.sweep_wallet_fragment_hint);

		sweepTransactionView = (FrameLayout) view.findViewById(R.id.sweep_wallet_fragment_sent_transaction);
		sweepTransactionAdapter = new TransactionsAdapter(activity, application.getWallet(), false,
				application.maxConnectedPeers(), null);
		sweepTransactionViewHolder = sweepTransactionAdapter.createTransactionViewHolder(sweepTransactionView);
		sweepTransactionView.addView(sweepTransactionViewHolder.itemView,
				new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		viewGo = (Button) view.findViewById(R.id.send_coins_go);
		viewGo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (state == State.DECODE_KEY)
					handleDecrypt();
				if (state == State.CONFIRM_SWEEP)
					handleSweep();
			}
		});

		viewCancel = (Button) view.findViewById(R.id.send_coins_cancel);
		viewCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				activity.finish();
			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		loaderManager.initLoader(ID_DYNAMIC_FEES_LOADER, null, dynamicFeesLoaderCallbacks);

		updateView();
	}

	@Override
	public void onPause() {
		loaderManager.destroyLoader(ID_DYNAMIC_FEES_LOADER);

		super.onPause();
	}

	@Override
	public void onDestroy() {
		backgroundThread.getLooper().quit();

		if (sentTransaction != null)
			sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);

		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);

		saveInstanceState(outState);
	}

	private void saveInstanceState(final Bundle outState) {
		outState.putSerializable("state", state);
		if (walletToSweep != null)
			outState.putByteArray("wallet_to_sweep", WalletUtils.walletToByteArray(walletToSweep));
		if (sentTransaction != null)
			outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());
	}

	private void restoreInstanceState(final Bundle savedInstanceState) {
		state = (State) savedInstanceState.getSerializable("state");
		if (savedInstanceState.containsKey("wallet_to_sweep"))
			walletToSweep = WalletUtils.walletFromByteArray(savedInstanceState.getByteArray("wallet_to_sweep"));
		if (savedInstanceState.containsKey("sent_transaction_hash")) {
			sentTransaction = application.getWallet()
					.getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
			sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
		}
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
		if (requestCode == REQUEST_CODE_SCAN) {
			if (resultCode == Activity.RESULT_OK) {
				final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

				new StringInputParser(input) {
					@Override
					protected void handlePrivateKey(final VersionedChecksummedBytes key) {
						privateKeyToSweep = key;
						setState(State.DECODE_KEY);
						maybeDecodeKey();
					}

					@Override
					protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
						cannotClassify(input);
					}

					@Override
					protected void handleDirectTransaction(final Transaction transaction) throws VerificationException {
						cannotClassify(input);
					}

					@Override
					protected void error(final int messageResId, final Object... messageArgs) {
						dialog(activity, null, R.string.button_scan, messageResId, messageArgs);
					}
				}.parse();
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.sweep_wallet_fragment_options, menu);

		reloadAction = menu.findItem(R.id.sweep_wallet_options_reload);

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.sweep_wallet_options_reload:
				handleReload();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleReload() {
		if (walletToSweep == null)
			return;

		requestWalletBalance();
	}

	private void handleScan() {
		startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener() {
		@Override
		public void onConfidenceChanged(final TransactionConfidence confidence,
										final TransactionConfidence.Listener.ChangeReason reason) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (!isResumed())
						return;

					final TransactionConfidence confidence = sentTransaction.getConfidence();
					final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
					final int numBroadcastPeers = confidence.numBroadcastPeers();

					if (state == State.SENDING) {
						if (confidenceType == TransactionConfidence.ConfidenceType.DEAD)
							setState(State.FAILED);
						else if (numBroadcastPeers > 1
								|| confidenceType == TransactionConfidence.ConfidenceType.BUILDING)
							setState(State.SENT);
					}

					if (reason == ChangeReason.SEEN_PEERS
							&& confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
						// play sound effect
						final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers,
								"raw", activity.getPackageName());
						android.content.Context context = getActivity().getApplicationContext();
						if (soundResId > 0)
							RingtoneManager
									.getRingtone(context, Uri.parse(
											"android.resource://" + context.getPackageName() + "/" + soundResId))
									.play();
					}

					updateView();
				}
			});
		}
	};

	private final Runnable maybeDecodeKeyRunnable = new Runnable() {
		@Override
		public void run() {
			maybeDecodeKey();
		}
	};

	private void maybeDecodeKey() {
		checkState(state == State.DECODE_KEY);
		checkState(privateKeyToSweep != null);

		if (privateKeyToSweep instanceof DumpedPrivateKey) {
			final ECKey key = ((DumpedPrivateKey) privateKeyToSweep).getKey();
			askConfirmSweep(key);
		} else if (privateKeyToSweep instanceof BIP38PrivateKey) {
			badPasswordView.setVisibility(View.INVISIBLE);

			final String password = passwordView.getText().toString().trim();
			passwordView.setText(null); // get rid of it asap

			if (!password.isEmpty()) {
				ProgressDialogFragment.showProgress(fragmentManager,
						getString(R.string.sweep_wallet_fragment_decrypt_progress));

				new DecodePrivateKeyTask(backgroundHandler) {
					@Override
					protected void onSuccess(ECKey decryptedKey) {
						log.info("successfully decoded BIP38 private key");

						ProgressDialogFragment.dismissProgress(fragmentManager);

						askConfirmSweep(decryptedKey);
					}

					@Override
					protected void onBadPassphrase() {
						log.info("failed decoding BIP38 private key (bad password)");

						ProgressDialogFragment.dismissProgress(fragmentManager);

						badPasswordView.setVisibility(View.VISIBLE);
						passwordView.requestFocus();
					}
				}.decodePrivateKey((BIP38PrivateKey) privateKeyToSweep, password);
			}
		} else {
			throw new IllegalStateException("cannot handle type: " + privateKeyToSweep.getClass().getName());
		}
	}

	private void askConfirmSweep(final ECKey key) {
		// create non-HD wallet
		final KeyChainGroup group = new KeyChainGroup(Constants.NETWORK_PARAMETERS);
		group.importKeys(key);
		walletToSweep = new Wallet(Constants.NETWORK_PARAMETERS, group);

		setState(State.CONFIRM_SWEEP);

		// delay until fragment is resumed
		handler.post(requestWalletBalanceRunnable);
	}

	private final Runnable requestWalletBalanceRunnable = new Runnable() {
		@Override
		public void run() {
			requestWalletBalance();
		}
	};

	private static final Comparator<UTXO> UTXO_COMPARATOR = new Comparator<UTXO>() {
		@Override
		public int compare(final UTXO lhs, final UTXO rhs) {
			return ComparisonChain.start().compare(lhs.getHash(), rhs.getHash()).compare(lhs.getIndex(), rhs.getIndex())
					.result();
		}
	};

	private void requestWalletBalance() {
		ProgressDialogFragment.showProgress(fragmentManager,
				getString(R.string.sweep_wallet_fragment_request_wallet_balance_progress));

		final RequestWalletBalanceTask.ResultCallback callback = new RequestWalletBalanceTask.ResultCallback() {
			@Override
			public void onResult(final Set<UTXO> utxos) {
				ProgressDialogFragment.dismissProgress(fragmentManager);

				// Filter UTXOs we've already spent and sort the rest.
				final Set<Transaction> walletTxns = application.getWallet().getTransactions(false);
				final Set<UTXO> sortedUtxos = new TreeSet<>(UTXO_COMPARATOR);
				for (final UTXO utxo : utxos)
					if (!utxoSpentBy(walletTxns, utxo))
						sortedUtxos.add(utxo);

				// Fake transaction funding the wallet to sweep.
				final Map<Sha256Hash, Transaction> fakeTxns = new HashMap<>();
				for (final UTXO utxo : sortedUtxos) {
					Transaction fakeTx = fakeTxns.get(utxo.getHash());
					if (fakeTx == null) {
						fakeTx = new FakeTransaction(Constants.NETWORK_PARAMETERS, utxo.getHash());
						fakeTx.getConfidence().setConfidenceType(ConfidenceType.BUILDING);
						fakeTxns.put(fakeTx.getHash(), fakeTx);
					}
					final TransactionOutput fakeOutput = new TransactionOutput(Constants.NETWORK_PARAMETERS, fakeTx,
							utxo.getValue(), utxo.getScript().getProgram());
					// Fill with output dummies as needed.
					while (fakeTx.getOutputs().size() < utxo.getIndex())
						fakeTx.addOutput(new TransactionOutput(Constants.NETWORK_PARAMETERS, fakeTx,
								Coin.NEGATIVE_SATOSHI, new byte[] {}));
					// Add the actual output we will spend later.
					fakeTx.addOutput(fakeOutput);
				}

				walletToSweep.clearTransactions(0);
				for (final Transaction tx : fakeTxns.values())
					walletToSweep.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, tx));
				log.info("built wallet to sweep:\n{}", walletToSweep.toString(false, true, false, null));

				updateView();
			}

			private boolean utxoSpentBy(final Set<Transaction> transactions, final UTXO utxo) {
				for (final Transaction tx : transactions) {
					for (final TransactionInput input : tx.getInputs()) {
						final TransactionOutPoint outpoint = input.getOutpoint();
						if (outpoint.getHash().equals(utxo.getHash()) && outpoint.getIndex() == utxo.getIndex())
							return true;
					}
				}
				return false;
			}

			@Override
			public void onFail(final int messageResId, final Object... messageArgs) {
				ProgressDialogFragment.dismissProgress(fragmentManager);

				final DialogBuilder dialog = DialogBuilder.warn(activity,
						R.string.sweep_wallet_fragment_request_wallet_balance_failed_title);
				dialog.setMessage(getString(messageResId, messageArgs));
				dialog.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						requestWalletBalance();
					}
				});
				dialog.setNegativeButton(R.string.button_dismiss, null);
				dialog.show();
			}
		};

		final Address address = walletToSweep.getImportedKeys().iterator().next()
				.toAddress(Constants.NETWORK_PARAMETERS);
		new RequestWalletBalanceTask(backgroundHandler, callback).requestWalletBalance(activity.getAssets(), address);
	}

	private void setState(final State state) {
		this.state = state;

		updateView();
	}

	private void updateView() {
		final MonetaryFormat btcFormat = config.getFormat();

		if (walletToSweep != null) {
			balanceView.setVisibility(View.VISIBLE);
			final MonetarySpannable balanceSpannable = new MonetarySpannable(btcFormat,
					walletToSweep.getBalance(BalanceType.ESTIMATED));
			balanceSpannable.applyMarkup(null, null);
			final SpannableStringBuilder balance = new SpannableStringBuilder(balanceSpannable);
			balance.insert(0, ": ");
			balance.insert(0, getString(R.string.sweep_wallet_fragment_balance));
			balanceView.setText(balance);
		} else {
			balanceView.setVisibility(View.GONE);
		}

		if (state == State.DECODE_KEY && privateKeyToSweep == null) {
			messageView.setVisibility(View.VISIBLE);
			messageView.setText(R.string.sweep_wallet_fragment_wallet_unknown);
		} else if (state == State.DECODE_KEY && privateKeyToSweep != null) {
			messageView.setVisibility(View.VISIBLE);
			messageView.setText(R.string.sweep_wallet_fragment_encrypted);
		} else if (privateKeyToSweep != null) {
			messageView.setVisibility(View.GONE);
		}

		passwordViewGroup
				.setVisibility(state == State.DECODE_KEY && privateKeyToSweep != null ? View.VISIBLE : View.GONE);

		hintView.setVisibility(state == State.DECODE_KEY && privateKeyToSweep == null ? View.VISIBLE : View.GONE);

		if (sentTransaction != null) {
			sweepTransactionView.setVisibility(View.VISIBLE);
			sweepTransactionAdapter.setFormat(btcFormat);
			sweepTransactionAdapter.replace(sentTransaction);
			sweepTransactionAdapter.bindViewHolder(sweepTransactionViewHolder, 0);
		} else {
			sweepTransactionView.setVisibility(View.GONE);
		}

		if (state == State.DECODE_KEY) {
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.sweep_wallet_fragment_button_decrypt);
			viewGo.setEnabled(privateKeyToSweep != null);
		} else if (state == State.CONFIRM_SWEEP) {
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.sweep_wallet_fragment_button_sweep);
			viewGo.setEnabled(walletToSweep != null && walletToSweep.getBalance(BalanceType.ESTIMATED).signum() > 0
					&& fees != null);
			log.info("State:  Confirm Sweep:  walletToSweep: " + (walletToSweep==null ? "valid" : "invalid") +
					"wallet balance > 0: " + (walletToSweep.getBalance(BalanceType.ESTIMATED).signum() > 0 ? "true" : "false") +
					"fees: " + (fees == null ? "null" : fees.toString()));
		} else if (state == State.PREPARATION) {
			viewCancel.setText(R.string.button_cancel);
			viewGo.setText(R.string.send_coins_preparation_msg);
			viewGo.setEnabled(false);
		} else if (state == State.SENDING) {
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_sending_msg);
			viewGo.setEnabled(false);
		} else if (state == State.SENT) {
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_sent_msg);
			viewGo.setEnabled(false);
		} else if (state == State.FAILED) {
			viewCancel.setText(R.string.send_coins_fragment_button_back);
			viewGo.setText(R.string.send_coins_failed_msg);
			viewGo.setEnabled(false);
		}

		viewCancel.setEnabled(state != State.PREPARATION);

		// enable actions
		if (reloadAction != null)
			reloadAction.setEnabled(state == State.CONFIRM_SWEEP && walletToSweep != null);
		if (scanAction != null)
			scanAction.setEnabled(state == State.DECODE_KEY || state == State.CONFIRM_SWEEP);
	}

	private void handleDecrypt() {
		handler.post(maybeDecodeKeyRunnable);
	}

	private void handleSweep() {
		setState(State.PREPARATION);

		final SendRequest sendRequest = SendRequest.emptyWallet(application.getWallet().freshReceiveAddress());
		sendRequest.feePerKb = fees.get(FeeCategory.NORMAL);

		new SendCoinsOfflineTask(walletToSweep, backgroundHandler) {
			@Override
			protected void onSuccess(final Transaction transaction) {
				sentTransaction = transaction;

				setState(State.SENDING);

				sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

				application.processDirectTransaction(sentTransaction);
			}

			@Override
			protected void onInsufficientMoney(@Nullable final InsufficientMoneyException e) {
				setState(State.FAILED);

				showInsufficientMoneyDialog();
			}

			@Override
			protected void onEmptyWalletFailed() {
				setState(State.FAILED);

				showInsufficientMoneyDialog();
			}

			@Override
			protected void onFailure(final Exception exception) {
				setState(State.FAILED);

				final DialogBuilder dialog = DialogBuilder.warn(activity, R.string.send_coins_error_msg);
				dialog.setMessage(exception.toString());
				dialog.setPositiveButton(R.string.button_dismiss, null);
				dialog.show();
			}

			@Override
			protected void onInvalidEncryptionKey() {
				throw new RuntimeException(); // cannot happen
			}

			private void showInsufficientMoneyDialog() {
				final DialogBuilder dialog = DialogBuilder.warn(activity,
						R.string.sweep_wallet_fragment_insufficient_money_title);
				dialog.setMessage(R.string.sweep_wallet_fragment_insufficient_money_msg);
				dialog.setPositiveButton(R.string.button_dismiss, null);
				dialog.show();
			}
		}.sendCoinsOffline(sendRequest); // send asynchronously
	}

	private static class FakeTransaction extends Transaction {
		private final Sha256Hash hash;

		public FakeTransaction(final NetworkParameters params, final Sha256Hash hash) {
			super(params);
			this.hash = hash;
		}

		@Override
		public Sha256Hash getHash() {
			return hash;
		}
	}
}
