package de.schildbach.wallet.ui.safe;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.protobuf.ByteString;
import com.j256.ormlite.dao.GenericRawResults;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.db.*;
import de.schildbach.wallet.ui.safe.recyclerview.LoadingFooter;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.CommonAdapter;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.ViewHolder;
import de.schildbach.wallet.ui.safe.recyclerview.LoadMoreRecyclerView;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.RecyclerViewDivider;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.ui.send.FeeCategory;
import de.schildbach.wallet.R;

import org.bitcoin.safe.SafeProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.SendRequest;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 领取糖果页面
 *
 * @author zhangmiao
 */
public class CandyGetFragment extends SendBaseFragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private LoadMoreRecyclerView recyclerView;
    private CommonAdapter<PutCandyData> adapter;

    private int pageIndex = 0;
    private long pageCount = 0;

    private final int pageSize = 50;
    private final int maxOutputCount = 200;

    private PutCandyData curGetItem; //当前领取糖果

    private ImageView topImg;
    private TextView emptyTv;

    private RelativeLayout relativeLayout;

    private String orderBy = "asc";

    private int txPageCount;


    @Override
    public int getLayoutResId() {
        return R.layout.fragment_candy_get;
    }

    @Override
    public void initView() {
        super.initView();
        topImg = (ImageView) findViewById(R.id.top_img);
        emptyTv = (TextView) findViewById(R.id.empty_tv);
        relativeLayout = (RelativeLayout) findViewById(R.id.data_view);
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

        //下拉刷新
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                pageIndex = 0;
                getData(false);
                recyclerView.setLoadingComplete();
            }
        });

        //加载更多
        recyclerView.setOnLoadMoreListener(new LoadMoreRecyclerView.OnLoadMoreListener() {
            @Override
            public void onLoadMore() {
                pageIndex++;
                getData(false);
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.safe_candy_receive));
        adapter = new CommonAdapter<PutCandyData>(R.layout.listrow_candy_get) {
            @Override
            protected void convert(ViewHolder viewHolder, PutCandyData item, final int position) {
                TextView expired = viewHolder.findViewById(R.id.tv_expired_date);
                TextView assetName = viewHolder.findViewById(R.id.tv_asset_name);
                TextView candyAmount = viewHolder.findViewById(R.id.ctv_candy_amount);
                TextView getCandy = viewHolder.findViewById(R.id.tv_get);
                expired.setText(item.getTxTime());
                assetName.setText(item.assetName);
                candyAmount.setText(String.format(getString(R.string.total_candy), SafeUtils.getAssetAmount(item.candyAmount, item.decimals)));
                getCandy.setText(R.string.safe_receive_label);
                getCandy.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (CommonUtils.isRepeatClick()) {
                            getCandy(position);
                        }
                    }
                });
            }

            @Override
            protected void onItemClick(View view, PutCandyData item, int position) {

            }
        };
        recyclerView.setAdapter(adapter);
        getData(true);
    }

    public Transaction getTransaction(String txId) {
        return wallet.getTransaction(Sha256Hash.wrap(txId));
    }

    public void getCandy(final int position) {

        showProgressDialog();

        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {

                if (getBlockSync()) {
                    dismissProgressDialog();
                    showBlockSyncDilog();
                    return;
                }

                curGetItem = adapter.getItem(position);

                if (curGetItem == null) {
                    return;
                }

                int lastBlockHeight = SafeConstant.getLastBlockHeight();

                if (curGetItem.isExpired(lastBlockHeight)) {
                    dismissProgressDialog();
                    shortToast(getString(R.string.safe_candy_already_expired));
                    //标记成已过期
                    SafeUtils.updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_EXPIRED), curGetItem.txId});
                    removeCurrentCandyItem();
                    return;
                }

                try {
                    BaseDaoImpl getCandyDao = new BaseDaoImpl(GetCandyData.class);
                    List<GetCandyData> getCandyList = getCandyDao.query("inTxId", curGetItem.txId);
                    if (getCandyList != null && getCandyList.size() > 0) {
                        dismissProgressDialog();
                        shortToast(getString(R.string.candy_received));
                        removeCurrentCandyItem();
                        //标记成已领取
                        SafeUtils.updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_RECEIVED), curGetItem.txId});
                        return;
                    }
                } catch (SQLException e) {
                    dismissProgressDialog();
                    return;
                }

                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                try {
                    Transaction tx = wallet.getTransaction(Sha256Hash.wrap(curGetItem.txId));
                    if (tx != null) {
                        TransactionConfidence confidence = tx.getConfidence();
                        TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
                        if (confidenceType != TransactionConfidence.ConfidenceType.BUILDING) {
                            dismissProgressDialog();
                            shortToast(getString(R.string.safe_tx_unconfirmed));
                            return;
                        }
                    }

                    int nTxHeight = (int) curGetItem.height;
                    int startSposHeight = Constants.NETWORK_PARAMETERS.getStartSposHeight();

                    if (nTxHeight < startSposHeight) {
                        if (nTxHeight + SafeConstant.BLOCKS_PER_DAY >= startSposHeight) { //采用POW和SPOS
                            int sposLaveHeight = (nTxHeight + SafeConstant.BLOCKS_PER_DAY - startSposHeight) * CoinDefinition.TARGET_SPACING / CoinDefinition.SPOS_TARGET_SPACING;
                            int neededHeight = startSposHeight + sposLaveHeight;
                            if (lastBlockHeight < neededHeight) {
                                dismissProgressDialog();
                                int seconds = (neededHeight - lastBlockHeight) * CoinDefinition.SPOS_TARGET_SPACING;
                                String hour = String.valueOf(seconds / 3600);
                                String min = String.valueOf(seconds % 3600 / 60);
                                if (!hour.equals("0") && !min.equals("0")) {
                                    shortToast(getString(R.string.safe_get_candy_tips, hour, min));
                                } else if (hour.equals("0")) {
                                    if (min.equals("0")) {
                                        min = "1";
                                    }
                                    shortToast(getString(R.string.safe_get_candy_tips2, min));
                                } else if (min.equals("0")) {
                                    shortToast(getString(R.string.safe_get_candy_tips1, hour));
                                }
                                return;
                            }
                        } else { //采用POW
                            int neededHeight = (int) curGetItem.height + SafeConstant.BLOCKS_PER_DAY;
                            if (lastBlockHeight < neededHeight) {
                                dismissProgressDialog();
                                int seconds = (neededHeight - lastBlockHeight) * CoinDefinition.TARGET_SPACING;
                                String hour = String.valueOf(seconds / 3600);
                                String min = String.valueOf(seconds % 3600 / 60);
                                if (!hour.equals("0") && !min.equals("0")) {
                                    shortToast(getString(R.string.safe_get_candy_tips, hour, min));
                                } else if (hour.equals("0")) {
                                    if (min.equals("0")) {
                                        min = "1";
                                    }
                                    shortToast(getString(R.string.safe_get_candy_tips2, min));
                                } else if (min.equals("0")) {
                                    shortToast(getString(R.string.safe_get_candy_tips1, hour));
                                }
                                return;
                            }
                        }
                    } else { //采用SPOS
                        int neededHeight = (int) curGetItem.height + SafeConstant.SPOS_BLOCKS_PER_DAY;
                        if (lastBlockHeight < neededHeight) {
                            dismissProgressDialog();
                            int seconds = (neededHeight - lastBlockHeight) * CoinDefinition.SPOS_TARGET_SPACING;
                            String hour = String.valueOf(seconds / 3600);
                            String min = String.valueOf(seconds % 3600 / 60);
                            if (!hour.equals("0") && !min.equals("0")) {
                                shortToast(getString(R.string.safe_get_candy_tips, hour, min));
                            } else if (hour.equals("0")) {
                                if (min.equals("0")) {
                                    min = "1";
                                }
                                shortToast(getString(R.string.safe_get_candy_tips2, min));
                            } else if (min.equals("0")) {
                                shortToast(getString(R.string.safe_get_candy_tips1, hour));
                            }
                            return;
                        }
                    }

                    BaseDaoImpl candyAddrDao = new BaseDaoImpl(CandyAddrData.class);
                    List<CandyAddrData> sendCandyAddrList = candyAddrDao.query(new String[]{"txId", "height"}, new Object[]{curGetItem.txId, curGetItem.height});

                    long candyAmount = curGetItem.candyAmount;
                    long totalAmount = SafeUtils.getTotalAmount(curGetItem.height);
                    long filterAmount = SafeUtils.getFilterAmount(curGetItem.height);

                    if (beenFinish(sendCandyAddrList, candyAmount, totalAmount, filterAmount)) { //是否已领完
                        return;
                    }

                    int totalSize = sendCandyAddrList.size();
//                    log.info("-----totalSize = {}, {}, {}", totalSize, totalSize / maxOutputCount, totalSize % maxOutputCount);
                    int count = totalSize / maxOutputCount;
                    txPageCount = totalSize % maxOutputCount > 0 ? count + 1 : count;
                    if (txPageCount > 1) {
                        for (int i = 0; i < txPageCount; i++) {
                            int fromIndex = i * maxOutputCount;
                            int toIndex = (i + 1) * maxOutputCount;
                            if (toIndex > totalSize) {
                                toIndex = totalSize;
                            }
//                            log.info("---------fromIndex = {}, toIndex = {}", fromIndex, toIndex);
                            mulTxSend(sendCandyAddrList.subList(fromIndex, toIndex), candyAmount, totalAmount, filterAmount);
                        }
                    } else {
                        mulTxSend(sendCandyAddrList, candyAmount, totalAmount, filterAmount);
                    }
                } catch (Exception e) {
                    dismissProgressDialog();
                }
            }
        }));
    }

    public boolean beenFinish(List<CandyAddrData> addrList, long candyAmount, long totalAmount, long filterAmount) throws Exception{

        long getCandyTotalAmount = 0;
        for (int i = 0; i < addrList.size(); i++) {
            CandyAddrData item = addrList.get(i);
            long value = item.value;
            long enableTotalAmount = totalAmount - filterAmount;
            long amount = (long) (1.0 * value / enableTotalAmount * candyAmount);
            getCandyTotalAmount += amount;
        }
        long receivedAmount = SafeUtils.getCandyAmount(curGetItem.txId);
        if(getCandyTotalAmount + receivedAmount > candyAmount){
            dismissProgressDialog();
            shortToast(getString(R.string.safe_candy_been_finished));
            //标记糖果已领完
            SafeUtils.updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_BEEN_FINISHED), curGetItem.txId});
            removeCurrentCandyItem();
            return true;
        }
        return false;
    }

    public void mulTxSend(List<CandyAddrData> addrList, long candyAmount, long totalAmount, long filterAmount) throws Exception {
        PaymentIntent.Output[] outputs = new PaymentIntent.Output[addrList.size()];

        for (int i = 0; i < addrList.size(); i++) {
            CandyAddrData item = addrList.get(i);
            long value = item.value;
            long enableTotalAmount = totalAmount - filterAmount;
            long amount = (long) (1.0 * value / enableTotalAmount * candyAmount);
            SafeProtos.GetCandyData getCandyData = getGetCandyProtos(curGetItem.assetId, amount);
            byte[] candyReserve = SafeUtils.serialReserve(SafeConstant.CMD_GET_CANDY, SafeConstant.SAFE_APP_ID, getCandyData.toByteArray());
            Address address = Address.fromBase58(Constants.NETWORK_PARAMETERS, item.address);
            outputs[i] = PaymentIntent.buildOutPut(Coin.valueOf(amount), address, candyReserve);
            log.info("---height = {}, address = {}, safeAmount = {}, totalAmount = {}, filterAmount = {}, enableTotalAmount = {}, candyAmount = {}, safeResult = {}", item.height, item.address, value, totalAmount, filterAmount, enableTotalAmount, candyAmount, amount);
        }

        PaymentIntent finalPaymentIntent = paymentIntent.getPaymentIntentOutput(outputs);
        SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
        TransactionOutPoint outPoint = new TransactionOutPoint(Constants.NETWORK_PARAMETERS, curGetItem.outputIndex, Sha256Hash.wrap(curGetItem.txId));
        sendRequest.tx.addInput(new TransactionInput(Constants.NETWORK_PARAMETERS, sendRequest.tx, new byte[0], outPoint));

        sendRequest.appCommand = SafeConstant.CMD_GET_CANDY;
        sendRequest.ensureMinRequiredFee = true;
        sendRequest.emptyWallet = false;
        sendRequest.memo = paymentIntent.memo;

        sendTx(sendRequest);

    }

    public void refreshUi(List<PutCandyData> candyList) {
        if (getActivity() == null || getActivity().isFinishing()) return;
        dismissProgressDialog();
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

    public SafeProtos.GetCandyData getGetCandyProtos(String assetId, long candyCount) {
        String remarks = "Get candy";

        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        return SafeProtos.GetCandyData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setAmount(candyCount)
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .build();

    }

    @Override
    public void sending() {
    }


    @Override
    public void sent() {
        try {
            if (txPageCount == 1) {
                CommonUtils.dismissProgressDialog(getActivity());
            } else {
                txPageCount--;
            }
            //标记成已领取
            SafeUtils.updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_RECEIVED), curGetItem.txId});
            adapter.remove(curGetItem);
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.receive_success));

            List<PutCandyData> putCandyList = adapter.getData();
            if (putCandyList.size() <= 10) { //小于10条刷新
                pageIndex = 0;
                getData(false);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getData(final boolean filterExpired) {
        if (filterExpired) {
            showProgressDialog();
        }
        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {
                final List<PutCandyData> candyList = new ArrayList<>();
                try {
                    BaseDaoImpl putCandyDao = new BaseDaoImpl(PutCandyData.class);

                    if (filterExpired) {

                        List<String> mSqlList = new ArrayList<>();
                        BaseDaoImpl getCandyDao = new BaseDaoImpl(GetCandyData.class);
                        List<String> receivedTxList = new ArrayList<>();
                        List<GetCandyData> getCandyList = getCandyDao.getDao().queryBuilder().selectColumns("inTxId").distinct().query();
                        for (GetCandyData item : getCandyList) {
                            receivedTxList.add(item.inTxId);
                        }

//                        log.info("-----Received Candy Count = {}", receivedTxList.size());
                        List<PutCandyData> putCandyList = putCandyDao.query("candyGetFlag", Integer.toString(PutCandyData.STATUS_ENABLED));
                        long lastBlockHeight = SafeConstant.getLastBlockHeight();
                        for (PutCandyData item : putCandyList) {
                            if (receivedTxList.contains(item.txId)) { //已领取
                                mSqlList.add("update tb_put_candy set candyGetFlag = " + PutCandyData.STATUS_RECEIVED + " where txId = '" + item.txId + "'");
                            } else if (item.isExpired(lastBlockHeight)) { //已过期
                                mSqlList.add("update tb_put_candy set candyGetFlag = " + PutCandyData.STATUS_EXPIRED + " where txId = '" + item.txId + "'");
                            }
                        }
                        putCandyDao.execSql(mSqlList);
                        mSqlList.clear();
//                        log.info("-----Filter Candy Count = {}", mSqlList.size());

                    }
                    String sql = "select txId from tb_put_candy where candyGetFlag = ?";
                    GenericRawResults<String[]> rawResults = putCandyDao.getDao().queryRaw(sql, new String[]{Integer.toString(PutCandyData.STATUS_ENABLED)});
                    pageCount = rawResults.getResults().size();

                    sql = "select a.txId, a.assetId, a.version, a.candyAmount, a.candyExpired, a.remarks," +
                            " a.txTime, a.height, a.outputIndex, a.calcFinishFlag, a.candyGetFlag, b.assetName, b.decimals " +
                            " from tb_put_candy as a inner join tb_issue as b on a.assetId = b.assetId" +
                            " where a.candyGetFlag = ? order by a.txTime " + orderBy + " limit ?, ?";

                    rawResults = putCandyDao.getDao().queryRaw(sql, new String[]{Integer.toString(PutCandyData.STATUS_ENABLED), Integer.toString(pageIndex * pageSize), Integer.toString(pageSize)});
                    List<String[]> putCandyArr = rawResults.getResults();

                    for (String[] item : putCandyArr) {
                        candyList.add(arrToPutCandy(item));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    BackgroundThread.postUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshUi(candyList);
                        }
                    });
                }
            }
        }));
    }

    @Override
    public void failed(Failed failed) {
        CommonUtils.dismissProgressDialog(getActivity());
    }

    public PutCandyData arrToPutCandy(String[] arr) {
        PutCandyData item = new PutCandyData();
        item.txId = arr[0];
        item.assetId = arr[1];
        item.version = Long.parseLong(arr[2]);
        item.candyAmount = Long.parseLong(arr[3]);
        item.candyExpired = Long.parseLong(arr[4]);
        item.remarks = arr[5];
        item.txTime = Long.parseLong(arr[6]);
        item.height = Long.parseLong(arr[7]);
        item.outputIndex = Integer.parseInt(arr[8]);
        item.calcFinishFlag = Integer.parseInt(arr[9]);
        item.candyGetFlag = Integer.parseInt(arr[10]);
        item.assetName = arr[11];
        item.decimals = Long.parseLong(arr[12]);
        return item;
    }

    public void showProgressDialog() {
        BackgroundThread.postUiThread(new Runnable() {
            @Override
            public void run() {
                CommonUtils.showProgressDialog(getActivity(), "", false);
            }
        });
    }

    public void dismissProgressDialog() {
        BackgroundThread.postUiThread(new Runnable() {
            @Override
            public void run() {
                CommonUtils.dismissProgressDialog(getActivity());
            }
        });
    }

    public void shortToast(final String msg) {
        BackgroundThread.postUiThread(new Runnable() {
            @Override
            public void run() {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(msg);
            }
        });
    }

    public void removeCurrentCandyItem() {
        BackgroundThread.postUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.remove(curGetItem);
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BackgroundThread.prepareThread();
    }

    @Override
    public void onDestroy() {
        BackgroundThread.destroyThread();
        super.onDestroy();

    }

    @Override
    public void isSyncFinish() {
        try {
            List<PutCandyData> filterCandyList = new ArrayList<>();
            List<PutCandyData> enableCandyList = new ArrayList<>();
            long lastBlockHeight = SafeConstant.getLastBlockHeight();
            List<PutCandyData> allCandyList = adapter.getData();

            BaseDaoImpl getCandyDao = new BaseDaoImpl(GetCandyData.class);
            List<String> inTxIds = new ArrayList<>();
            List<GetCandyData> getCandyList = getCandyDao.getDao().queryBuilder().selectColumns("inTxId").distinct().query();
            for (GetCandyData item : getCandyList) {
                inTxIds.add(item.inTxId);
            }
            for (PutCandyData item : allCandyList) {
                if (item.isExpired(lastBlockHeight)) { //已过期
                    SafeUtils.updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_EXPIRED), item.txId});
                    filterCandyList.add(item);
                } else if (inTxIds.contains(item.txId)) { //已领取
                    SafeUtils.updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_RECEIVED), item.txId});
                    filterCandyList.add(item);
                } else {
                    enableCandyList.add(item);
                }
            }

            if (filterCandyList.size() > 0) {
                if (filterCandyList.size() == allCandyList.size()) {
                    pageIndex = 0;
                    getData(false);
                    recyclerView.setLoadingComplete();
                } else {
                    if (enableCandyList.size() > 10) { //大于10条直接刷新界面
                        adapter.replaceAll(enableCandyList);
                    } else {
                        pageIndex = 0;
                        getData(false);
                        recyclerView.setLoadingComplete();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Menu menu;

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.get_candy_fragment_options, menu);
        super.onCreateOptionsMenu(menu, inflater);
        this.menu = menu;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.time_asc:
                setOrderBy();
                break;
            case R.id.time_desc:
                setOrderBy();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setOrderBy() {
        if (orderBy.equals("asc")) {
            orderBy = "desc";
            menu.findItem(R.id.time_asc).setVisible(true);
            menu.findItem(R.id.time_desc).setVisible(false);
        } else {
            orderBy = "asc";
            menu.findItem(R.id.time_asc).setVisible(false);
            menu.findItem(R.id.time_desc).setVisible(true);
        }
        pageIndex = 0;
        getData(false);
        recyclerView.setLoadingComplete();
    }

}
