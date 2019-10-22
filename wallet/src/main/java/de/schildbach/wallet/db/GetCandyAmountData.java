package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * 领取糖果数量
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_get_candy_amount")
public class GetCandyAmountData implements Serializable {

    @DatabaseField(unique = true)
    public String txId;        //交易TxId

    @DatabaseField()
    public String putCandyTxId;  //交易ID

    @DatabaseField()
    public long totalAmount; //总金额

    @Override
    public String toString() {
        return "GetCandyAmountData{" +
                "txId='" + txId + '\'' +
                ", putCandyTxId='" + putCandyTxId + '\'' +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
