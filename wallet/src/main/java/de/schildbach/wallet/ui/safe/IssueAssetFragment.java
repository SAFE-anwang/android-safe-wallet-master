package de.schildbach.wallet.ui.safe;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;

import com.google.protobuf.ByteString;
import com.j256.ormlite.dao.GenericRawResults;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.seekbar.IndicatorSeekBar;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.EditTextJudgeNumber;
import de.schildbach.wallet.ui.safe.utils.InputManager;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.InputUtils;
import de.schildbach.wallet.util.Toast;
import de.schildbach.wallet.R;

import org.bitcoin.safe.SafeProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.SafeConstant;
import org.bitcoinj.wallet.SendRequest;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 发行资产
 * @author zhangmiao
 */
public class IssueAssetFragment extends SendBaseFragment {

    private ScrollView svRoot;
    private View llInner;
    private EditText etAssetName;
    private TextView tvCheckExist;
    private EditText etShortName;
    private EditText etAssetsUnit;
    private EditText etTotalAmount;
    private EditText etFirstIssueAmount;
    private EditText etDecimals;
    private EditText etAssetDesc;
    private EditText etRemarks;
    private CheckBox cbCanDestory;
    private CheckBox cbPayCandy;
    private LinearLayout llCandyExpired;
    private LinearLayout llCandyRate;
    private EditText etCandyExpired;
    private TextView tvCandyAmount;
    private IndicatorSeekBar isbCandyRate;
    private TextView tvIssue;
    private List<String> filterKeyWord = SafeUtils.getFilterKeyWord();
    private List<String> filterSimilarKeyWord = SafeUtils.getFilterSimilarKeyWord();
    private InputManager assetIM;
    PaymentIntent finalPaymentIntent;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_asset_issue;
    }

    @Override
    public void initView() {
        super.initView();
        getRootView().setBackgroundResource(R.color.white);
        svRoot = (ScrollView) findViewById(R.id.sv_root);
        llInner = findViewById(R.id.ll_inner);
        etAssetName = (EditText) findViewById(R.id.et_asset_name);
        tvCheckExist = (TextView) findViewById(R.id.tv_check_exist);
        etShortName = (EditText) findViewById(R.id.et_short_name);
        etAssetsUnit = (EditText) findViewById(R.id.et_asset_unit);
        etTotalAmount = (EditText) findViewById(R.id.et_total_amount);
        etFirstIssueAmount = (EditText) findViewById(R.id.et_first_issue_amount);
        etDecimals = (EditText) findViewById(R.id.et_decimals);
        etAssetDesc = (EditText) findViewById(R.id.et_asset_desc);
        etRemarks = (EditText) findViewById(R.id.et_remarks);
        cbCanDestory = (CheckBox) findViewById(R.id.cb_can_destory);
        cbPayCandy = (CheckBox) findViewById(R.id.cb_pay_candy);
        llCandyExpired = (LinearLayout) findViewById(R.id.ll_candy_expired);
        llCandyRate = (LinearLayout) findViewById(R.id.ll_candy_rate);
        etCandyExpired = (EditText) findViewById(R.id.et_candy_expired);
        tvCandyAmount = (TextView) findViewById(R.id.tv_candy_amount);
        isbCandyRate = (IndicatorSeekBar) findViewById(R.id.isb_candy_rate);
        tvIssue = (TextView) findViewById(R.id.tv_issue);
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.safe_asset_issue));

        List<TextView> nameCheckList = new ArrayList<>();
        nameCheckList.add(etAssetName);
        InputManager.checkEmptyListener(nameCheckList, tvCheckExist);
        List<TextView> checkList = new ArrayList<>();
        checkList.add(etAssetName);
        checkList.add(etShortName);
        checkList.add(etAssetsUnit);
        checkList.add(etTotalAmount);
        checkList.add(etFirstIssueAmount);
        checkList.add(etDecimals);
        checkList.add(etAssetDesc);
        assetIM = InputManager.checkEmptyListener(checkList, tvIssue, new InputManager.OnOuterCheckListener() {
            @Override
            public boolean onOuterCheck() {
                if (cbPayCandy.isChecked()) {
                    if (!TextUtils.isEmpty(etCandyExpired.getText())) {
                        return true;
                    } else {
                        return false;
                    }
                }
                return true;
            }
        });

        etAssetName.setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence charSequence, int i, int i1, Spanned spanned, int i2, int i3) {
                        String regex = "^[a-zA-Z0-9\\s\\u4e00-\\u9fa5]+$";
                        for (int ix = 0; ix < charSequence.length(); ix++) {
                            String charSeq = String.valueOf(charSequence.charAt(i));
                            boolean isChinese = Pattern.matches(regex, charSeq);
                            boolean isNormal = Pattern.matches(regex, String.valueOf(charSequence.charAt(ix)));
                            if (String.valueOf(charSequence.charAt(ix)).equals(".")) {
                                return "";
                            }
                            if (!isChinese) {
                                return "";
                            }
                            if (!isNormal) {
                                return "";
                            }
                        }
                        return null;
                    }
                }, new InputFilter.LengthFilter(20)
        });

        etShortName.setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence charSequence, int i, int i1, Spanned spanned, int i2, int i3) {
                        String regex = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$";
                        for (int ix = 0; ix < charSequence.length(); ix++) {
                            String charSeq = String.valueOf(charSequence.charAt(i));
                            boolean isChinese = Pattern.matches(regex, charSeq);
                            boolean isNormal = Pattern.matches(regex, String.valueOf(charSequence.charAt(ix)));
                            if (String.valueOf(charSequence.charAt(ix)).equals(".")) {
                                return "";
                            }

                            if (!isChinese) {
                                return "";
                            }
                            if (!isNormal) {
                                return "";
                            }
                        }
                        return null;
                    }
                }, new InputFilter.LengthFilter(20)
        });

        etAssetsUnit.setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence charSequence, int i, int i1, Spanned spanned, int i2, int i3) {
                        String regex = "^[a-zA-Z\\u4e00-\\u9fa5]+$";
                        for (int ix = 0; ix < charSequence.length(); ix++) {
                            String charSeq = String.valueOf(charSequence.charAt(i));
                            boolean isChinese = Pattern.matches(regex, charSeq);
                            boolean isNormal = Pattern.matches(regex, String.valueOf(charSequence.charAt(ix)));
                            if (String.valueOf(charSequence.charAt(ix)).equals(".")) {
                                return "";
                            }
                            if (!isChinese) {
                                return "";
                            }
                            if (!isNormal) {
                                return "";
                            }
                        }
                        return null;
                    }
                }, new InputFilter.LengthFilter(10)
        });

        tvCheckExist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CommonUtils.isRepeatClick()) {
                    if (getBlockSync()) {
                        showBlockSyncDilog();
                        return;
                    }
                    if (checkAssetNameExist()) {
                        new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_enable));
                    }
                }
            }
        });

        cbPayCandy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    llCandyExpired.setVisibility(View.VISIBLE);
                    llCandyRate.setVisibility(View.VISIBLE);
                    if (tvIssue.isEnabled() && TextUtils.isEmpty(etCandyExpired.getText())) {
                        tvIssue.setEnabled(false);
                    }
                    scrollToBottom(svRoot, llInner);
                } else {
                    llCandyExpired.setVisibility(View.GONE);
                    llCandyRate.setVisibility(View.GONE);
                    if (!assetIM.isInputEmpty()) {
                        tvIssue.setEnabled(true);
                    } else {
                        tvIssue.setEnabled(false);
                    }
                }
            }
        });

        etTotalAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String content = etTotalAmount.getText().toString();
                if (content.indexOf(".") == 0) {
                    content = "";
                }
                if (content.trim().length() == 0) {
                    tvCandyAmount.setText("");
                    return;
                }
                String decimals = etDecimals.getText().toString();
                if (TextUtils.isEmpty(decimals)) {
                    decimals = "0";
                }
                if (!TextUtils.isEmpty(content) && !TextUtils.isEmpty(decimals)) {
                    BigDecimal amount = new BigDecimal(content);
                    BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(Long.parseLong(decimals)));
                    amount = amount.multiply(realDecimal);
                    BigDecimal proFloat = new BigDecimal(isbCandyRate.getProgress());
                    BigDecimal divideCount = new BigDecimal(1000);
                    BigDecimal realPro = proFloat.divide(divideCount);
                    BigDecimal candyAmount = amount.multiply(realPro);
                    if (Long.parseLong(decimals) == 0) {
                        tvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount, Long.parseLong(decimals)).replace(".", ""));
                    } else {
                        tvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount, Long.parseLong(decimals)));
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        etDecimals.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (etDecimals.getText().toString().matches("^0")) {//判断当前的输入第一个数是不是为0
                    etDecimals.setText("");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(etDecimals.getText())) {

                    /**
                     * 限制不能再最前面输入0
                     */
                    if (s.toString().startsWith("0")) {
                        if (s.toString().length() > 1 && s.toString().startsWith("0")) {
                            s.delete(0, s.toString().indexOf("0") + 1);
                        }
                        return;
                    }

                    long decimals = Long.parseLong(etDecimals.getText().toString());
                    if (decimals > 10) {
                        etDecimals.setText(etDecimals.getText().subSequence(0,1));
                        etDecimals.setSelection(etDecimals.length());
                        decimals = Integer.parseInt(etDecimals.getText().toString());
                    }
                    String content = etTotalAmount.getText().toString();
                    if (content.indexOf(".") == 0) {
                        content = "";
                    }
                    if (!TextUtils.isEmpty(content)) {
                        BigDecimal amount = new BigDecimal(content);
                        BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(decimals));
                        amount = amount.multiply(realDecimal);
                        BigDecimal proFloat = new BigDecimal(isbCandyRate.getProgress());
                        BigDecimal divideCount = new BigDecimal(1000);
                        BigDecimal realPro = proFloat.divide(divideCount);
                        BigDecimal candyAmount = amount.multiply(realPro);
                        tvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount, decimals));
                    }
                } else {
                    long decimals = 0;
                    String content = etTotalAmount.getText().toString();
                    if (!TextUtils.isEmpty(content)) {
                        BigDecimal amount = new BigDecimal(content);
                        BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(decimals));
                        amount = amount.multiply(realDecimal);
                        BigDecimal proFloat = new BigDecimal(isbCandyRate.getProgress());
                        BigDecimal divideCount = new BigDecimal(1000);
                        BigDecimal realPro = proFloat.divide(divideCount);
                        BigDecimal candyAmount = amount.multiply(realPro);
                        if (SafeUtils.getAssetAmount(candyAmount, decimals).contains(".")) {
                            tvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount, decimals).replace(".", ""));
                        }
                    }
                }
            }
        });

        isbCandyRate.setOnSeekChangeListener(new IndicatorSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(IndicatorSeekBar seekBar, int progress, float progressFloat, boolean fromUserTouch) {
                String content = etTotalAmount.getText().toString();
                if (content.indexOf(".") == 0) {
                    content = "";
                }
                String decimals = etDecimals.getText().toString();
                if (TextUtils.isEmpty(decimals)) {
                    decimals = "0";
                }
                if (!TextUtils.isEmpty(content) && !TextUtils.isEmpty(decimals)) {
                    BigDecimal amount = new BigDecimal(content);
                    BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(Long.parseLong(decimals)));
                    amount = amount.multiply(realDecimal);
                    BigDecimal proFloat = new BigDecimal(progress);
                    BigDecimal divideCount = new BigDecimal(1000);
                    BigDecimal realPro = proFloat.divide(divideCount);
                    BigDecimal candyAmount = amount.multiply(realPro);
                    if (decimals.equals("0")) {
                        if (SafeUtils.getAssetAmount(candyAmount, Long.parseLong(decimals)).contains(".")) {
                            tvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount, Long.parseLong(decimals)).replace(".", ""));
                        }
                    } else {
                        tvCandyAmount.setText(SafeUtils.getAssetAmount(candyAmount, Long.parseLong(decimals)));
                    }
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

        etCandyExpired.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!assetIM.isInputEmpty() && !TextUtils.isEmpty(etCandyExpired.getText())) {
                    tvIssue.setEnabled(true);
                } else {
                    tvIssue.setEnabled(false);
                }
            }
        });

        tvIssue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CommonUtils.isRepeatClick()) {
                    if (getBlockSync()) {
                        showBlockSyncDilog();
                        return;
                    }
                    if (!checkForm()) {
                        return;
                    }
                    startIssue();
                }
            }
        });
        InputUtils.filterChart(etAssetName, 20);
        InputUtils.filterChart(etShortName, 20);
        InputUtils.filterChart(etAssetsUnit, 10);
        InputUtils.filterChart(etAssetDesc, 300);
        InputUtils.filterChart(etRemarks, 500);
        InputUtils.filterSign(etAssetName);
        InputUtils.filterSign(etShortName);
        etTotalAmount.addTextChangedListener(new EditTextJudgeNumber(etTotalAmount, 15, 10));
        etFirstIssueAmount.addTextChangedListener(new EditTextJudgeNumber(etFirstIssueAmount, 15, 10));
        etAssetName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.toString().length() > 0  && InputUtils.isStartNumeric(String.valueOf(editable.toString().charAt(0)))){
                    if (editable.toString().length() > 1){
                        etAssetName.setText(editable.toString().substring(1));
                    }else {
                        etAssetName.setText("");
                    }

                }
            }
        });
        etShortName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.toString().length() >0  && InputUtils.isStartNumeric(String.valueOf(editable.toString().charAt(0)))){
                    if (editable.toString().length() > 1){
                        etShortName.setText(editable.toString().substring(1));
                    }else {
                        etShortName.setText("");
                    }

                }
            }
        });

    }

    public void startIssue() {
        try {
            SafeProtos.IssueData issueData = getIssueProtos();
            byte[] issueReserve = SafeUtils.serialReserve(SafeConstant.CMD_ISSUE, SafeConstant.SAFE_APP_ID, issueData.toByteArray());

            //---------测试解析开始---------
//            SafeReserve safeReserve = SafeUtils.parseReserve(issueReserve);
//            SafeProtos.IssueData protos = SafeProtos.IssueData.parseFrom(safeReserve.protos);
//            IssueData issue = new IssueData();
//            issue.version = Utils.readUint16(protos.getVersion().toByteArray(), 0);
//            issue.shortName = protos.getShortName().toStringUtf8();
//            issue.assetName = protos.getAssetName().toStringUtf8();
//            issue.assetDesc = protos.getAssetDesc().toStringUtf8();
//            issue.assetUnit = protos.getAssetUnit().toStringUtf8();
//            issue.totalAmount = protos.getTotalAmount();
//            issue.firstIssueAmount = protos.getFirstIssueAmount();
//            issue.firstActualAmount = protos.getFirstActualAmount();
//            issue.decimals = Utils.readUint8(protos.getDecimals().toByteArray(), 0);
//            issue.destory = protos.getDestory();
//            issue.payCandy = protos.getPayCandy();
//            issue.candyAmount = protos.getCandyAmount();
//            issue.candyExpired = Utils.readUint16(protos.getVersion().toByteArray(), 0);
//            issue.remarks = protos.getRemarks().toStringUtf8();
//            log.info("--------------issue = {} ", issue);
            //---------测试解析结束---------

            String assetId = SafeUtils.generateAssetId(issueData);

            Coin blockHoleCoin = SafeUtils.getCancelledAmount();
            log.info("---- blockHoleCoin = {}", blockHoleCoin);

            Address blockHoleAddress = Address.fromBase58(Constants.NETWORK_PARAMETERS, SafeConstant.BLACK_HOLE_ADDRESS);
            Coin assetCoin = Coin.valueOf(issueData.getFirstActualAmount());

            PaymentIntent.Output output1 = PaymentIntent.buildOutPut(blockHoleCoin, blockHoleAddress);
            PaymentIntent.Output output2 = PaymentIntent.buildOutPut(assetCoin, wallet.currentReceiveAddress(), issueReserve);

            if (issueData.getPayCandy()) {

                SafeProtos.PutCandyData candyData = getCandyProtos(assetId);
                byte[] candyReserve = SafeUtils.serialReserve(SafeConstant.CMD_GRANT_CANDY, SafeConstant.SAFE_APP_ID, candyData.toByteArray());

                //---------测试解析结束---------
//                safeReserve = SafeUtils.parseReserve(candyReserve);
//                SafeProtos.PutCandyData candyProtos = SafeProtos.PutCandyData.parseFrom(safeReserve.protos);
//                PutCandyData candy = new PutCandyData();
//                candy.assetId = SafeUtils.hash256ToAssetId(candyProtos.getAssetId().toByteArray());
//                candy.version = Utils.readUint16(candyProtos.getVersion().toByteArray(), 0);
//                candy.candyAmount = candyProtos.getAmount();
//                candy.candyExpired = Utils.readUint16(candyProtos.getExpired().toByteArray(), 0);
//                candy.remarks = candyProtos.getRemarks().toStringUtf8();
//                log.info("-------candy assetId = {}", candy.assetId);
//                log.info("--------------candy = {}", candy);
                //---------测试解析结束---------

                Address candyBlockHoleAddress = Address.fromBase58(Constants.NETWORK_PARAMETERS, SafeConstant.CANDY_BLACK_HOLE_ADDRESS);
                Coin candyCoin = Coin.valueOf(issueData.getCandyAmount());
                PaymentIntent.Output output3 = PaymentIntent.buildOutPut(candyCoin, candyBlockHoleAddress, candyReserve);
                finalPaymentIntent = paymentIntent.getPaymentIntentOutput(new PaymentIntent.Output[]{output1, output2, output3});
            } else {
                finalPaymentIntent = paymentIntent.getPaymentIntentOutput(new PaymentIntent.Output[]{output1, output2});
            }

            SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
            sendRequest.appCommand = SafeConstant.CMD_ISSUE;
            sendRequest.ensureMinRequiredFee = true;
            sendRequest.emptyWallet = false;
            sendRequest.memo = paymentIntent.memo;
            sendRequest.signInputs = false;
            wallet.completeTx(sendRequest);

        } catch (Exception e) {
            tipError(e);
            return;
        }

        Coin coin = SafeUtils.getCancelledAmount();

        String msg;

        if (cbPayCandy.isChecked()) {
            msg = getString(R.string.safe_hint_asset_issue_candy_confirm, SafeConstant.CANDY_GRANT_MAX_TIMES, config.getFormat().format(coin));
        } else {
            msg = getString(R.string.safe_hint_asset_issue_confirm, config.getFormat().format(coin));
        }

        final DialogBuilder dialog = DialogBuilder.warn(getActivity(), R.string.safe_asset_issue);
        dialog.setMessage(msg);
        dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                CommonUtils.showProgressDialog(getActivity(), "");
                SendRequest sendRequest = finalPaymentIntent.toSendRequest(0);
                sendRequest.appCommand = SafeConstant.CMD_ISSUE;
                sendRequest.ensureMinRequiredFee = true;
                sendRequest.emptyWallet = false;
                sendRequest.memo = paymentIntent.memo;
                sendRequest.signInputs = true;
                sendTx(sendRequest);
            }
        });
        dialog.setNegativeButton(R.string.button_cancel, null);
        dialog.setCancelable(false);
        dialog.show();
    }

    public void tipError(Exception e) {
        e.printStackTrace();
        if (e instanceof InsufficientMoneyException) {
            MonetaryFormat btcFormat = config.getMaxPrecisionFormat();
            InsufficientMoneyException ime = (InsufficientMoneyException) e;
            String hintMoney;
            if (ime.useTxLimit) {
                hintMoney = getString(R.string.safe_tx_send_limit);
            } else {
                Coin mCoin = ime.missing.add(Coin.valueOf(1000000));
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

        if (containSpace(etShortName.getText().toString())) {
            new Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_invalid));
            return false;
        }

        if (containSpace(etAssetsUnit.getText().toString())) {
            new Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_unit_invalid));
            return false;
        }

        if (!checkAssetNameExist()) {
            return false;
        }

        if (!checkShortNameExist()) {
            return false;
        }

        if (!checkAssetUnitExist()) {
            return false;
        }

        String totalAmount = etTotalAmount.getText().toString().trim();

        if (new BigDecimal(totalAmount).compareTo(new BigDecimal(100)) < 0) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_total_amount_too_small));
            return false;
        }

        String decimalStr = etDecimals.getText().toString().trim();
        long decimals = Long.parseLong(decimalStr);
        if (decimals < 4) {
            DialogBuilder dialog = new DialogBuilder(getActivity());
            dialog.setTitle(R.string.safe_comm_title);
            dialog.setMessage(R.string.safe_hint_decimals_too_small);
            dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    etDecimals.setText("4");
                    tvIssue.performClick();
                }
            });
            dialog.setNegativeButton(R.string.button_cancel, null);
            dialog.setCancelable(false);
            dialog.show();
            return false;
        }

        if (totalAmount.endsWith(".")) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_total_amount_or_decimal_mismatch));
            etTotalAmount.requestFocus();
            return false;
        }

        totalAmount = new BigDecimal(totalAmount).stripTrailingZeros().toPlainString();
        int frontLength = totalAmount.indexOf(".");
        if (frontLength < 0) {
            frontLength = totalAmount.length();
        } else {
            int behindLength = totalAmount.substring(frontLength + 1).length();
            if (behindLength > decimals) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_total_amount_or_decimal_mismatch));
                etTotalAmount.requestFocus();
                return false;
            }
        }

        String firstAmount = etFirstIssueAmount.getText().toString().trim();
        if (firstAmount.endsWith(".")) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_first_amount_or_decimal_mismatch));
            etFirstIssueAmount.requestFocus();
            return false;
        }

        firstAmount = new BigDecimal(firstAmount).stripTrailingZeros().toPlainString();
        if (firstAmount.indexOf(".") > 0) {
            int behindLength = firstAmount.substring(firstAmount.indexOf(".") + 1).length();
            if (behindLength > decimals) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_first_amount_or_decimal_mismatch));
                etFirstIssueAmount.requestFocus();
                return false;
            }
        }

        if (frontLength + decimals == 19) {
            BigDecimal totalDecimals = new BigDecimal(totalAmount);
            BigDecimal realDecimals = new BigDecimal(SafeUtils.getRealDecimals(decimals));
            BigDecimal totalAmountDecimal = totalDecimals.multiply(realDecimals);
            if (totalAmountDecimal.compareTo(new BigDecimal(2000000000000000000L)) > 0) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_total_amount_over_max_value));
                return false;
            }
        }

        if (frontLength + decimals > 19) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_total_amount_too_big));
            etTotalAmount.requestFocus();
            return false;
        }

        String firstIssueAmount = etFirstIssueAmount.getText().toString();
        BigDecimal firstDeciaml = new BigDecimal(firstIssueAmount);
        if (firstDeciaml.compareTo(new BigDecimal(100)) < 0) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_first_issue_amount_invalid));
            etFirstIssueAmount.requestFocus();
            return false;
        }

        if (firstDeciaml.compareTo(new BigDecimal(totalAmount)) > 0) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_first_issue_amount_too_big));
            etFirstIssueAmount.requestFocus();
            return false;
        }

        String assetDescStr = etAssetDesc.getText().toString().trim();

        if (assetDescStr.length() == 0) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_desc_empty));
            return false;
        }

        try {
            if (assetDescStr.getBytes("UTF-8").length > 300) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_desc_invalid));
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
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

        if (cbPayCandy.isChecked()) {

            String candyAmount = tvCandyAmount.getText().toString();
            BigDecimal firstIssueDecimal = new BigDecimal(firstIssueAmount);
            BigDecimal candyDecimal = new BigDecimal(candyAmount);
            if (firstIssueDecimal.compareTo(candyDecimal) < 0) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_first_candy_amount_too_big));
                return false;
            }

            BigDecimal diffDecimal = firstIssueDecimal.subtract(candyDecimal);
            if (diffDecimal.longValue() < 100) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_first_issue_amount_invalid));
                etFirstIssueAmount.requestFocus();
                return false;
            }

        }

        return true;
    }

    public SafeProtos.IssueData getIssueProtos() {
        String assetName = etAssetName.getText().toString().trim();
        String shortName = etShortName.getText().toString().trim();
        String assetUnit = etAssetsUnit.getText().toString().trim();
        long decimals = Long.parseLong(etDecimals.getText().toString());
        String totalAmount = etTotalAmount.getText().toString();
        BigDecimal totalDecimal = new BigDecimal(totalAmount);
        long totalAmountLong = totalDecimal.multiply(new BigDecimal(SafeUtils.getRealDecimals(decimals))).longValue();
        String firstIssueAmount = etFirstIssueAmount.getText().toString();
        BigDecimal firstDecimal = new BigDecimal(firstIssueAmount);
        long firstAmountLong = firstDecimal.multiply(new BigDecimal(SafeUtils.getRealDecimals(decimals))).longValue();
        String assetsDesc = etAssetDesc.getText().toString().trim();
        String remarks = etRemarks.getText().toString().trim();
        if (TextUtils.isEmpty(remarks)) {
            remarks = "";
        }
        boolean canDesroty = cbCanDestory.isChecked();
        boolean payCandy = cbPayCandy.isChecked();
        long candyExpired = 0;
        long candyAmountLong = 0;
        if (payCandy) {
            candyExpired = Long.parseLong(etCandyExpired.getText().toString());
            String candyAmount = tvCandyAmount.getText().toString();
            BigDecimal candyDecimal = new BigDecimal(candyAmount);
            BigDecimal realCandyDecimal = candyDecimal.multiply(new BigDecimal(SafeUtils.getRealDecimals(decimals)));
            candyAmountLong = realCandyDecimal.longValue();
        }
        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        byte[] decimalsByte = new byte[1];
        Utils.uint8ToByteArrayLE(decimals, decimalsByte, 0);

        byte[] candyExpiredByte = new byte[2];
        Utils.uint16ToByteArrayLE(candyExpired, candyExpiredByte, 0);

        return SafeProtos.IssueData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAssetName(ByteString.copyFromUtf8(assetName))
                .setShortName(ByteString.copyFromUtf8(shortName))
                .setAssetUnit(ByteString.copyFromUtf8(assetUnit))
                .setTotalAmount(totalAmountLong)
                .setFirstIssueAmount(firstAmountLong)
                .setFirstActualAmount(firstAmountLong - candyAmountLong)
                .setDecimals(ByteString.copyFrom(decimalsByte))
                .setAssetDesc(ByteString.copyFromUtf8(assetsDesc))
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .setDestory(canDesroty)
                .setPayCandy(payCandy)
                .setCandyExpired(ByteString.copyFrom(candyExpiredByte))
                .setCandyAmount(candyAmountLong)
                .build();
    }

    public SafeProtos.PutCandyData getCandyProtos(String assetId) {
        long decimals = Long.parseLong(etDecimals.getText().toString());
        long candyExpired = Long.parseLong(etCandyExpired.getText().toString());
        String candyAmount = tvCandyAmount.getText().toString().trim();
        BigDecimal candyDecimal = new BigDecimal(candyAmount);
        BigDecimal realCandyDecimal = candyDecimal.multiply(new BigDecimal(SafeUtils.getRealDecimals(decimals)));
        long candyAmountLong = realCandyDecimal.longValue();

        String remarks = etRemarks.getText().toString().trim();

        byte[] versionByte = new byte[2];
        Utils.uint16ToByteArrayLE(1, versionByte, 0);

        byte[] candyExpiredByte = new byte[2];
        Utils.uint16ToByteArrayLE(candyExpired, candyExpiredByte, 0);

        return SafeProtos.PutCandyData.newBuilder()
                .setVersion(ByteString.copyFrom(versionByte))
                .setAmount(candyAmountLong)
                .setAssetId(ByteString.copyFrom(SafeUtils.assetIdToHash256(assetId)))
                .setRemarks(ByteString.copyFromUtf8(remarks))
                .setExpired(ByteString.copyFrom(candyExpiredByte))
                .build();
    }

    public boolean checkAssetNameExist() {
        String assetName = etAssetName.getText().toString().trim();
        if (assetName.length() == 0) {
            new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_empty));
            return false;
        }

        try {
            if (assetName.getBytes("UTF-8").length > 20) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_invalid));
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        for (String item : filterKeyWord) {
            if (item.equalsIgnoreCase(assetName)) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_tip_disable));
                return false;
            }
        }
        for (String item : filterSimilarKeyWord) {
            if (assetName.contains(item)) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_tip_disable));
                return false;
            }
        }
        BaseDaoImpl issueDao = new BaseDaoImpl(IssueData.class);
        try {
            GenericRawResults<String[]> rawResults = issueDao.getDao().queryRaw("select * from tb_issue where LOWER(assetName) = ?", new String[]{assetName.toLowerCase()});
            List<String[]> results = rawResults.getResults();
            if (results.size() > 0) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_name_disable));
                return false;
            } else {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean checkShortNameExist() {
        String shortName = etShortName.getText().toString().trim();
        try {
            if (shortName.getBytes("UTF-8").length > 20) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_short_name_invalid));
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        for (String item : filterKeyWord) {
            if (item.equalsIgnoreCase(shortName)) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_short_name_str_disable));
                return false;
            }
        }
        for (String item : filterSimilarKeyWord) {
            if (shortName.contains(item)) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_short_name_str_disable));
                return false;
            }
        }
        BaseDaoImpl issueDao = new BaseDaoImpl(IssueData.class);
        try {
            GenericRawResults<String[]> rawResults = issueDao.getDao().queryRaw("select * from tb_issue where LOWER(shortName) = ?", new String[]{shortName.toLowerCase()});
            List<String[]> results = rawResults.getResults();
            if (results.size() > 0) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_short_name_disable));
                return false;
            } else {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean checkAssetUnitExist() {
        String assetUnit = etAssetsUnit.getText().toString().trim();
        try {
            if (assetUnit.getBytes("UTF-8").length > 10) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_unit_invalid));
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        for (String item : filterKeyWord) {
            if (item.equalsIgnoreCase(assetUnit)) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_unit_str_disable));
                return false;
            }
        }
        for (String item : filterSimilarKeyWord) {
            if (assetUnit.contains(item)) {
                new de.schildbach.wallet.util.Toast(getActivity()).shortToast(getString(R.string.safe_hint_asset_unit_str_disable));
                return false;
            }
        }
        return true;
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

    public void scrollToBottom(final View scroll, final View inner) {
        getHandler().post(new Runnable() {
            public void run() {
                if (scroll == null || inner == null) {
                    return;
                }
                int offset = inner.getMeasuredHeight() - scroll.getHeight();
                if (offset < 0) {
                    offset = 0;
                }
                scroll.scrollTo(0, offset);
            }
        });
    }

    /**
     * 包括空格判断
     *
     * @param input
     * @return
     */
    private boolean containSpace(CharSequence input) {
        return Pattern.compile("\\s+").matcher(input).find();
    }

    @Override
    public void isSyncFinish() {

    }

}
