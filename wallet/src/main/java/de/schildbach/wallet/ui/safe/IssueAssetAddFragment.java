package de.schildbach.wallet.ui.safe;

import android.app.LoaderManager;
import android.content.*;
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
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.safe.bean.SafeReserve;
import de.schildbach.wallet.ui.safe.utils.BackgroundThread;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.EditTextJudgeNumber;
import de.schildbach.wallet.ui.safe.utils.InputManager;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.InputUtils;
import de.schildbach.wallet.R;

import org.bitcoin.safe.SafeProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.SendRequest;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 追加资产
 * @author zhangmiao
 */
public class IssueAssetAddFragment extends SendBaseFragment {

    private Spinner spAssetName;
    private ImageView imgAssetName;
    private TextView tvEnableAmount;
    private EditText etAmount;
    private EditText etRemarks;
    private TextView tvIssue;

    private IssueData selectIssue;
    private Address selectAddress;

    private static int TX_ID_ROW = 0;
    private static int ASSET_ID_ROW = 1;
    private static int ASSET_NAME_ROW = 2;

    private static final int ID_AMOUNT_LOADER = 0;
    private LoaderManager loaderManager;
    private long surplusAmount;

    private List<String[]> allAssetList = new ArrayList<>();
    private List<String> filterTxList = new ArrayList<>();
    private List<String> filterAssetList = new ArrayList<>();
    private List<String> filetNameList = new ArrayList<>();
    private PaymentIntent finalPaymentIntent;

    private boolean isLock = false; //处理手机反应慢，调用wallet不会即时反应的情况

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_asset_add;
    }

    @Override
    public void initView() {
        super.initView();
        getRootView().setBackgroundResource(R.color.white);
        spAssetName = (Spinner) findViewById(R.id.sp_asset_name);
        imgAssetName = (ImageView) findViewById(R.id.img_asset_name);
        tvEnableAmount = (TextView) findViewById(R.id.tv_enable_amount);
        etAmount = (EditText) findViewById(R.id.et_amount);
        etRemarks = (EditText) findViewById(R.id.et_remarks);
        tvIssue = (TextView) findViewById(R.id.tv_issue);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        loaderManager = getLoaderManager();
        getActivity().setTitle(getString(R.string.safe_add_issue));
        List<TextView> checkList = new ArrayList<>();
        checkList.add(etAmount);
        InputManager.checkEmptyListener(checkList, tvIssue);
        spAssetName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                tvEnableAmount.setText(null);
                tvEnableAmount.setVisibility(View.GONE);
                if (position == 0) {
                    selectAddress = null;
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
                    loaderManager.destroyLoader(ID_AMOUNT_LOADER);
                    loaderManager.initLoader(ID_AMOUNT_LOADER, null, new LoaderManager.LoaderCallbacks<Long>() {
                        @Override
                        public Loader<Long> onCreateLoader(int id, Bundle args) {
                            return new AssetAmountLoader(getActivity(), selectIssue);
                        }

                        @Override
                        public void onLoadFinished(Loader<Long> loader, Long amount) {
                            surplusAmount = amount;
                            tvEnableAmount.setText(getString(R.string.avaliable_str) + "：" + SafeUtils.getAssetAmount(amount, selectIssue.decimals));
                            tvEnableAmount.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onLoaderReset(Loader<Long> loader) {

                        }
                    });
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

        tvIssue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(CommonUtils.isRepeatClick() && !isLock){
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
                    startAddIssue();
                }
            }
        });
        InputUtils.filterChart(etRemarks, 500);

        etAmount.addTextChangedListener(new EditTextJudgeNumber(etAmount, 15, 10, true));

        BackgroundThread.post(new BackgroundThread.MyBgThread(new BackgroundThread.MyBgThread.ThreadCallBack() {
            @Override
            public void run() {
                try {
                    filterTxList.add("");
                    filterAssetList.add("");
                    filetNameList.add(getString(R.string.safe_asset_name));
                    BaseDaoImpl issueDao = new BaseDaoImpl(IssueData.class);
                    String sql = "select b.txId, b.assetId, b.assetName from (select * from tb_wallet_asset_tx where appCommand = ?) as a left join tb_issue as b on a.assetId = b.assetId";
                    GenericRawResults<String[]> rawResults = issueDao.getDao().queryRaw(sql, Integer.toString(SafeConstant.CMD_ISSUE));
                    allAssetList = rawResults.getResults();
                    for (String[] item : allAssetList) {
                        String txId = item[TX_ID_ROW];
                        String assetId = item[ASSET_ID_ROW];
                        String assetName = item[ASSET_NAME_ROW];
                        IssueData selectIssue = (IssueData) issueDao.queryForFirst("assetId", assetId);
                        long amount = AssetAmountLoader.getIssueAmount(selectIssue);
                        if (amount > 0) {
                            filterTxList.add(txId);
                            filterAssetList.add(assetId);
                            filetNameList.add(assetName);
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

    public void startAddIssue() {

        try {
            SafeProtos.CommonData commonData = getAssetAddProtos();
            byte[] vReserve = SafeUtils.serialReserve(SafeConstant.CMD_ADD_ISSUE, SafeConstant.SAFE_APP_ID, commonData.toByteArray());
            PaymentIntent.Output output = PaymentIntent.buildOutPut(Coin.valueOf(commonData.getAmount()), selectAddress, vReserve);
            paymentIntent.appCommand = SafeConstant.CMD_ADD_ISSUE;
            finalPaymentIntent = paymentIntent.getPaymentIntentOutput(new PaymentIntent.Output[]{output});
            SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
            sendRequest.appCommand = SafeConstant.CMD_ADD_ISSUE;
            sendRequest.address = selectAddress;
            sendRequest.changeAddress = selectAddress;
            sendRequest.ensureMinRequiredFee = true;
            sendRequest.emptyWallet = false;
            sendRequest.memo = paymentIntent.memo;
            sendRequest.signInputs = false;
            wallet.completeTx(sendRequest);
        } catch (Exception e) {
            tipError(e);
            isLock = false;
            return;
        }

        final DialogBuilder dialog = DialogBuilder.warn(getActivity(), R.string.safe_add_issue);
        dialog.setMessage(R.string.safe_hint_add_asset_issue_confirm);
        dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                CommonUtils.showProgressDialog(getActivity(), "");
                SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
                sendRequest.appCommand = SafeConstant.CMD_ADD_ISSUE;
                sendRequest.address = selectAddress;
                sendRequest.changeAddress = selectAddress;
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
    }

    public void tipError(Exception e) {
        if (e instanceof InsufficientMoneyException) {
            MonetaryFormat btcFormat = config.getMaxPrecisionFormat();
            InsufficientMoneyException ime = (InsufficientMoneyException) e;
            String hintMoney;
            if (ime.useTxLimit) {
                hintMoney = getString(R.string.safe_tx_send_limit);
            } else {
                hintMoney = getString(R.string.safe_hint_asset_add_insufficient_money, selectAddress.toBase58(), btcFormat.format(Coin.valueOf(1000000)));
            }
            DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            dialog.setMessage(hintMoney);
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

        if (selectIssue != null && selectIssue.totalAmount != 0) {
            String totalAmount = etAmount.getText().toString().trim();
            BigDecimal totalAmountDecimal = new BigDecimal(totalAmount);
            if (totalAmountDecimal.compareTo(new BigDecimal(0)) <= 0) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_issue_add_too_small));
                return false;
            }
            if ((totalAmount.indexOf(".") + 1) == totalAmount.length()) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.add_safe_total_amount_or_decimal_mismatch));
                etAmount.requestFocus();
                return false;
            }
            BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(selectIssue.decimals));
            totalAmountDecimal = totalAmountDecimal.multiply(realDecimal);
            if (new BigDecimal(surplusAmount).compareTo(totalAmountDecimal) < 0) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_issue_add_too_big));
                return false;
            }

            totalAmount = new BigDecimal(totalAmount).stripTrailingZeros().toPlainString();
            int frontLength = totalAmount.indexOf(".");
            if (frontLength > 0) {
                int behindLength = totalAmount.substring(frontLength + 1).length();
                if (behindLength > selectIssue.decimals) {
                    new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.add_safe_total_amount_or_decimal_mismatch));
                    etAmount.requestFocus();
                    return false;
                }
            }
        }

        String remarkStr = etRemarks.getText().toString().trim();

        try {
            if (remarkStr.getBytes("UTF-8").length > 500) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_remark_invalid));
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return true;
    }

    public SafeProtos.CommonData getAssetAddProtos() {
        String assetId = selectIssue.assetId;
        String amount = etAmount.getText().toString();
        BigDecimal totalDecimal = new BigDecimal(amount);
        long amountLong = totalDecimal.multiply(new BigDecimal(SafeUtils.getRealDecimals(selectIssue.decimals))).longValue();
        String remarks = etRemarks.getText().toString().trim();
        if (TextUtils.isEmpty(remarks)) {
            remarks = "";
        }
        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        return SafeProtos.CommonData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setAmount(amountLong)
                .setRemarks(ByteString.copyFromUtf8(remarks))
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
        tvIssue.setText(getString(R.string.send_coins_sending_msg));
        tvIssue.setEnabled(false);
    }

    @Override
    public void sent() {
        CommonUtils.dismissProgressDialog(getActivity());
        tvIssue.setText(getString(R.string.send_coins_sent_msg));
        Intent intent = new Intent(getActivity(), MainActivity.class);
        startActivity(intent);
        getActivity().finish();
    }

    @Override
    public void failed(Failed failed) {
        CommonUtils.dismissProgressDialog(getActivity());
        tvIssue.setText(getString(R.string.safe_issue));
        tvIssue.setEnabled(true);
    }

    public void refreshUI() {
        if (getActivity() == null || getActivity().isFinishing()) return;
        ArrayAdapter arrayAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_spinner_item, filetNameList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAssetName.setAdapter(arrayAdapter);
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
