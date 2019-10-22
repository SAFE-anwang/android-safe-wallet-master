package de.schildbach.wallet.ui.safe;

import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.ui.*;
import de.schildbach.wallet.ui.safe.bean.EventMessage;
import de.schildbach.wallet.ui.safe.recyclerview.LoadMoreRecyclerView;
import de.schildbach.wallet.ui.safe.recyclerview.RecyclerAdapterWrapper;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.ViewHolder;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.CommonAdapter;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.RecyclerViewDivider;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.WholeStringBuilder;
import de.schildbach.wallet.R;

import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 资产列表
 * @author zhangmiao
 */
public class HomeListFragment extends BaseFragment {

    private View headerView;
    private WalletAddressFragment fragment;
    private LoadMoreRecyclerView recyclerView;
    private RecyclerAdapterWrapper adapterWrapper;
    private CommonAdapter<IssueData> adapter;
    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;
    private static final int ID_BALANCE_LOADER = 0;
    private LoaderManager loaderManager;

    public static final Logger log = LoggerFactory.getLogger(HomeListFragment.class);


    @Override
    public int getLayoutResId() {
        return R.layout.fragment_home_list;
    }

    @Override
    public void initView() {
        super.initView();
        recyclerView = (LoadMoreRecyclerView) findViewById(R.id.recyclerView);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);

        this.application = (WalletApplication) getActivity().getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.loaderManager = getLoaderManager();

        adapter = new CommonAdapter<IssueData>(R.layout.listrow_home) {
            @Override
            protected void convert(ViewHolder viewHolder, IssueData item, int position) {
                ImageView icon = viewHolder.findViewById(R.id.icon);
                TextView name = viewHolder.findViewById(R.id.name);
                CurrencyTextView total = viewHolder.findViewById(R.id.total);
                icon.setImageResource(R.drawable.safe_logo);
                name.setText(item.assetName.equals(SafeConstant.SAFE_FLAG) ? item.assetName.toUpperCase() : item.assetName);
                if (!item.assetId.equals(SafeConstant.SAFE_FLAG)) {
                    total.setText(SafeUtils.getAssetAmount(item.balance, item.decimals));
                } else {
                    total.setFormat(config.getFormat().noCode());
                    total.setAmount(item.balance);
                }
            }

            @Override
            protected void onItemClick(View view, IssueData item, int position) {
                if (CommonUtils.isRepeatClick()) {
                    Intent intent = new Intent(getActivity(), BaseWalletActivity.class);
                    intent.putExtra(BaseWalletActivity.CLASS, AssetFragment.class);
                    intent.putExtra("assetId", item.assetId);
                    startActivity(intent);
                }
            }
        };

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new RecyclerViewDivider(getActivity(), LinearLayoutManager.HORIZONTAL));

        adapterWrapper = new RecyclerAdapterWrapper();
        adapterWrapper.setAdapter(adapter);
        headerView = LayoutInflater.from(getActivity()).inflate(R.layout.listrow_fgt_address, recyclerView, false);
        fragment = (WalletAddressFragment) getActivity().getFragmentManager().findFragmentById(R.id.wallet_address_fragment);
        fragment.setLoading();
        adapterWrapper.addHeaderView(headerView);

        recyclerView.setAdapter(adapterWrapper);

        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_BALANCE_LOADER);
        super.onPause();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refreshAssetList(EventMessage msg) {
        if (msg.eventType == EventMessage.TYPE_WALLET_RESET) {
            loaderManager.restartLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
        }
    }

    private final LoaderManager.LoaderCallbacks<List<IssueData>> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<List<IssueData>>() {

        @Override
        public Loader<List<IssueData>> onCreateLoader(int id, Bundle args) {
            return new AllAssetBalanceLoader(application, wallet);
        }

        @Override
        public void onLoadFinished(Loader<List<IssueData>> loader, List<IssueData> data) {
            AllAssetBalanceLoader.INIT_LOADER_FINISH = true;
            if (data == null || data.size() == 0) {
                fragment.setEmptyView(WholeStringBuilder.bold(getString(R.string.safe_home_empty_text)));
            } else if (adapter.getItemCount() == 0) {
                fragment.setLoadFinish();
            }
            adapter.replaceAll(data);
        }

        @Override
        public void onLoaderReset(Loader<List<IssueData>> loader) {

        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

}
