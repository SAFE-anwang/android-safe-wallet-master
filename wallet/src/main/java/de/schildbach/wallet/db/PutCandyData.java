package de.schildbach.wallet.db;

import android.util.Log;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.CoinDefinition;
import org.bitcoinj.utils.SafeConstant;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 全网发放糖果索引
 * @author zhangmiao
 */
@DatabaseTable(tableName = "tb_put_candy")
public class PutCandyData implements Serializable {

    public final static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public final static int UN_CACL = 0; //未计算

    public final static int CALC_END = 1; //已计算

    public final static int STATUS_DISENABLED = 0; //不可用

    public final static int STATUS_ENABLED = 1; //已可领

    public final static int STATUS_RECEIVED = 2; //已领取

    public final static int STATUS_EXPIRED = 3; //已过期

    public final static int STATUS_BEEN_FINISHED = 4; //已领完

    @DatabaseField(unique = true)
    public String txId; // 交易ID

    @DatabaseField()
    public String assetId;  // 资产ID

    @DatabaseField()
    public long version; // 版本号

    @DatabaseField()
    public long candyAmount; // 资产糖果总额

    @DatabaseField()
    public long candyExpired; // 糖果过期时间（单位：月份）

    @DatabaseField()
    public String remarks; // 备注

    @DatabaseField()
    public long txTime; //交易时间

    @DatabaseField()
    public long height; //高度

    @DatabaseField()
    public int outputIndex; // 输出地址的位置

    @DatabaseField(defaultValue = "0")
    public int calcFinishFlag; // 糖果计算完成标识（0 未计算，1 已计算）

    @DatabaseField(defaultValue = "0")
    public int candyGetFlag; // 糖果领取标识 （0 不可用，1 可领取，2 已领取，3 已过期，4 已领完 ）

    //保存临时资产名称
    public String assetName; //资产名称

    //保存临时资产单位
    public long decimals; //资产单位

    /**
     * 判断糖果是否过期
     * @param lastBlockHeight 区块最新高度
     * @return
     */
    public boolean isExpired(long lastBlockHeight) {
        long nCurrentHeight;
        int startSposHeight = Constants.NETWORK_PARAMETERS.getStartSposHeight();
        if(lastBlockHeight >= startSposHeight) {
            nCurrentHeight = lastBlockHeight - 3 * SafeConstant.SPOS_BLOCKS_PER_DAY; //这里提前3天过期
        } else {
            nCurrentHeight = lastBlockHeight - 2; //这里提前两个过期
        }
        if (height < startSposHeight) {
            if (candyExpired * SafeConstant.BLOCKS_PER_MONTH + height >= startSposHeight) { //采用POW和SPOS
                int sposLaveHeight = (int) (candyExpired * SafeConstant.BLOCKS_PER_MONTH + height - startSposHeight) * (CoinDefinition.TARGET_SPACING / CoinDefinition.SPOS_TARGET_SPACING);
                int neededHeight = startSposHeight + sposLaveHeight;
                if (neededHeight > nCurrentHeight) {
                    return false;
                } else {
                    return true;
                }
            } else { //采用POW
                if (candyExpired * SafeConstant.BLOCKS_PER_MONTH + height > nCurrentHeight) {
                    return false;
                } else {
                    return true;
                }
            }
        } else { //采用SPOS
            if (candyExpired * SafeConstant.SPOS_BLOCKS_PER_MONTH + height > nCurrentHeight) {
                return false;
            } else {
                return true;
            }
        }
    }

    public String getTxTime(){
        Calendar calendar =  Calendar.getInstance();
        calendar.setTimeInMillis(txTime);
        return format.format(calendar.getTime());
    }

    @Override
    public String toString() {
        return "PutCandyData{" +
                "txId='" + txId + '\'' +
                ", assetId='" + assetId + '\'' +
                ", version=" + version +
                ", candyAmount=" + candyAmount +
                ", candyExpired=" + candyExpired +
                ", remarks='" + remarks + '\'' +
                ", txTime=" + txTime +
                ", height=" + height +
                ", outputIndex=" + outputIndex +
                ", calcFinishFlag=" + calcFinishFlag +
                ", candyGetFlag=" + candyGetFlag +
                ", assetName='" + assetName + '\'' +
                ", decimals='" + decimals + '\'' +
                '}';
    }
}
