package de.schildbach.wallet.ui.safe.utils;

import org.bitcoinj.core.Monetary;

/**
 * 资产币格式
 * @author zhangmiao
 */
public class AssetCoin implements Monetary {

    public long value;
    public int exponent;

    public AssetCoin(long value, int exponent){
        this.value = value;
        this.exponent = exponent;
    }

    @Override
    public int smallestUnitExponent() {
        return exponent;
    }

    @Override
    public long getValue() {
        return value;
    }

    @Override
    public int signum() {
        if (this.value == 0)
            return 0;
        return this.value < 0 ? -1 : 1;
    }

}
