package de.schildbach.wallet.ui.safe.utils;

import android.text.TextUtils;

import com.google.common.util.concurrent.Uninterruptibles;
import com.j256.ormlite.dao.GenericRawResults;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.*;
import de.schildbach.wallet.ui.safe.bean.Coinbase;
import de.schildbach.wallet.ui.safe.bean.SafeReserve;

import org.bitcoin.safe.SafeProtos;
import org.bitcoin.safe.SposProtos;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.SafeConstant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SignatureException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 处理Safe公用方法
 *
 * @author zhangmiao
 */
public class SafeUtils {

    public static final Logger log = LoggerFactory.getLogger(SafeUtils.class);

    private static BaseDaoImpl issueDao = new BaseDaoImpl(IssueData.class);

    private static BaseDaoImpl putCandyDao = new BaseDaoImpl(PutCandyData.class);

    private static BaseDaoImpl getCandydao = new BaseDaoImpl(GetCandyData.class);

    private static BaseDaoImpl candyAddrDao = new BaseDaoImpl(CandyAddrData.class);

    private static BaseDaoImpl totalDao = new BaseDaoImpl(TotalAmountData.class);

    private static BaseDaoImpl filterDao = new BaseDaoImpl(FilterAmountData.class);

    private static BaseDaoImpl assetTxDao = new BaseDaoImpl(WalletAssetTx.class);

    private static BaseDaoImpl getCandyAmountDao = new BaseDaoImpl(GetCandyAmountData.class);

    private static Coinbase mCoinbase = null;

    //封装预留字段
    public static byte[] serialReserve(int appCommand, String appId, byte[] protos) throws Exception {
        ByteArrayOutputStream stream = new UnsafeByteArrayOutputStream();
        byte[] safeFlag = SafeConstant.SAFE_FLAG.getBytes();
        stream.write(safeFlag);
        Utils.uint16ToByteStreamLE(SafeConstant.RESERVE_HEADER_VERSION, stream);
        stream.write(Sha256Hash.wrap(appId).getReversedBytes());
        Utils.uint32ToByteStreamLE(appCommand, stream);
        stream.write(protos);
        return stream.toByteArray();

    }

    //解析预留字段
    public static SafeReserve parseReserve(byte[] vReserve) {
        SafeReserve mReserve = new SafeReserve();
        if (vReserve == null || vReserve.length == 0) return mReserve;
        int cursor = 0;
        int length = 4;
        mReserve.safeFlag = new String(readBytes(vReserve, cursor, length));
        if (vReserve.length > 4) {
            cursor += length;
            String spos = new String(readBytes(vReserve, cursor, 4));
            if (spos.equals("spos")) {
                mReserve.safeFlag = spos;
                mReserve.isSposFlag = true;
                cursor += 4;
                length = 2;
                long version = Utils.readUint16(vReserve, cursor);
                cursor += length;
                mReserve.version = (int) version;
                length = vReserve.length - cursor;
                mReserve.protos = readBytes(vReserve, cursor, length);
            } else {
                mReserve.isSposFlag = false;
                length = 2;
                long version = Utils.readUint16(vReserve, cursor);
                cursor += length;
                mReserve.version = (int) version;
                length = 32;
                mReserve.appId = SafeUtils.hash256ToAppId(readBytes(vReserve, cursor, length));
                cursor += length;
                length = 4;
                long appCommand = Utils.readUint32(vReserve, cursor);
                mReserve.appCommand = (int) appCommand;
                cursor += length;
                length = vReserve.length - cursor;
                mReserve.protos = readBytes(vReserve, cursor, length);
            }
        }
        return mReserve;
    }

    private static boolean validSignMessage(byte[] keyId, byte[] originalData, byte[] signData) {
        ECKey ecKey = null;
        try {
            ecKey = ECKey.signedMessageToKey(originalData, signData);
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        if (ecKey == null) {
            return false;
        }

        if (Arrays.equals(keyId, ecKey.getPubKeyHash())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 验证SPOS预留字段
     *
     * @param vReserve
     * @return
     */
    public static  void validSposCoinbase(byte[] vReserve) {
        int cursor = 0;
        int length = 8;
        byte[] sposFlag = readBytes(vReserve, cursor, length);
        if (!"safespos".equals(new String(sposFlag))) {
            throw new VerificationException("SPOS_CoinBase_Reserve_Error, safespos begin");
        }
        cursor += length;
        length = 2;
        long version = Utils.readUint16(vReserve, cursor);
        if (version == 1) {
            cursor += length;
            length = 20;
            byte[] keyId = readBytes(vReserve, cursor, length);

            cursor += length;
            length = vReserve.length - cursor;
            byte[] originalData = readBytes(vReserve, 0, cursor);
            byte[] signData = readBytes(vReserve, cursor, length);
            if (!validSignMessage(keyId, originalData, signData)) {
                throw new VerificationException("SPOS_CoinBase_Reserve_Error, invalid spos sign message");
            }
        } else if (version == 2) {
            SafeReserve mReserve = parseReserve(vReserve);
            SposProtos.DeterministicCoinbaseData protos = mReserve.getDeterministicCoinbaseProtos();
            if (protos != null) {
                byte[] keyId = protos.getPubkeyId().toByteArray();
                long officialMNNum = Utils.readUint16(protos.getOfficialMNNum().toByteArray(), 0);
                long generalMNNum = Utils.readUint16(protos.getGeneralMNNum().toByteArray(), 0);
                long randomNum = Utils.readUint32(protos.getRandomNum().toByteArray(), 0);
                long firstBlock = Utils.readUint16(protos.getFirstBlock().toByteArray(), 0);
                try {
                    ByteArrayOutputStream stream = new UnsafeByteArrayOutputStream();
                    stream.write(protos.getOfficialMNNum().toByteArray());
                    stream.write(protos.getGeneralMNNum().toByteArray());
                    stream.write(protos.getRandomNum().toByteArray());
                    stream.write(protos.getFirstBlock().toByteArray());
                    byte[] originalData = stream.toByteArray();
                    byte[] signData = protos.getSignMsg().toByteArray();
                    if (!validSignMessage(keyId, originalData, signData)) {
                        throw new VerificationException("SPOS_Deterministic_CoinBase_Reserve_Error, invalid sign message");
                    }
                    if (firstBlock == 1L) {
                        mCoinbase = new Coinbase();
                        mCoinbase.firstBlock = firstBlock;
                        mCoinbase.officialMNNum = officialMNNum;
                        mCoinbase.generalMNNum = generalMNNum;
                        mCoinbase.randomNum = randomNum;
                    } else {
                        if (mCoinbase != null) {
                            if (mCoinbase.officialMNNum != officialMNNum || mCoinbase.generalMNNum != generalMNNum || mCoinbase.randomNum != randomNum) {
                                throw new VerificationException(String.format("SPOS_Deterministic_CoinBase_Reserve_Error, invalid coinbase num %d-%d, %d-%d, %d-%d", mCoinbase.officialMNNum, officialMNNum, mCoinbase.generalMNNum, generalMNNum, mCoinbase.randomNum, randomNum));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new VerificationException("SPOS_Deterministic_CoinBase_Reserve_Error, " + e.getMessage());
                }
            } else {
                throw new VerificationException("SPOS_Deterministic_CoinBase_Reserve_Error, invalid deterministic Coinbase proto");
            }
        } else {
            throw new VerificationException("SPOS_CoinBase_Reserve_Error, invalid version");
        }
    }

    public static synchronized void saveFilterAmount(Transaction tx, int height) throws Exception {
        if (tx.isCoinBase()) {
            return;
        }
        long calcFilterAmount = 0;
        for (int i = 0; i < tx.getOutputs().size(); i++) {
            TransactionOutput output = tx.getOutputs().get(i);
            if (output.isSafeTx()) { //销毁SAFE黑洞地址
                Address filterAddress = output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS);
                if (filterAddress != null && SafeConstant.FILTER_BLACK_HOLE_ADDRESS.contains(filterAddress.toBase58())) {
                    calcFilterAmount += output.getValue().getValue();
                }
            }
        }
        if (Constants.TEST) {
            if (calcFilterAmount > 0 && height > SafeConstant.getSafeBranchHeight()) {
                SafeUtils.setFilterAmount(tx.getHashAsString(), height, calcFilterAmount);
            }
        } else { //正式环境已经包含销毁金额
            if (calcFilterAmount > 0 && height > SafeConstant.getBlockAmountHeight()) {
                SafeUtils.setFilterAmount(tx.getHashAsString(), height, calcFilterAmount);
            }
        }
    }

    public static synchronized void saveIssueAndCandy(Transaction tx, long time, int height) throws Exception {
        if (tx.isCoinBase()) {
            return;
        }
        long calcCandyAmount = 0;
        for (int i = 0; i < tx.getOutputs().size(); i++) {
            TransactionOutput output = tx.getOutputs().get(i);
            SafeReserve vReserve = output.getSafeReserve();
            if (vReserve.isIssue()) { //发行资产
                String txId = tx.getHashAsString();
                SafeProtos.IssueData protoIssue = vReserve.getIssueProtos();
                if (protoIssue != null) {
                    String assetId = generateAssetId(vReserve.getIssueProtos());
                    if (issueDao.queryForFirst("assetId", assetId) == null) {
                        IssueData issue = new IssueData();
                        issue.assetId = assetId;
                        issue.appId = vReserve.appId;
                        issue.version = Utils.readUint16(protoIssue.getVersion().toByteArray(), 0);
                        issue.shortName = protoIssue.getShortName().toStringUtf8();
                        issue.assetName = protoIssue.getAssetName().toStringUtf8();
                        issue.assetDesc = protoIssue.getAssetDesc().toStringUtf8();
                        issue.assetUnit = protoIssue.getAssetUnit().toStringUtf8();
                        issue.totalAmount = protoIssue.getTotalAmount();
                        issue.firstIssueAmount = protoIssue.getFirstIssueAmount();
                        issue.firstActualAmount = protoIssue.getFirstActualAmount();
                        issue.decimals = Utils.readUint8(protoIssue.getDecimals().toByteArray(), 0);
                        issue.destory = protoIssue.getDestory();
                        issue.payCandy = protoIssue.getPayCandy();
                        issue.candyAmount = protoIssue.getCandyAmount();
                        issue.candyExpired = Utils.readUint16(protoIssue.getVersion().toByteArray(), 0);
                        issue.remarks = protoIssue.getRemarks().toStringUtf8();
                        issue.txId = txId;
                        issueDao.save(issue);
                    }
                }
            } else if (vReserve.isPutCandy()) { //糖果资产
                String txId = tx.getHashAsString();
                if (putCandyDao.queryForFirst("txId", txId) == null) {
                    SafeProtos.PutCandyData protoCandy = vReserve.getPutCandyProtos();
                    String assetId = hash256ToAssetId(protoCandy.getAssetId().toByteArray());
                    PutCandyData candy = new PutCandyData();
                    candy.txId = txId;
                    candy.assetId = assetId;
                    candy.version = Utils.readUint16(protoCandy.getVersion().toByteArray(), 0);
                    candy.candyAmount = protoCandy.getAmount();
                    candy.candyExpired = Utils.readUint16(protoCandy.getExpired().toByteArray(), 0);
                    candy.remarks = protoCandy.getRemarks().toStringUtf8();
                    candy.txTime = time;
                    candy.height = height;
                    candy.outputIndex = i;
                    candy.calcFinishFlag = PutCandyData.UN_CACL;
                    candy.candyGetFlag = PutCandyData.STATUS_DISENABLED;
                    putCandyDao.save(candy);
                }
            } else if(vReserve.isGetCandy()) {
                SafeProtos.GetCandyData protos = vReserve.getGetCandyProtos();
                calcCandyAmount += protos.getAmount();
            }
//            else if (vReserve.isSposMasternodeDeterministic()) {
//                SposProtos.DeterministicMasternodeData protos = vReserve.getDeterministicMasternodeProtos();
//                ByteArrayOutputStream stream = new UnsafeByteArrayOutputStream();
//                stream.write(protos.getIp().toByteArray());
//                stream.write(protos.getPort().toByteArray());
//                stream.write(protos.getCollateralAddress().toByteArray());
//                stream.write(protos.getTxid().toByteArray());
//                stream.write(protos.getOutputIndex().toByteArray());
//                stream.write(protos.getSerialPubkeyId().toByteArray());
//                byte[] originalData = stream.toByteArray();
//                byte[] signData = protos.getSignedMsg().toByteArray();
//                validSignMessage(protos.getSerialPubkeyId().toByteArray(), originalData, signData);
//            }
        }
        String txId = tx.getHashAsString();
        if (getCandyAmountDao.queryForFirst("txId", txId) == null) {
            TransactionInput input = tx.getLastInput();
            String putCandyTxId = input.getOutpoint().getHash().toString();
            GetCandyAmountData candyAmount = new GetCandyAmountData();
            candyAmount.txId = txId;
            candyAmount.putCandyTxId = putCandyTxId;
            candyAmount.totalAmount = calcCandyAmount;
            getCandyAmountDao.save(candyAmount);
        }
    }

    public static synchronized void saveCandyAddrData(PutCandyData candy, int height, long totalAmount, long filterAmount, Map<String, Coin> banlace) throws Exception {
        String txId = candy.txId;
        String assetId = candy.assetId;
        List<CandyAddrData> candyAddrList = new ArrayList<>();
        for (Map.Entry<String, Coin> entry : banlace.entrySet()) {
            String address = entry.getKey();
            Coin value = entry.getValue();
            List resultList = candyAddrDao.query(new String[]{"txId", "address", "height"}, new Object[]{txId, address, Integer.toString(height)});
            if (resultList == null || resultList.size() == 0) {
                long decimals = SafeUtils.getAssetIdDecimals(assetId);
                long enableTotalAmount = totalAmount - filterAmount;
//                log.info("--------height = {}, safeAmount = {}, enableAmount = {}, totalCandyAmount = {}, address = {}", height, value.getValue(), enableTotalAmount, candy.candyAmount, address);
                if (filterCandy(value.getValue(), enableTotalAmount, candy.candyAmount, decimals)) {
                    CandyAddrData candyAddr = new CandyAddrData();
                    candyAddr.txId = txId;
                    candyAddr.value = value.getValue();
                    candyAddr.address = address;
                    candyAddr.height = height;
                    candyAddrList.add(candyAddr);
                }
            }
        }
        if (candyAddrList.size() > 0) {
            candyAddrDao.save(candyAddrList);
            //标记成可领取
            updateCandy("update tb_put_candy set candyGetFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.STATUS_ENABLED), candy.txId});
        }
    }

    public static synchronized boolean filterCandy(long safeAmount, long enableAmount, long totalCandyAmount, long decimals) {
        long candyAmount = (long) (1.0 * safeAmount / enableAmount * totalCandyAmount);
        BigDecimal candyDecimals = new BigDecimal(candyAmount);
        BigDecimal realDecimals = new BigDecimal(SafeUtils.getRealDecimals(decimals));
        BigDecimal formatCandy = candyDecimals.divide(realDecimals);
        return formatCandy.compareTo(new BigDecimal("0.0001")) >= 0;
    }

    /**
     * 保存总金额表
     */
    public static synchronized void setTotalAmount(int height, long value) {
        try {
            if (totalDao.queryForFirst("height", height) == null) {
                TotalAmountData totalAmount = new TotalAmountData();
                totalAmount.height = height;
                totalAmount.totalAmount = value;
                totalDao.save(totalAmount);
            }
        } catch (SQLException e) {
            setTotalAmount(height, value);
        }
    }

    /**
     * 保存过滤金额表
     */
    public static synchronized void setFilterAmount(String txId, int height, long value) {
        try {
            if (filterDao.queryForFirst("txId", txId) == null) {
                FilterAmountData filterAmount = new FilterAmountData();
                filterAmount.txId = txId;
                filterAmount.height = height;
                filterAmount.filterAmount = value;
                int count = filterDao.save(filterAmount);
//                log.info("-----------height = {}, value = {}, count = {}", height, value, count);
            }
        } catch (SQLException e) {
            setFilterAmount(txId, height, value);
        }
    }

    public static long getTotalAmount(long height) {
        long totalAmount = 0;
        try {
            GenericRawResults<String[]> rawResults = totalDao.getDao().queryRaw("select sum(totalAmount) from tb_total_amount where height <= ?", new String[]{Long.toString(height)});
            List<String[]> listResults = rawResults.getResults();
            String[] arr = listResults.get(0);
            String totalStr = arr[0];
            if (!TextUtils.isEmpty(totalStr)) {
                totalAmount = Long.parseLong(totalStr);
            }
        } catch (SQLException e) {

        }
        return totalAmount;
    }

    public static long getFilterAmount(long height) {
        long filterAmount = 0;
        try {
            GenericRawResults<String[]> rawResults = filterDao.getDao().queryRaw("select sum(filterAmount) from tb_filter_amount where height <= ?", new String[]{Long.toString(height)});
            List<String[]> listResults = rawResults.getResults();
            String[] arr = listResults.get(0);
            String totalStr = arr[0];
            if (!TextUtils.isEmpty(totalStr)) {
                filterAmount = Long.parseLong(totalStr);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return filterAmount;
    }

    public static long getAssetIdDecimals(String assetId) {
        long decimals = 0;
        try {
            IssueData issue = (IssueData) issueDao.queryForFirst("assetId", assetId);
            decimals = issue.decimals;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return decimals;
    }


    public static long getCandyAmount(String putCandyTxId) {
        long candyAmount = 0;
        try {
            GenericRawResults<String[]> rawResults = getCandyAmountDao.getDao().queryRaw("select sum(totalAmount) from tb_get_candy_amount where putCandyTxId = ?", new String[]{ putCandyTxId });
            List<String[]> listResults = rawResults.getResults();
            String[] arr = listResults.get(0);
            String totalStr = arr[0];
            if (!TextUtils.isEmpty(totalStr)) {
                candyAmount = Long.parseLong(totalStr);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return candyAmount;
    }

    /**
     * 保存当前钱包的发送和接受的资产交易
     *
     * @param tx
     */
    public static synchronized void saveMeWalletAssetTx(Transaction tx) throws Exception {
        for (TransactionOutput output : tx.getOutputs()) {
            SafeReserve vReserve = SafeUtils.parseReserve(output.getReserve());
            if (vReserve.isIssue()) { //发行
                String txId = tx.getHashAsString();
                String assetId = generateAssetId(vReserve.getIssueProtos());
                SafeProtos.IssueData protoIssue = vReserve.getIssueProtos();
                if (protoIssue != null) {
                    if (issueDao.queryForFirst("assetId", assetId) == null) {
                        IssueData issue = new IssueData();
                        issue.assetId = assetId;
                        issue.appId = vReserve.appId;
                        issue.version = Utils.readUint16(protoIssue.getVersion().toByteArray(), 0);
                        issue.shortName = protoIssue.getShortName().toStringUtf8();
                        issue.assetName = protoIssue.getAssetName().toStringUtf8();
                        issue.assetDesc = protoIssue.getAssetDesc().toStringUtf8();
                        issue.assetUnit = protoIssue.getAssetUnit().toStringUtf8();
                        issue.totalAmount = protoIssue.getTotalAmount();
                        issue.firstIssueAmount = protoIssue.getFirstIssueAmount();
                        issue.firstActualAmount = protoIssue.getFirstActualAmount();
                        issue.decimals = Utils.readUint8(protoIssue.getDecimals().toByteArray(), 0);
                        issue.destory = protoIssue.getDestory();
                        issue.payCandy = protoIssue.getPayCandy();
                        issue.candyAmount = protoIssue.getCandyAmount();
                        issue.candyExpired = Utils.readUint16(protoIssue.getVersion().toByteArray(), 0);
                        issue.remarks = protoIssue.getRemarks().toStringUtf8();
                        issue.txId = txId;
                        issueDao.save(issue);
                    }
                }

                List resultList = assetTxDao.query(new String[]{"txId", "assetId", "appCommand"}, new Object[]{txId, assetId, vReserve.appCommand});
                if (resultList == null || resultList.size() == 0) {
                    WalletAssetTx assetTx = new WalletAssetTx();
                    assetTx.txId = txId;
                    assetTx.assetId = assetId;
                    assetTx.appCommand = vReserve.appCommand;
                    assetTx.amount = protoIssue.getFirstActualAmount();
                    assetTxDao.save(assetTx);
                }
                continue;
            } else if (vReserve.isAddIssue() || vReserve.isTransfer() || vReserve.isAssetChange()) { //追加、转让、资产找零
                SafeProtos.CommonData protos = vReserve.getCommonProtos();
                if (protos != null) {
                    String txId = tx.getHashAsString();
                    String assetId = hash256ToAssetId(protos.getAssetId().toByteArray());
                    List resultList = assetTxDao.query(new String[]{"txId", "assetId", "appCommand"}, new Object[]{txId, assetId, vReserve.appCommand});
                    if (resultList == null || resultList.size() == 0) {
                        WalletAssetTx assetTx = new WalletAssetTx();
                        assetTx.txId = txId;
                        assetTx.assetId = assetId;
                        assetTx.appCommand = vReserve.appCommand;
                        assetTx.amount = protos.getAmount();
                        assetTxDao.save(assetTx);
                    }
                }
                //TODO 发放糖果如果余额没有全部发放，该交易会有一个资产找零的输出，会在上一个if中将该交易添加一次，这里再添加一次会导致该表中这笔交易重复，使用时应有去重操作
            } else if (vReserve.isPutCandy()) {
                SafeProtos.PutCandyData protos = vReserve.getPutCandyProtos();
                if (protos != null) {
                    String txId = tx.getHashAsString();
                    String assetId = hash256ToAssetId(protos.getAssetId().toByteArray());
                    List resultList = assetTxDao.query(new String[]{"txId", "assetId", "appCommand"}, new Object[]{txId, assetId, vReserve.appCommand});
                    if (resultList == null || resultList.size() == 0) {
                        WalletAssetTx assetTx = new WalletAssetTx();
                        assetTx.txId = txId;
                        assetTx.assetId = assetId;
                        assetTx.appCommand = vReserve.appCommand;
                        assetTx.amount = protos.getAmount();
                        assetTxDao.save(assetTx);
                    }
                }
            } else if (vReserve.isGetCandy()) {
                SafeProtos.GetCandyData protos = vReserve.getGetCandyProtos();
                if (protos != null) {
                    String txId = tx.getHashAsString();
                    String assetId = hash256ToAssetId(protos.getAssetId().toByteArray());
                    List resultList = assetTxDao.query(new String[]{"txId", "assetId", "appCommand"}, new Object[]{txId, assetId, vReserve.appCommand});
                    if (resultList == null || resultList.size() == 0) {
                        WalletAssetTx assetTx = new WalletAssetTx();
                        assetTx.txId = txId;
                        assetTx.assetId = assetId;
                        assetTx.appCommand = vReserve.appCommand;
                        assetTx.amount = protos.getAmount();
                        assetTxDao.save(assetTx);
                    }
                    //保存领取糖果记录
                    String address = output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS).toBase58();
                    resultList = getCandydao.query(new String[]{"txId", "address"}, new Object[]{txId, address});
                    if (resultList == null || resultList.size() == 0) {
                        List<TransactionInput> intputs = tx.getInputs();
                        TransactionInput input = tx.getInput(intputs.size() - 1);
                        TransactionOutPoint outPoint = input.getOutpoint();
                        GetCandyData candyData = new GetCandyData();
                        candyData.txId = txId;
                        candyData.inTxId = outPoint.getHash().toString();
                        candyData.version = Utils.readUint16(protos.getVersion().toByteArray(), 0);
                        candyData.assetId = assetId;
                        candyData.candyAmount = protos.getAmount();
                        candyData.remarks = protos.getRemarks().toStringUtf8();
                        candyData.address = output.getAddressFromP2PKHScript(Constants.NETWORK_PARAMETERS).toBase58();
                        getCandydao.save(candyData);
                    }
                }
                continue;
            } else {
                continue;
            }
        }
    }

    public static long getLockHeight(int month) {
        long lockHeight = 0;
        if (month > 0) {
            if(SafeConstant.getLastBlockHeight() > Constants.NETWORK_PARAMETERS.getStartSposHeight()) {
                lockHeight = SafeConstant.getLastBlockHeight() + 1 + SafeConstant.SPOS_BLOCKS_PER_MONTH * month;
            } else {
                lockHeight = SafeConstant.getLastBlockHeight() + 1 + SafeConstant.BLOCKS_PER_MONTH * month;
            }
        }
        return lockHeight;
    }

    public static Coin getCancelledAmount() {
        if (SafeConstant.getLastBlockHeight() > Constants.NETWORK_PARAMETERS.getStartSposHeight()) { //采用SPOS

            int h1 = Constants.NETWORK_PARAMETERS.getStartSposHeight();
            int h2 = SafeConstant.getDashDisabledHeight();
            int d1 = (h1 - h2) /  576;
            int d2 = (SafeConstant.getLastBlockHeight() - h1) / 2880;
            int nTotalMonth = (d1 + d2) / 30;

            double nLeft = 500.00;
            for (int i = 1; i <= nTotalMonth; i++) {
                nLeft *= 0.95;
                int thirdData;
                thirdData = (int) (nLeft * 1000) % 100 % 10;
                if (thirdData > 4)
                    nLeft = (double) ((int) (nLeft * 100) + 1) / 100;
                else {
                    if (thirdData == 4) {
                        int fourthData;
                        fourthData = (int) (nLeft * 10000) % 1000 % 100 % 10;
                        if (fourthData > 4)
                            nLeft = (double) ((int) (nLeft * 100) + 1) / 100;
                        else
                            nLeft = (double) ((int) (nLeft * 100)) / 100;
                    } else
                        nLeft = (double) ((int) (nLeft * 100)) / 100;
                }
                if (nLeft < 50)
                    nLeft = 50.00;
            }
            BigDecimal decimal1 = new BigDecimal(String.valueOf(nLeft));
            BigDecimal decimal2 = new BigDecimal("100000000");
            return Coin.valueOf(decimal1.multiply(decimal2).longValue());

        } else { //采用POW

            int nOffset = SafeConstant.getLastBlockHeight() - SafeConstant.getDashDisabledHeight();
            if (nOffset < 0)
                return Coin.valueOf(0);
            int nMonth = nOffset / SafeConstant.BLOCKS_PER_MONTH;
            if (nMonth == 0)
                return Coin.valueOf(50000000000L);
            double nLeft = 500.00;
            for (int i = 1; i <= nMonth; i++) {
                nLeft *= 0.95;
                int thirdData;
                thirdData = (int) (nLeft * 1000) % 100 % 10;
                if (thirdData > 4)
                    nLeft = (double) ((int) (nLeft * 100) + 1) / 100;
                else {
                    if (thirdData == 4) {
                        int fourthData;
                        fourthData = (int) (nLeft * 10000) % 1000 % 100 % 10;
                        if (fourthData > 4)
                            nLeft = (double) ((int) (nLeft * 100) + 1) / 100;
                        else
                            nLeft = (double) ((int) (nLeft * 100)) / 100;
                    } else
                        nLeft = (double) ((int) (nLeft * 100)) / 100;
                }
                if (nLeft < 50)
                    nLeft = 50.00;
            }
            BigDecimal decimal1 = new BigDecimal(String.valueOf(nLeft));
            BigDecimal decimal2 = new BigDecimal("100000000");
            return Coin.valueOf(decimal1.multiply(decimal2).longValue());

        }
    }

    public static List<String> getFilterKeyWord() {
        List<String> filter = new ArrayList();
        filter.add("安资");
        filter.add("安聊");
        filter.add("安投");
        filter.add("安付");
        filter.add("安資");
        filter.add("SafeAsset");
        filter.add("SafeChat");
        filter.add("SafeVote");
        filter.add("SafePay");
        filter.add("anwang");
        filter.add("bankledger");
        filter.add("electionchain");
        filter.add("safenetspace");
        filter.add("darknetspace");
        filter.add("SAFE");
        filter.add("ELT");
        filter.add("DNC");
        filter.add("DNC2");
        filter.add("BTC");
        filter.add("ETH");
        filter.add("EOS");
        filter.add("LTC");
        filter.add("DASH");
        filter.add("ETC");
        filter.add("bitcoin");
        filter.add("ethereum");
        filter.add("LiteCoin");
        filter.add("Ethereum Classic");
//        filter.add("ethereumclassic");
        filter.add("人民币");
        filter.add("港元");
        filter.add("港币");
        filter.add("港幣");
        filter.add("澳门元");
        filter.add("澳门币");
        filter.add("新台币");
        filter.add("RMB");
        filter.add("CNY");
        filter.add("HKD");
        filter.add("MOP");
        filter.add("TWD");
        filter.add("人民幣");
        filter.add("澳門元");
        filter.add("澳門幣");
        filter.add("澳门幣");
        filter.add("新台幣");
        filter.add("mSAFE");
        filter.add("μSAFE");
        filter.add("duffs");
        filter.add("tSAFE");
        filter.add("mtSAFE");
        filter.add("μtSAFE");
        filter.add("tduffs");
        return filter;
    }

    public static List<String> getFilterSimilarKeyWord() {
        List<String> filter = new ArrayList();
        filter.add("安网");
        filter.add("安網");
        filter.add("银链");
        filter.add("銀链");
        filter.add("銀鏈");
        filter.add("银鏈");
        return filter;
    }

    /**
     * 生成资产ID，按照C++的生成规则来
     *
     * @param protoIssue
     * @return
     */
    public static String generateAssetId(SafeProtos.IssueData protoIssue) {
        ByteArrayOutputStream stream = new UnsafeByteArrayOutputStream();
        try {
            long totalAmount = protoIssue.getTotalAmount();
            long firstIssueAmount = protoIssue.getFirstIssueAmount();
            long firstActualAmount = protoIssue.getFirstActualAmount();
            long decimals = Utils.readUint8(protoIssue.getDecimals().toByteArray(), 0);
            boolean destory = protoIssue.getDestory();
            boolean payCandy = protoIssue.getPayCandy();
            long candyAmount = protoIssue.getCandyAmount();
            long candyExpired = Utils.readUint16(protoIssue.getCandyExpired().toByteArray(), 0);
            writeSerializeSize(stream, protoIssue.getShortName().toByteArray().length);
            stream.write(protoIssue.getShortName().toByteArray());
            writeSerializeSize(stream, protoIssue.getAssetName().toByteArray().length);
            stream.write(protoIssue.getAssetName().toByteArray());
            writeSerializeSize(stream, protoIssue.getAssetDesc().toByteArray().length);
            stream.write(protoIssue.getAssetDesc().toByteArray());
            writeSerializeSize(stream, protoIssue.getAssetUnit().toByteArray().length);
            stream.write(protoIssue.getAssetUnit().toByteArray());
            Utils.int64ToByteStreamLE(totalAmount, stream);
            Utils.int64ToByteStreamLE(firstIssueAmount, stream);
            Utils.int64ToByteStreamLE(firstActualAmount, stream);
            Utils.uint8ToByteStreamLE(decimals, stream);
            if (destory) {
                Utils.uint8ToByteStreamLE(1, stream);
            } else {
                Utils.uint8ToByteStreamLE(0, stream);
            }
            if (payCandy) {
                Utils.uint8ToByteStreamLE(1, stream);
            } else {
                Utils.uint8ToByteStreamLE(0, stream);
            }
            Utils.int64ToByteStreamLE(candyAmount, stream);
            Utils.uint16ToByteStreamLE(candyExpired, stream);
            writeSerializeSize(stream, protoIssue.getRemarks().toByteArray().length);
            stream.write(protoIssue.getRemarks().toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(stream.toByteArray())).toString(); //Transaction 用sha256反序 生成TxId一样的方法。
    }

    public static void writeSerializeSize(ByteArrayOutputStream stream, long nSize) throws Exception {
        if (nSize < 253) {
            Utils.uint8ToByteStreamLE(nSize, stream);
        } else if (nSize <= 0xFFFF) {
            Utils.uint8ToByteStreamLE(253, stream);
            Utils.uint16ToByteStreamLE(nSize, stream);
        } else if (nSize <= 0xFFFFFFFF) {
            Utils.uint8ToByteStreamLE(254, stream);
            Utils.uint32ToByteStreamLE(nSize, stream);
        } else {
            Utils.uint8ToByteStreamLE(255, stream);
            Utils.int64ToByteStreamLE(nSize, stream);
        }
    }

    public static byte[] readBytes(byte[] bytes, int cursor, int length) {
        byte[] b = new byte[length];
        System.arraycopy(bytes, cursor, b, 0, length);
        return b;
    }

    public static String hash256ToAppId(byte[] rawBytes) {
        return Sha256Hash.wrapReversed(rawBytes).toString();
    }


    /**
     * hash转资产Id
     *
     * @param rawBytes
     * @return
     */
    public static String hash256ToAssetId(byte[] rawBytes) {
        return Sha256Hash.wrapReversed(rawBytes).toString();
    }

    /**
     * 资产Id转hash
     *
     * @param assetId
     * @return
     */
    public static byte[] assetIdToHash256(String assetId) {
        return Sha256Hash.wrap(assetId).getReversedBytes();
    }

    public static long getRealDecimals(long decimals) {
        long ret = 1;
        for (int i = 0; i < decimals; i++) {
            ret = ret * 10;
        }
        return ret;
    }

    public static String getAssetAmount(Coin value, long decimals) {
        return getAssetAmount(value.getValue(), decimals);
    }

    public static String getAssetAmount(long value, long decimals) {
        return getAssetAmount(new BigDecimal(value), decimals);
    }

    public static String getAssetAmount(BigDecimal valueDecimal, long decimals) {
        BigDecimal realDecimal = new BigDecimal(SafeUtils.getRealDecimals(decimals));
        StringBuilder patternBuffer = new StringBuilder("0.");
        for (int i = 0; i < decimals; i++) {
            patternBuffer.append("0");
        }
        DecimalFormat format = new DecimalFormat(patternBuffer.toString());
        format.setRoundingMode(RoundingMode.FLOOR);

        return format.format(valueDecimal.divide(realDecimal));
    }

    public static void clearAssetDB() {
        try {
            issueDao.deleteAll();
            putCandyDao.deleteAll();
            getCandydao.deleteAll();
            candyAddrDao.deleteAll();
            totalDao.deleteAll();
            filterDao.deleteAll();
            assetTxDao.deleteAll();
            getCandyAmountDao.deleteAll();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void handleBlock(Block block, StoredBlock storedPrev, int height) {
        Coin value; //挖矿金额
        if (Constants.TEST) {
            if (height == SafeConstant.getSafeBranchHeight()) {
                value = Coin.valueOf(2100000000000000L);
            } else if (height > SafeConstant.getSafeBranchHeight()) {
                if (height >= Constants.NETWORK_PARAMETERS.getStartSposHeight()) { //采用SPOS
                    value = block.getSposBlockInflationCoin(height, false);
                } else { //采用POW
                    //这里不用coinBase交易金额，而用挖矿方法，是因为coinBase交易存在交易手续费金额
                    value = block.getBlockInflationCoin(height, storedPrev.getHeader().getDifficultyTarget(), false);
                }
            } else {//达世币封存，这里忽略
                return;
            }
        } else {
            if (height == SafeConstant.getBlockAmountHeight()) {
                value = Coin.valueOf(1846514477084975L);
            } else if (height > SafeConstant.getBlockAmountHeight()) {
                if (height >= Constants.NETWORK_PARAMETERS.getStartSposHeight()) { //采用SPOS
                    value = block.getSposBlockInflationCoin(height, false);
                } else { //采用POW
                    //这里不用coinBase交易金额，而用挖矿方法，是因为coinBase交易存在交易手续费金额
                    value = block.getBlockInflationCoin(height, storedPrev.getHeader().getDifficultyTarget(), false);
                }
            } else { //达世币封存，这里忽略
                return;
            }
        }

        SafeUtils.setTotalAmount(height, value.getValue());

        if (block.getTransactions() != null) {
            for (Transaction tx : block.getTransactions()) {
                try {
                    SafeUtils.saveFilterAmount(tx, height);
                } catch (Exception e) {
                    log.error("Filter amount save failed: {}", e);
                }
                try {
                    SafeUtils.saveIssueAndCandy(tx, block.getTime().getTime(), height);
                } catch (Exception e) {
                    log.error("Issue save failed: {}", e);
                }
            }
        }
    }

    public static void loadNoCalcCandy() {
        try {
            int candyHeight = SafeConstant.getLastBlockHeight() - SafeConstant.CANDY_CALC_DEPTH;
            String sqlCount = "select distinct height from tb_put_candy where height <= ? and calcFinishFlag = ? order by height asc";
            GenericRawResults<String[]> rawResults = putCandyDao.getDao().queryRaw(sqlCount, new String[]{Integer.toString(candyHeight), Integer.toString(PutCandyData.UN_CACL)});
            List<String[]> candyHeightArr = rawResults.getResults();
            for (String[] item : candyHeightArr) {
                handleCandy(Integer.parseInt(item[0]));
            }
        } catch (Exception e) {
            log.error("Candy height load failed: {}", e);
        }
    }

    public static void handleCandy(final int height) {
        ThreadPoolProxyFactory.getCandyThreadPoolProxy().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    //查询还未计算的糖果数量
                    String sql = "select distinct height from tb_put_candy where height = ? and calcFinishFlag = ?";
                    int candyHeight = height - SafeConstant.CANDY_CALC_DEPTH;
                    GenericRawResults<String[]> rawResults = putCandyDao.getDao().queryRaw(sql, new String[]{Integer.toString(candyHeight), Integer.toString(PutCandyData.UN_CACL)});
                    List<String[]> results = rawResults.getResults();
                    if (results.size() > 0) {
                        Uninterruptibles.putUninterruptibly(taskQueues, candyHeight);
                    }
                } catch (Exception e) {
                    log.error("Candy height load failed: {}", e);
                }
            }
        });
    }

    private static LinkedBlockingQueue<Integer> taskQueues = new LinkedBlockingQueue<>();

    public static class SafeThread extends Thread {
        @Override
        public void run() {
            while (true) {
                int height = Uninterruptibles.takeUninterruptibly(taskQueues);
                try {
                    //未计算的糖果
                    List<PutCandyData> candyList = putCandyDao.query(new String[]{"height", "calcFinishFlag"}, new Object[]{height, PutCandyData.UN_CACL});
                    if (candyList.size() > 0) {
                        org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                        Map<String, Coin> balance = WalletApplication.getInstance().getWallet().getHeightBalance(height);
                        if (balance.size() > 0) {
                            long totalAmount = SafeUtils.getTotalAmount(height);
                            long filterAmount = SafeUtils.getFilterAmount(height);
                            for (PutCandyData item : candyList) {
                                SafeUtils.saveCandyAddrData(item, height, totalAmount, filterAmount, balance);
                                //标记成已计算
                                updateCandy("update tb_put_candy set calcFinishFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.CALC_END), item.txId});
                            }
                        } else {
                            for (PutCandyData item : candyList) {
                                //标记成已计算
                                updateCandy("update tb_put_candy set calcFinishFlag = ? where txId = ?", new String[]{Integer.toString(PutCandyData.CALC_END), item.txId});
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Candy save failed: {}", e);
                }
            }
        }
    }

    public static synchronized void updateCandy(String sql, String... args) {
        try {
            putCandyDao.getDao().updateRaw(sql, args);
        } catch (Exception e) {
            log.error("update candy save failed: {}", e);
        }
    }

    static {
        SafeThread thread = new SafeThread();
        thread.setDaemon(true);
        thread.start();
    }

    public static void deleteCandyRecord(String txId){
        try {
            getCandydao.getDao().executeRaw("delete from tb_get_candy where txId = ?", new String[]{txId});
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
