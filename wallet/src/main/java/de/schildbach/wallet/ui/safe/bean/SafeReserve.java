package de.schildbach.wallet.ui.safe.bean;

import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoin.safe.SafeProtos;
import org.bitcoin.safe.SposProtos;
import org.bitcoinj.utils.SafeConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * SAFE预留字段解析
 * @author zhangmiao
 */
public class SafeReserve {

    public String safeFlag; //safe标识
    public int version; //头部版本
    public String appId; //官方应用ID
    public int appCommand; //应用命名
    public byte[] protos; //业务数据
    public boolean isSposFlag = false; //spos协议标识

    public static final Logger log = LoggerFactory.getLogger(SafeReserve.class);

    public boolean isDashTx() {
        return safeFlag == null;
    }

    public boolean isSafeTx() {
        return !isSposFlag && (protos == null || isSafeRemark());
    }

    public boolean isSposDeterministicCoinbase() {
        return isSposFlag && version == 2;
    }

    public boolean isSposMasternodeDeterministic() {
        return isSposFlag && version == 100;
    }

    public boolean isIssue() {
        return appCommand == SafeConstant.CMD_ISSUE;
    }

    public boolean isAddIssue() {
        return appCommand == SafeConstant.CMD_ADD_ISSUE;
    }

    public boolean isTransfer() {
        return appCommand == SafeConstant.CMD_TRANSFER;
    }

    public boolean isAssetChange() {
        return appCommand == SafeConstant.CMD_ASSET_CHANGE;
    }

    public boolean isPutCandy() {
        return appCommand == SafeConstant.CMD_GRANT_CANDY;
    }

    public boolean isGetCandy() {
        return appCommand == SafeConstant.CMD_GET_CANDY;
    }

    public boolean isSafeRemark() {
        return appCommand == SafeConstant.CMD_SAFE_REMARK;
    }

    public SafeProtos.IssueData getIssueProtos() {
        try {
            if (isIssue()) {
                return SafeProtos.IssueData.parseFrom(protos);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

    public SafeProtos.CommonData getCommonProtos() {
        try {
            if (isAddIssue() || isTransfer() || isAssetChange()) {
                return SafeProtos.CommonData.parseFrom(protos);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

    public SafeProtos.PutCandyData getPutCandyProtos() {
        try {
            if (isPutCandy()) {
                return SafeProtos.PutCandyData.parseFrom(protos);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

    public SafeProtos.GetCandyData getGetCandyProtos() {
        try {
            if (isGetCandy()) {
                return SafeProtos.GetCandyData.parseFrom(protos);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

    public SposProtos.DeterministicCoinbaseData getDeterministicCoinbaseProtos() {
        try {
            if (isSposDeterministicCoinbase()) {
                return SposProtos.DeterministicCoinbaseData.parseFrom(protos);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

    public SposProtos.DeterministicMasternodeData getDeterministicMasternodeProtos() {
        try {
            if (isSposMasternodeDeterministic()) {
                return SposProtos.DeterministicMasternodeData.parseFrom(protos);
            } else {
                return null;
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    }

}
