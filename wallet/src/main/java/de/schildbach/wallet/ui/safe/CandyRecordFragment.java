package de.schildbach.wallet.ui.safe;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.j256.ormlite.dao.GenericRawResults;

import de.schildbach.wallet.data.AddressBookProvider;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.GetCandyData;
import de.schildbach.wallet.ui.ReceiveDetailActivity;
import de.schildbach.wallet.ui.safe.bean.AddressBook;
import de.schildbach.wallet.ui.safe.recyclerview.LoadMoreRecyclerView;
import de.schildbach.wallet.ui.safe.recyclerview.LoadingFooter;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.CommonAdapter;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.ViewHolder;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.RecyclerViewDivider;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.R;

import org.bitcoinj.core.PublicKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 糖果记录页面
 * @author zhangmiao
 */
public class CandyRecordFragment extends BaseFragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private LoadMoreRecyclerView recyclerView;
    private CommonAdapter<GetCandyData> adapter;

    private int pageIndex = 0;
    private long pageCount = 0;

    private final int pageSize = 50;
    private TextView emptyTv;
    private ImageView topImg;
    private RelativeLayout relativeLayout;
    private String txId;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_candy_record;
    }

    @Override
    public void initView() {
        super.initView();
        topImg = (ImageView) findViewById(R.id.top_img);
        relativeLayout = (RelativeLayout) findViewById(R.id.data_view);
        emptyTv = (TextView) findViewById(R.id.empty_tv);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeLayout);
        //改变加载显示的颜色
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#367bbe"));
        //设置向下拉多少出现刷新
        swipeRefreshLayout.setDistanceToTriggerSync(100);
        //设置刷新出现的位置
        swipeRefreshLayout.setProgressViewEndTarget(false, 200);
        recyclerView = (LoadMoreRecyclerView) findViewById(R.id.recycler_view);
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new RecyclerViewDivider(getActivity(), LinearLayoutManager.HORIZONTAL));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            //返回当前recyclerview的可见的item数目，也就是datas.length
            //dx是水平滚动的距离，dy是垂直滚动距离，向上滚动的时候为正，向下滚动的时候为负
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();//可见范围内的第一项的位置
                if (firstVisibleItemPosition == 0) {
                    topImg.setVisibility(View.GONE);
                } else {
                    topImg.setVisibility(View.VISIBLE);
                }
            }
        });
        topImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recyclerView.smoothScrollToPosition(0);
            }
        });

        //下拉刷新
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                pageIndex = 0;
                getData();
            }
        });

        //加载更多
        recyclerView.setOnLoadMoreListener(new LoadMoreRecyclerView.OnLoadMoreListener() {
            @Override
            public void onLoadMore() {
                pageIndex++;
                getData();
            }
        });

    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.safe_candy_record));
        Bundle args = getArguments();

        txId = args.getString("txId");

        adapter = new CommonAdapter<GetCandyData>(R.layout.listrow_candy_record) {

            @Override
            protected void convert(ViewHolder viewHolder, GetCandyData item, int position) {
                TextView address = viewHolder.findViewById(R.id.tv_address);
                TextView assetName = viewHolder.findViewById(R.id.tv_asset_name);
                TextView candyAmount = viewHolder.findViewById(R.id.ctv_candy_amount);
                String addressLabel = item.address != null
                        ? AddressBookProvider.resolveLabel(getActivity(), item.address) : null;
                address.setText(addressLabel == null ? item.address : addressLabel);

                assetName.setText(item.assetName);
                candyAmount.setText(SafeUtils.getAssetAmount(item.candyAmount, item.decimals));
            }

            @Override
            protected void onItemClick(View view, GetCandyData item, int position) {
                if (CommonUtils.isRepeatClick()) {
                    Intent intent = new Intent(getActivity(), BaseWalletActivity.class);
                    intent.putExtra(BaseWalletActivity.CLASS, TransactionDetailsFragment.class);
                    intent.putExtra("txId", item.txId);
                    intent.putExtra("assetId", item.assetId);
                    intent.putExtra("candy", item);
                    startActivity(intent);
                }
            }
        };
        recyclerView.setAdapter(adapter);
        swipeRefreshLayout.setRefreshing(true);
        getData();
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    public void getData() {
        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {
                final List<GetCandyData> candyList = new ArrayList<>();

                try {

                    BaseDaoImpl getCandyDao = new BaseDaoImpl(GetCandyData.class);

                    String sql;
                    if (null != txId) {
                        sql = String.format("select distinct txId, address from tb_get_candy where txId = '%s'", txId);
                    } else {
                        sql = "select distinct txId, address from tb_get_candy";
                    }
                    GenericRawResults<String[]> rawResults = getCandyDao.getDao().queryRaw(sql);
                    pageCount = rawResults.getResults().size();

                    if (null != txId) {
                        sql = String.format("select distinct a.txId, a.inTxId, a.version, a.assetId, a.candyAmount, a.remarks, a.address, b.assetName, b.decimals, b.assetUnit from tb_get_candy as a inner join tb_issue as b on a.assetId = b.assetId where a.txId = '%s' order by a.id desc limit %d, %d", txId, pageIndex * pageSize, pageSize);
                    } else {
                        sql = String.format("select distinct a.txId, a.inTxId, a.version, a.assetId, a.candyAmount, a.remarks, a.address, b.assetName, b.decimals, b.assetUnit from tb_get_candy as a inner join tb_issue as b on a.assetId = b.assetId order by a.id desc limit %d, %d", pageIndex * pageSize, pageSize);
                    }

                    rawResults = getCandyDao.getDao().queryRaw(sql);
                    List<String[]> putCandyArr = rawResults.getResults();

                    for (String[] item : putCandyArr) {
                        candyList.add(arrToGetCandy(item));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    BackgroundThread.postUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshUI(candyList);
                        }
                    });
                }
            }
        }));
    }

    public void refreshUI(List<GetCandyData> candyList) {
        int offset = pageIndex + 1;
        if (pageIndex == 0) {
            adapter.replaceAll(candyList);
        } else {
            adapter.addAll(candyList);
        }
        if (offset * pageSize >= pageCount) {
            recyclerView.setState(LoadingFooter.State.TheEnd, false); //已经到底了
        } else {
            recyclerView.setLoadingComplete(); //加载完成
        }
        swipeRefreshLayout.setRefreshing(false); //刷新完成
        if (adapter.getData() == null || adapter.getData().size() == 0) {
            emptyTv.setVisibility(View.VISIBLE);
            relativeLayout.setVisibility(View.GONE);
        } else {
            emptyTv.setVisibility(View.GONE);
            relativeLayout.setVisibility(View.VISIBLE);
        }
    }

    public GetCandyData arrToGetCandy(String[] arr) {
        GetCandyData item = new GetCandyData();
        item.txId = arr[0];
        item.inTxId = arr[1];
        item.version = Long.parseLong(arr[2]);
        item.assetId = arr[3];
        item.candyAmount = Long.parseLong(arr[4]);
        item.remarks = arr[5];
        item.address = arr[6];
        item.assetName = arr[7];
        item.decimals = Long.parseLong(arr[8]);
        item.assetUnit = arr[9];
        return item;
    }


    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void addressBookEdit(final AddressBook book) {
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        BackgroundThread.prepareThread();
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        BackgroundThread.destroyThread();
        super.onDestroy();
    }

}
