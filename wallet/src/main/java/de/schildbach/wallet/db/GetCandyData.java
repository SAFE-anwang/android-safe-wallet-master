package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * 钱包领取糖果记录
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_get_candy")
public class GetCandyData implements Serializable {

    @DatabaseField(generatedId = true)
    public long id; // 主键ID

    @DatabaseField()
    public String txId; // 交易ID

    @DatabaseField()
    public String inTxId; // 输入交易ID

    @DatabaseField()
    public long version; // 版本号

    @DatabaseField()
    public String assetId;  // 资产ID

    @DatabaseField()
    public long candyAmount; // 地址糖果总额

    @DatabaseField()
    public String remarks; // 备注

    @DatabaseField()
    public String address; // 接受糖果地址

    //保存临时资产名称
    public String assetName; //资产名称

    //保存临时小数点
    public long decimals; //资产小数点

    //保存临时资产单位
    public String assetUnit; //资产单位

    @Override
    public String toString() {
        return "GetCandyData{" +
                "txId='" + txId + '\'' +
                ", inTxId='" + inTxId + '\'' +
                ", version=" + version +
                ", assetId='" + assetId + '\'' +
                ", candyAmount=" + candyAmount +
                ", remarks='" + remarks + '\'' +
                ", address='" + address + '\'' +
                ", assetName='" + assetName + '\'' +
                ", decimals=" + decimals +
                ", assetUnit='" + assetUnit + '\'' +
                '}';
    }
}
