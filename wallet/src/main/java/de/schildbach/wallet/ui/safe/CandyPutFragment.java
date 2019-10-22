package de.schildbach.wallet.ui.safe;

import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import com.google.protobuf.ByteString;
import com.j256.ormlite.dao.GenericRawResults;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.db.PutCandyData;
import de.schildbach.wallet.db.WalletAssetTx;
import de.schildbach.wallet.seekbar.IndicatorSeekBar;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.safe.bean.SafeReserve;
import de.schildbach.wallet.ui.safe.utils.*;
import de.schildbach.wallet.util.InputUtils;
import de.schildbach.wallet.R;

import org.bitcoin.safe.SafeProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 发放糖果页面
 * @author zhangmiao
 */
public class CandyPutFragment extends SendBaseFragment {

    private Spinner spAssetName;
    private ImageView imgAssetName;
    private TextView ctvTotalAmount;
    private EditText etCandyExpired;
    private TextView ctvCandyAmount;
    private IndicatorSeekBar isbCandyRate;
    private EditText etRemarks;
    private TextView tvGrant;

    private IssueData selectIssue;
    private Address selectAddress;

    private static int TX_ID_ROW = 0;
    private static int ASSET_ID_ROW = 1;
    private static int ASSET_NAME_ROW = 2;

    private List<String[]> allAssetList = new ArrayList<>();
    private List<String> filterTxList = new ArrayList<>();
    private List<String> filterAssetList = new ArrayList<>();
    private List<String> filtetNameList = new ArrayList<>();

    private PaymentIntent finalPaymentIntent;

    private boolean isLock = false; //处理手机反应慢，调用wallet不会即时反应的情况

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_candy_put;
    }


    @Override
    public void initView() {
        super.initView();
        spAssetName = (Spinner) findViewById(R.id.sp_asset_name);
        imgAssetName = (ImageView) findViewById(R.id.img_asset_name);
        ctvTotalAmount = (TextView) findViewById(R.id.ctv_total_amount);
        etCandyExpired = (EditText) findViewById(R.id.et_candy_expired);
        ctvCandyAmount = (TextView) findViewById(R.id.ctv_candy_amount);
        isbCandyRate = (IndicatorSeekBar) findViewById(R.id.isb_candy_rate);
        etRemarks = (EditText) findViewById(R.id.et_remarks);
        tvGrant = (TextView) findViewById(R.id.tv_grant);
        InputUtils.filterChart(etRemarks, 500);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.safe_candy_grant));

        List<TextView> checkList = new ArrayList<>();
        checkList.add(etCandyExpired);
        InputManager.checkEmptyListener(checkList, tvGrant);

        spAssetName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ctvCandyAmount.setText(null);
                if (position == 0) {
                    selectAddress = null;
                    selectIssue = null;
                    return;
                } else if (getBlockSync()) {
                    showBlockSyncDilog();
                    selectAddress = null;
                    spAssetName.setSelection(0);
                    return;
                }

                String txId = filterTxList.get(position);
                String assetId = filterAssetList.get(position);
                try {
                    BaseDaoImpl issueDao = new BaseDaoImpl(IssueData.class);
                    selectIssue = (IssueData) issueDao.queryForFirst("assetId", assetId);
                    selectAddress = getAssetAddress(txId);
                    BigDecimal totalDecimal = new BigDecimal(selectIssue.totalAmount);
                    ctvTotalAmount.setText(SafeUtils.getAssetAmount(totalDecimal.longValue(), selectIssue.decimals));

                    BigDecimal amount = new BigDecimal(selectIssue.totalAmount);
                    BigDecimal proFloat = new BigDecimal(isbCandyRate.getProgress());
                    BigDecimal divideCount = new BigDecimal(1000);
                    BigDecimal realPro = proFloat.divide(divideCount);
                    BigDecimal candyAmount = amount.multiply(realPro);
                    ctvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount.longValue(), selectIssue.decimals));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }

        });
        imgAssetName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spAssetName.performClick();
            }
        });

        isbCandyRate.setOnSeekChangeListener(new IndicatorSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(IndicatorSeekBar seekBar, int progress, float progressFloat, boolean fromUserTouch) {
                if (selectIssue != null) {
                    BigDecimal amount = new BigDecimal(selectIssue.totalAmount);
                    BigDecimal proFloat = new BigDecimal(progress);
                    BigDecimal divideCount = new BigDecimal(1000);
                    BigDecimal realPro = proFloat.divide(divideCount);
                    BigDecimal candyAmount = amount.multiply(realPro);
                    ctvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount.longValue(), selectIssue.decimals));
                }
            }

            @Override
            public void onSectionChanged(IndicatorSeekBar seekBar, int thumbPosOnTick, String textBelowTick, boolean fromUserTouch) {

            }

            @Override
            public void onStartTrackingTouch(IndicatorSeekBar seekBar, int thumbPosOnTick) {

            }

            @Override
            public void onStopTrackingTouch(IndicatorSeekBar seekBar) {

            }
        });
        tvGrant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CommonUtils.isRepeatClick() && !isLock) {
                    if (getBlockSync()) {
                        showBlockSyncDilog();
                        return;
                    }

                    if (!checkForm()) {
                        return;
                    }

                    isLock = true;
                    Transaction tx = wallet.getTransaction(Sha256Hash.wrap(selectIssue.txId));

                    if (tx != null) {
                        TransactionConfidence confidence = tx.getConfidence();
                        TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
                        if (confidenceType != TransactionConfidence.ConfidenceType.BUILDING) {
                            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_tx_unconfirmed));
                            isLock = false;
                            return;
                        }
                    }

                    Map<Sha256Hash, Transaction> pendingTx = wallet.getTransactionPool(WalletTransaction.Pool.PENDING);
                    if (pendingTx.size() > 0) {
                        for (Transaction item : pendingTx.values()) {
                            String assetId = item.getAssetId();
                            if (selectIssue.assetId.equals(assetId)) {
                                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_wallet_unconfirmed_tx));
                                isLock = false;
                                return;
                            }
                        }
                    }

                    Coin enableAmount = wallet.getBalance(Wallet.BalanceType.ESTIMATED, selectIssue.assetId, selectAddress);
                    BigDecimal candyAmount = new BigDecimal(ctvCandyAmount.getText().toString());
                    BigDecimal realDecimals = new BigDecimal(SafeUtils.getRealDecimals(selectIssue.decimals));
                    BigDecimal realCandyDecimal = candyAmount.multiply(realDecimals);
                    BigDecimal enableDecimal = new BigDecimal(enableAmount.getValue());
                    if (realCandyDecimal.compareTo(enableDecimal) > 0) {
                        CharSequence enable = SafeUtils.getAssetAmount(enableDecimal, selectIssue.decimals);
                        String hintMsg = getString(R.string.safe_hint_candy_amount_too_big, selectIssue.assetName, enable + selectIssue.assetUnit, ctvCandyAmount.getText() + selectIssue.assetUnit);
                        DialogBuilder dialog = new DialogBuilder(getActivity());
                        dialog.setTitle(R.string.safe_comm_title);
                        dialog.setMessage(hintMsg);
                        dialog.setPositiveButton(R.string.button_copy_address, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                ClipData mClipData = ClipData.newPlainText("safe", selectAddress.toBase58());
                                cm.setPrimaryClip(mClipData);
                                new de.schildbach.wallet.util.Toast(getActivity()).shortToast( getString(R.string.copy_success));
                            }
                        });
                        dialog.setNegativeButton(R.string.button_cancel, null);
                        dialog.setCancelable(false);
                        dialog.show();
                        isLock = false;
                        return;
                    }

                    List<PutCandyData> putCandyList = new ArrayList<>();
                    try {
                        BaseDaoImpl dao = new BaseDaoImpl(PutCandyData.class);
                        List<PutCandyData> tempList = dao.query("assetId", selectIssue.assetId);
                        putCandyList.addAll(tempList);
                    } catch (SQLException e) {
                        isLock = false;
                        return;
                    }
                    putCandy(putCandyList);
                }
            }
        });


        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {
                try {
                    filterTxList.add("");
                    filterAssetList.add("");
                    filtetNameList.add(getString(R.string.safe_asset_name));
                    BaseDaoImpl dao = new BaseDaoImpl(IssueData.class);
                    String sql = "select b.txId, b.assetId, b.assetName from (select * from tb_wallet_asset_tx where appCommand = ?) as a left join tb_issue as b on a.assetId = b.assetId";
                    GenericRawResults<String[]> rawResults = dao.getDao().queryRaw(sql, Integer.toString(SafeConstant.CMD_ISSUE));
                    allAssetList = rawResults.getResults();
                    for (String[] item : allAssetList) {
                        String txId = item[TX_ID_ROW];
                        String assetId = item[ASSET_ID_ROW];
                        String assetName = item[ASSET_NAME_ROW];
                        Address selectAddress = getAssetAddress(txId);
                        Coin enableAmount = wallet.getBalance(Wallet.BalanceType.ESTIMATED, assetId, null);
                        if (!enableAmount.isZero()) {
                            filterTxList.add(txId);
                            filterAssetList.add(assetId);
                            filtetNameList.add(assetName);
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    BackgroundThread.postUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshUI();
                        }
                    });
                }
            }
        }));
    }

    public void refreshUI() {
        if (getActivity() == null || getActivity().isFinishing()) return;
        ArrayAdapter arrayAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_spinner_item, filtetNameList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAssetName.setDropDownWidth(500);
        spAssetName.setAdapter(arrayAdapter);
    }

    public void putCandy(List<PutCandyData> putCandyList) {

        try {
            SafeProtos.PutCandyData candyData = getPutCandyProtos();
            byte[] candyReserve = SafeUtils.serialReserve(SafeConstant.CMD_GRANT_CANDY, SafeConstant.SAFE_APP_ID, candyData.toByteArray());

            //---------测试解析开始---------
//        SafeReserve safeReserve = SafeUtils.parseReserve(candyReserve);
//        SafeProtos.PutCandyData candyProtos = SafeProtos.PutCandyData.parseFrom(safeReserve.protos);
//        PutCandyData candy = new PutCandyData();
//        candy.assetId = Sha256Hash.wrap(candyProtos.getAssetId().toByteArray()).toString();
//        candy.version = Utils.readUint16(candyProtos.getVersion().toByteArray(), 0);
//        candy.candyAmount = candyProtos.getAmount();
//        candy.candyExpired = Utils.readUint16(candyProtos.getExpired().toByteArray(), 0);
//        candy.remarks = candyProtos.getRemarks().toStringUtf8();
//        log.info("-------assetId = {}", candy.assetId);
//        log.info("--------------candy = {}", candy);
            //---------测试解析开始---------

            Address candyBlockHoleAddress = Address.fromBase58(Constants.NETWORK_PARAMETERS, SafeConstant.CANDY_BLACK_HOLE_ADDRESS);
            Coin candyCoin = Coin.valueOf(candyData.getAmount());
            PaymentIntent.Output paymentIntentOutput = PaymentIntent.buildOutPut(candyCoin, candyBlockHoleAddress, candyReserve);
            finalPaymentIntent = paymentIntent.getPaymentIntentOutput(new PaymentIntent.Output[]{paymentIntentOutput});
            SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
            if (setAssetChange(sendRequest, finalPaymentIntent.getAmount())) {
//                log.info("--------------candy = {}, amount = {} ", candyCoin, finalPaymentIntent.getAmount());
                sendRequest.appCommand = SafeConstant.CMD_GRANT_CANDY;
                sendRequest.ensureMinRequiredFee = true;
                sendRequest.emptyWallet = false;
                sendRequest.memo = paymentIntent.memo;
                sendRequest.signInputs = false;
                wallet.completeTx(sendRequest);
            } else {
                isLock = false;
                return;
            }
        } catch (Exception e) {
            tipError(e);
            isLock = false;
            return;
        }
        int spentCount = putCandyList.size();
        int enableCount = SafeConstant.CANDY_GRANT_MAX_TIMES - spentCount;
        if (enableCount > 0) {
            String msg = getString(R.string.safe_hint_put_candy_confirm, enableCount, ctvCandyAmount.getText().toString());
            final DialogBuilder dialog = DialogBuilder.warn(getActivity(), R.string.safe_candy_grant);
            dialog.setMessage(msg);
            dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    CommonUtils.showProgressDialog(getActivity(), "");
                    SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
                    setAssetChange(sendRequest, finalPaymentIntent.getAmount());
                    sendRequest.appCommand = SafeConstant.CMD_GRANT_CANDY;
                    sendRequest.ensureMinRequiredFee = true;
                    sendRequest.emptyWallet = false;
                    sendRequest.memo = paymentIntent.memo;
                    sendRequest.signInputs = true;
                    sendTx(sendRequest);
                    isLock = false;
                }
            });
            dialog.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    isLock = false;
                }
            });
            dialog.setCancelable(false);
            dialog.show();
        } else {
            String msg = getString(R.string.safe_issue_count_used);
            final DialogBuilder dialog = DialogBuilder.warn(getActivity(), R.string.safe_candy_grant);
            dialog.setMessage(msg);
            dialog.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    isLock = false;
                }
            });
            dialog.setCancelable(false);
            dialog.show();
        }
    }

    private boolean setAssetChange(SendRequest sendRequest, Coin candyCoin) {
        //---------处理资产找零---------
        List<TransactionOutput> allOutput = wallet.calculateAllSpendCandidates(true, sendRequest.missingSigsMode == Wallet.MissingSigsMode.THROW, selectIssue.assetId, selectAddress);
        List<TransactionOutput> candidates = new ArrayList<>();
        for (TransactionOutput output : allOutput) { //过滤锁定的金额
            if (output.getLockHeight() != 0 && SafeConstant.getLastBlockHeight() < output.getLockHeight()) {
                continue;
            } else {
                candidates.add(output);
            }
        }
        CoinSelection bestCoinSelection = wallet.getCoinSelector().select(candyCoin, candidates);
        for (TransactionOutput output : bestCoinSelection.gathered) {
            sendRequest.tx.addInput(output);
        }
        Coin bestValue = bestCoinSelection.valueGathered;
        if (bestValue.compareTo(candyCoin) > 0) {
            Coin changeValue = bestValue.subtract(candyCoin);
            SafeProtos.CommonData commonData = getAssetChangeProtos(changeValue.getValue());
            byte[] changeReserve;
            try {
                changeReserve = SafeUtils.serialReserve(SafeConstant.CMD_ASSET_CHANGE, SafeConstant.SAFE_APP_ID, commonData.toByteArray());
            } catch (Exception e) {
                return false;
            }
            Script script = ScriptBuilder.createOutputScript(selectAddress);
            TransactionOutput output = new TransactionOutput(Constants.NETWORK_PARAMETERS, sendRequest.tx, changeValue, script.getProgram(), 0, changeReserve);
            sendRequest.tx.addOutput(output);
            int assetSignSize = wallet.estimateBytesForSigning(bestCoinSelection); //试算资产找零交易签名的大小
//            log.info("--------assetSignSize = {}", assetSignSize);
            sendRequest.assetSignSize = assetSignSize;
            return true;
        } else if (bestValue.compareTo(candyCoin) < 0) {
            CharSequence best = Constants.getAssetFormat((int) selectIssue.decimals).format(new AssetCoin(bestValue.getValue(), (int) selectIssue.decimals));
            CharSequence missing = Constants.getAssetFormat((int) selectIssue.decimals).format(new AssetCoin(candyCoin.subtract(bestValue).getValue(), (int) selectIssue.decimals));
            DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            dialog.setMessage(getString(R.string.put_candy_insufficient_money_error, selectIssue.assetName, best + selectIssue.assetUnit, missing + selectIssue.assetUnit));
            dialog.setPositiveButton(R.string.button_ok, null);
            dialog.setCancelable(false);
            dialog.show();
            return false;
        }
        return true;
        //---------处理资产找零---------
    }

    private void tipError(Exception e) {
        if (e instanceof InsufficientMoneyException) {
            MonetaryFormat btcFormat = config.getMaxPrecisionFormat();
            InsufficientMoneyException ime = (InsufficientMoneyException) e;
            String hintMoney;
            if (ime.useTxLimit) {
                hintMoney = getString(R.string.safe_tx_send_limit);
            } else {
                Coin mCoin = ((InsufficientMoneyException) e).missing;
                hintMoney = getString(R.string.send_coins_fragment_hint_insufficient_money, btcFormat.format(mCoin));
            }
            DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            dialog.setMessage(hintMoney);
            dialog.setPositiveButton(R.string.button_ok, null);
            dialog.setCancelable(false);
            dialog.show();
        } else {
            DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            dialog.setMessage(e.getMessage());
            dialog.setPositiveButton(R.string.button_ok, null);
            dialog.setCancelable(false);
            dialog.show();
        }
    }

    public boolean checkForm() {

        if (selectAddress == null) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_select_asset_name));
            return false;
        }

        String remarkStr = etRemarks.getText().toString().trim();

        try {
            if (remarkStr.getBytes("UTF-8").length > 500) {
                Toast.makeText(getActivity(), R.string.safe_hint_remark_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return true;
    }


    public SafeProtos.CommonData getAssetChangeProtos(long assetChangeAmount) {
        String assetId = selectIssue.assetId;
        String remarks = "Asset change";

        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        return SafeProtos.CommonData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setAmount(assetChangeAmount)
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .build();
    }

    public SafeProtos.PutCandyData getPutCandyProtos() {
        String assetId = selectIssue.assetId;
        long candyExpired = Long.parseLong(etCandyExpired.getText().toString());
        BigDecimal candyAmount = new BigDecimal(ctvCandyAmount.getText().toString());
        BigDecimal realDecimals = new BigDecimal(SafeUtils.getRealDecimals(selectIssue.decimals));
        BigDecimal realCandydecimal = candyAmount.multiply(realDecimals);
        String remarks = etRemarks.getText().toString().trim();
        if (TextUtils.isEmpty(remarks)) {
            remarks = "";
        }

        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        byte[] candyExpiredByte = new byte[2];
        Utils.uint16ToByteArrayLE(candyExpired, candyExpiredByte, 0);

        return SafeProtos.PutCandyData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAmount(realCandydecimal.longValue())
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .setExpired(ByteString.copyFrom(candyExpiredByte))
                .build();
    }

    public Address getAssetAddress(String txId) {
        Transaction tx = wallet.getTransaction(Sha256Hash.wrap(txId));
        for (TransactionOutput output : tx.getOutputs()) {
            SafeReserve reserve = SafeUtils.parseReserve(output.getReserve());
            if (reserve.isIssue()) {
                return output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS);
            }
        }
        return null;
    }

    @Override
    public void sending() {
        CommonUtils.showProgressDialog(getActivity(), "");
        tvGrant.setText(getString(R.string.send_coins_sending_msg));
        tvGrant.setEnabled(false);
    }

    @Override
    public void sent() {
        CommonUtils.dismissProgressDialog(getActivity());
        tvGrant.setText(getString(R.string.send_coins_sent_msg));
        Intent intent = new Intent(getActivity(), MainActivity.class);
        startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void failed(Failed failed) {
        CommonUtils.dismissProgressDialog(getActivity());
        tvGrant.setText(getString(R.string.safe_grant));
        tvGrant.setEnabled(true);
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

    }

}
