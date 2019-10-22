package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 全网资产索引
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_issue")
public class IssueData implements Serializable {

    @DatabaseField(unique = true)
    public String assetId;  // 资产ID

    @DatabaseField()
    public String appId; // 应用ID

    @DatabaseField()
    public long version; // 版本号

    @DatabaseField()
    public String shortName; // 资产简介

    @DatabaseField()
    public String assetName; // 资产名称

    @DatabaseField()
    public String assetDesc; // 资产描述

    @DatabaseField()
    public String assetUnit; // 资产单位

    @DatabaseField()
    public long totalAmount; // 资产总量

    @DatabaseField()
    public long firstIssueAmount; // 初次发行总量

    @DatabaseField()
    public long firstActualAmount; // 实际发行总量

    @DatabaseField()
    public long decimals; // 小数点

    @DatabaseField()
    public boolean destory; // 是否可以销毁

    @DatabaseField()
    public boolean payCandy; // 是否分发糖果

    @DatabaseField()
    public long candyAmount;  //糖果金额

    @DatabaseField()
    public long candyExpired; //过期时间

    @DatabaseField()
    public String remarks; // 备注

    @DatabaseField()
    public String txId; // 交易ID

    //资产余额
    public Coin balance = Coin.ZERO;

    //资产未花费资产输出
    public List<TransactionOutput> balanceList = new ArrayList<>();

    @Override
    public String toString() {
        return "IssueData{" +
                "assetId='" + assetId + '\'' +
                ", version=" + version +
                ", shortName='" + shortName + '\'' +
                ", assetName='" + assetName + '\'' +
                ", assetDesc='" + assetDesc + '\'' +
                ", assetUnit='" + assetUnit + '\'' +
                ", totalAmount=" + totalAmount +
                ", firstIssueAmount=" + firstIssueAmount +
                ", firstActualAmount=" + firstActualAmount +
                ", decimals=" + decimals +
                ", destory=" + destory +
                ", payCandy=" + payCandy +
                ", candyAmount=" + candyAmount +
                ", candyExpired='" + candyExpired + '\'' +
                ", remarks='" + remarks + '\'' +
                ", txId='" + txId +
                ", balance='" + balance.getValue() +
                '}';
    }
}
