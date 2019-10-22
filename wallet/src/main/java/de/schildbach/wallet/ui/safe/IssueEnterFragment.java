package de.schildbach.wallet.ui.safe;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.bitcoinj.utils.SafeConstant;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.preference.SettingsActivity;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.R;

/**
 * 发行资产入口
 *
 * @author zhangmiao
 */
public class IssueEnterFragment extends BaseFragment implements View.OnClickListener {

    private RelativeLayout rlAssetIssue;
    private RelativeLayout rlAddAsset;
    private RelativeLayout rlCandyGrant;
    private RelativeLayout rlCandyReceive;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_asset_enter;
    }

    @Override
    public void initView() {
        super.initView();
        getRootView().setBackgroundResource(R.color.white);
        rlAssetIssue = (RelativeLayout) findViewById(R.id.rl_asset_issue);
        rlAddAsset = (RelativeLayout) findViewById(R.id.rl_add_asset);
        rlCandyGrant = (RelativeLayout) findViewById(R.id.rl_candy_grant);
        rlCandyReceive = (RelativeLayout) findViewById(R.id.rl_candy_receive);
        rlAssetIssue.setOnClickListener(this);
        rlAddAsset.setOnClickListener(this);
        rlCandyGrant.setOnClickListener(this);
        rlCandyReceive.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (CommonUtils.isRepeatClick()) {
            if (SafeConstant.getLastBlockHeight() >= SafeConstant.getDashDisabledHeight()) {
                Intent intent = new Intent(getActivity(), BaseWalletActivity.class);
                switch (v.getId()) {
                    case R.id.rl_asset_issue:
                        intent.putExtra(BaseWalletActivity.CLASS, IssueAssetFragment.class);
                        startActivity(intent);
                        break;
                    case R.id.rl_add_asset:
                        intent.putExtra(BaseWalletActivity.CLASS, IssueAssetAddFragment.class);
                        startActivity(intent);
                        break;
                    case R.id.rl_candy_grant:
                        intent.putExtra(BaseWalletActivity.CLASS, CandyPutFragment.class);
                        startActivity(intent);
                        break;
                    case R.id.rl_candy_receive:
                        intent.putExtra(BaseWalletActivity.CLASS, CandyGetFragment.class);
                        startActivity(intent);
                        break;
                }
            } else {
                new de.schildbach.wallet.util.Toast(getActivity()).toast(R.string.func_open, SafeConstant.getDashDisabledHeight());
            }
        }
    }

}
