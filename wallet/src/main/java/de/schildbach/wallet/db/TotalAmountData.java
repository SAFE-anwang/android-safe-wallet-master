package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * 高度对应金额表
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_total_amount")
public class TotalAmountData implements Serializable {

    @DatabaseField(unique = true)
    public long height; //高度

    @DatabaseField()
    public long totalAmount; //总金额

}
