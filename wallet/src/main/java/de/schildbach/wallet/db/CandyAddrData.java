package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * 糖果对应地址数据
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_candy_addr")
public class CandyAddrData implements Serializable {

    @DatabaseField()
    public String txId; // 交易ID

    @DatabaseField()
    public long value;  // SAFE余额

    @DatabaseField()
    public String address; // 可领取SAFE地址

    @DatabaseField()
    public long height; //高度

    @Override
    public String toString() {
        return "CandyAddrData{" +
                "txId='" + txId + '\'' +
                ", value=" + value +
                ", address='" + address + '\'' +
                ", height=" + height +
                '}';
    }
}
