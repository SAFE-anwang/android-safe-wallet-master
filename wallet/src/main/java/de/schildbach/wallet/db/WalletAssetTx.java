package de.schildbach.wallet.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * 钱包资产交易
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_wallet_asset_tx")
public class WalletAssetTx implements Serializable {

    @DatabaseField(generatedId = true)
    public long id;

    @DatabaseField()
    public String txId;

    @DatabaseField()
    public String assetId;

    @DatabaseField()
    public int appCommand;

    @DatabaseField()
    public long amount;

    @Override
    public String toString() {
        return "WalletAssetTx{" +
                "id=" + id +
                ", txId='" + txId + '\'' +
                ", assetId='" + assetId + '\'' +
                ", appCommand=" + appCommand +
                ", amount=" + amount +
                '}';
    }
}
