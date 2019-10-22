package de.schildbach.wallet.ui.safe;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.*;
import de.schildbach.wallet.ui.preference.AboutFragment;
import de.schildbach.wallet.ui.preference.SettingsActivity;
import de.schildbach.wallet.ui.preference.SettingsEnterFragment;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.R;

import java.io.*;

/**
 * 我的
 * @author zhangmiao
 */
public class MineFragment extends BaseFragment implements View.OnClickListener{

    private View mineCandy;
    private View mineMasternode;
    private View mineAddress;
    private View mineNetwork;
    private View mineSecurity;
    private View mineSetting;
    private View mineAbout;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_mine;
    }

    @Override
    public void initView() {
        super.initView();
        mineCandy = findViewById(R.id.mine_candy);
        mineMasternode = findViewById(R.id.mine_masternode);
        mineAddress = findViewById(R.id.mine_address);
        mineNetwork = findViewById(R.id.mine_network);
        mineSecurity = findViewById(R.id.mine_security);
        mineSetting = findViewById(R.id.mine_setting);
        mineAbout = findViewById(R.id.mine_about);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        mineCandy.setOnClickListener(this);
        mineMasternode.setOnClickListener(this);
        mineAddress.setOnClickListener(this);
        mineNetwork.setOnClickListener(this);
        mineSecurity.setOnClickListener(this);
        mineSetting.setOnClickListener(this);
        mineAbout.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.mine_candy:
                Intent intent = new Intent(getActivity(), BaseWalletActivity.class);
                intent.putExtra(BaseWalletActivity.CLASS, CandyRecordFragment.class);
                startActivity(intent);
                break;
            case R.id.mine_masternode:
                intent = new Intent(getActivity(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.CLASS, MasternodeFragment.class);
                startActivity(intent);
                break;
            case R.id.mine_address:
                AddressBookActivity.start(getActivity());
                break;
            case R.id.mine_network:
                startActivity(new Intent(getActivity(), NetworkMonitorActivity.class));
                break;
            case R.id.mine_security:
                ((MainActivity)getActivity()).showContextMenu();
                break;
            case R.id.mine_setting:
                intent = new Intent(getActivity(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.CLASS, SettingsEnterFragment.class);
                startActivity(intent);
                break;
            case R.id.mine_about:
                intent = new Intent(getActivity(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.CLASS, AboutFragment.class);
                startActivity(intent);
                break;
        }
    }

}
