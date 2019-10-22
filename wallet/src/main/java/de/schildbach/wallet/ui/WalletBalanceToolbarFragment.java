package de.schildbach.wallet.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.service.BlockchainStateReceiver;
import de.schildbach.wallet.ui.safe.BaseFragment;
import de.schildbach.wallet.ui.safe.MainActivity;
import de.schildbach.wallet.R;

/**
 * @author zhangmiao
 */
public final class WalletBalanceToolbarFragment extends BaseFragment
{

	private View progressView;

    private TextView appBarMessageView;

    private String progressMessage;

	public static BlockchainState blockchainState = null;

	public BlockchainStateReceiver blockchainStateReceiver;

	public AbstractBindServiceActivity activity;

	public Configuration config;


	private static final long BLOCKCHAIN_UPTODATE_THRESHOLD_MS = DateUtils.HOUR_IN_MILLIS;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
		this.activity = (AbstractBindServiceActivity) getActivity();
		WalletApplication application = (WalletApplication) this.activity.getApplication();
		config = application.getConfiguration();
		blockchainStateReceiver = new BlockchainStateReceiver(getActivity(), new BlockchainStateReceiver.BlockchainStateListener() {
			@Override
			public void onCallBack(BlockchainState state) {
				WalletBalanceToolbarFragment.blockchainState = state;
				updateView();
			}
		});
    }

	@Override
	public int getLayoutResId() {
		return R.layout.wallet_balance_toolbar_fragment;
	}

    @Override
    public void onActivityCreated(@android.support.annotation.Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        this.appBarMessageView = (TextView) getActivity().findViewById(R.id.toolbar_message);
    }

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		progressView = view.findViewById(R.id.progress);
		setBackgroundResource(android.R.color.transparent);

	}

    @Override
	public void onResume()
	{
		super.onResume();
		blockchainStateReceiver.registerReceiver();
		if (activity.getBlockchainService() != null) {
			WalletBalanceToolbarFragment.blockchainState = activity.getBlockchainService().getBlockchainState();
		}
		updateView();
	}

	@Override
	public void onPause()
	{
		blockchainStateReceiver.unregisterReceiver();
		super.onPause();
	}

	private void updateView()
	{
		if (!isAdded())
			return;

		boolean showProgress;

		if (blockchainState != null && blockchainState.bestChainDate != null)
		{
			final long blockchainLag = System.currentTimeMillis() - blockchainState.bestChainDate.getTime();
			final boolean blockSync = blockchainLag >= BLOCKCHAIN_UPTODATE_THRESHOLD_MS;
			final boolean noImpediments = blockchainState.impediments.isEmpty();
			showProgress = blockSync;

			final String downloading = getString(noImpediments ? R.string.blockchain_state_progress_downloading
					: R.string.blockchain_state_progress_stalled);

			if (blockchainLag < 2 * DateUtils.DAY_IN_MILLIS)
			{
				final long hours = blockchainLag / DateUtils.HOUR_IN_MILLIS;
                progressMessage = getString(R.string.blockchain_state_progress_hours, downloading, hours);
			}
			else if (blockchainLag < 2 * DateUtils.WEEK_IN_MILLIS)
			{
				final long days = blockchainLag / DateUtils.DAY_IN_MILLIS;
                progressMessage = getString(R.string.blockchain_state_progress_days, downloading, days);
			}
			else if (blockchainLag < 90 * DateUtils.DAY_IN_MILLIS)
			{
				final long weeks = blockchainLag / DateUtils.WEEK_IN_MILLIS;
                progressMessage = getString(R.string.blockchain_state_progress_weeks, downloading, weeks);
			}
			else
			{
				final long months = blockchainLag / (30 * DateUtils.DAY_IN_MILLIS);
                progressMessage = getString(R.string.blockchain_state_progress_months, downloading, months);
			}
		}
		else
		{
			showProgress = false;
		}

		if (!showProgress){
			progressView.setVisibility(View.GONE);
			showAppBarMessage(null);
		}
		else
		{
            showAppBarMessage(progressMessage);
            progressView.setVisibility(View.VISIBLE);
            progressView.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                	new de.schildbach.wallet.util.Toast(getActivity()).shortToast(progressMessage);
                }
            });
        }

	}

    private void showAppBarMessage(CharSequence message) {
        if (message != null) {
            appBarMessageView.setVisibility(View.VISIBLE);
            appBarMessageView.setText(message);
        } else {
            appBarMessageView.setVisibility(View.GONE);
        }
    }

	@Override
	protected void serviceBinded() {
		super.serviceBinded();
		WalletBalanceToolbarFragment.blockchainState = activity.getBlockchainService().getBlockchainState();
		updateView();
	}
}
