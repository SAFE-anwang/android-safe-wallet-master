package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * 高度对应金额表
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_filter_amount")
public class FilterAmountData implements Serializable {

    @DatabaseField(unique = true)
    public String txId; // 交易ID

    @DatabaseField()
    public long height; // 高度

    @DatabaseField()
    public long filterAmount; //过滤金额

}
