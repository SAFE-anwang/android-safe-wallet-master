package de.schildbach.wallet.ui.safe;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.service.BlockchainState;
import de.schildbach.wallet.ui.*;
import de.schildbach.wallet.ui.safe.utils.AssetCoin;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.R;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.Wallet;

import java.sql.SQLException;
import java.util.Map;

/**
 * 资产页面
 * @author zhangmiao
 */
public class AssetFragment extends BaseFragment implements View.OnClickListener {

    private TextView sendCoins;
    private TextView requestCoins;

    private String assetId;
    private IssueData issue;

    private AbstractBindServiceActivity activity;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_asset;
    }

    @Override
    public void initView() {
        super.initView();

        sendCoins = (TextView) findViewById(R.id.send_coins);
        requestCoins = (TextView) findViewById(R.id.request_coins);


        sendCoins.setOnClickListener(this);
        requestCoins.setOnClickListener(this);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);


        Bundle args = getArguments();

        assetId = args.getString("assetId");

        if (isSafe()) {
            String assetTitle = SafeConstant.SAFE_FLAG.toUpperCase();
            getActivity().setTitle(assetTitle);
        } else {
            BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
            try {
                issue = (IssueData) dao.queryForFirst("assetId", assetId);
            } catch (SQLException e) {
                getActivity().finish();
            }
            getActivity().setTitle(issue.assetName);
        }

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        TransactionFragment fragment = new TransactionFragment();
        fragment.setArguments(getArguments());
        ft.add(R.id.fl_lately_tx, fragment);
        ft.commit();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (AbstractBindServiceActivity) activity;
    }

    @Override
    public void onClick(View v) {
        if(CommonUtils.isRepeatClick()){
            switch (v.getId()) {
                case R.id.send_coins:
                    BlockchainState blockchainState = activity.getBlockchainService().getBlockchainState();
                    if (blockchainState != null && blockchainState.bestChainDate != null) {
                        long blockchainLag = System.currentTimeMillis() - blockchainState.bestChainDate.getTime();
                        if (blockchainLag >= DateUtils.HOUR_IN_MILLIS) {
                            new Toast(activity).shortToast(R.string.send_coins_fragment_hint_replaying);
                            return;
                        }
                    }
                    Intent intent = new Intent(getActivity(), SendCoinsActivity.class);
                    intent.putExtras(getArguments());
                    startActivity(intent);
                    break;
                case R.id.request_coins:
                    intent = new Intent(getActivity(), RequestCoinsActivity.class);
                    intent.putExtras(getArguments());
                    startActivity(intent);
                    break;
            }
        }
    }

    //是否SAFE
    public boolean isSafe() {
        if (TextUtils.isEmpty(assetId) || assetId.equalsIgnoreCase(SafeConstant.SAFE_FLAG)) {
            assetId = SafeConstant.SAFE_FLAG;
            return true;
        } else {
            return false;
        }
    }

}
