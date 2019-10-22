package org.bitcoinj.utils;

import org.bitcoinj.core.CoinDefinition;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe数据管理
 *
 * @author zhangmiao
 */
public class SafeConstant {

    public static final String SAFE_FLAG = CoinDefinition.coinURIScheme; //safe标识

    public static final String SAFE_APP_ID = "cfe2450bf016e2ad8130e4996960a32e0686c1704b62a6ad02e49ee805a9b288"; //安资APP_ID

    public static final String PAY_APP_ID = "a4bea6705cd38d535e873da1c9ad897048b6bbc8e286ca9b28bd18bb22eedcc9"; //安付APP_ID

    public static final String BLACK_HOLE_ADDRESS = "XagqqFetxiDb9wbartKDrXgnqLah9fKoTx"; //销毁SAFE黑洞地址

    public static final String CANDY_BLACK_HOLE_ADDRESS = "XagqqFetxiDb9wbartKDrXgnqLahUovwfs"; //销毁糖果黑洞地址

    public static final List<String> FILTER_BLACK_HOLE_ADDRESS = new ArrayList<>();

    public static final int RESERVE_HEADER_VERSION = 1; //预留字段头部版本编号 1、2、3、4 （安资、安付、安聊、安投）

    public static final int BRANCH_TX_VERSION = 101; //分支交易版本

    public static final int APP_TX_VERSION = 102; //App交易版本

    public static final int SPOS_TX_VERSION = 103; //SPOS交易版本

    public static final int CMD_REGISTER = 100; //注册应用命令

    public static final int CMD_ISSUE = 200; //发行命令

    public static final int CMD_ADD_ISSUE = 201; //追加发行命令

    public static final int CMD_TRANSFER = 202; //转让命令

    public static final int CMD_DESTORY = 203; //销毁命令

    public static final int CMD_ASSET_CHANGE = 204; //资产找零

    public static final int CMD_GRANT_CANDY = 205; //发放糖果命令

    public static final int CMD_GET_CANDY = 206; //领取糖果命令

    public static final int CMD_SAFE_REMARK = 300; //SAFE备注

    public static final int BLOCKS_PER_DAY = CoinDefinition.TARGET_TIMESPAN / CoinDefinition.TARGET_SPACING;

    public static final int BLOCKS_PER_MONTH = 30 * BLOCKS_PER_DAY;

    public static final int SPOS_BLOCKS_PER_DAY = CoinDefinition.TARGET_TIMESPAN / CoinDefinition.SPOS_TARGET_SPACING;

    public static final int SPOS_BLOCKS_MINUTE = 6 * CoinDefinition.SPOS_TARGET_SPACING;

    public static final int SPOS_BLOCKS_PER_MONTH = 30 * SPOS_BLOCKS_PER_DAY;

    public static final int CANDY_GRANT_MAX_TIMES = 5; //最大发放糖果

    public static final int CANDY_CALC_DEPTH = 19; //糖果计算深度

    public static final int BEFORE_CHECK_POINT = 100;

    public static final int CAN_SELECT_MASTERNODE_HEIGHT= 10000;


    static {
        FILTER_BLACK_HOLE_ADDRESS.add("XagqqFetxiDb9wbartKDrXgnqLah6SqX2S");
        FILTER_BLACK_HOLE_ADDRESS.add("XagqqFetxiDb9wbartKDrXgnqLah9fKoTx");
        FILTER_BLACK_HOLE_ADDRESS.add("XagqqFetxiDb9wbartKDrXgnqLahHSe2VE");
        FILTER_BLACK_HOLE_ADDRESS.add("XagqqFetxiDb9wbartKDrXgnqLahUovwfs");
    }

    public static synchronized int getLastBlockHeight() {
        if (WalletApplication.getInstance() != null && WalletApplication.getInstance().getWallet() != null) {
            return WalletApplication.getInstance().getWallet().getWalletSeenHeight();
        } else {
            return 0;
        }
    }

    public static final int getSafeBranchHeight() {
        if (Constants.TEST) {
            return CoinDefinition.TEST_SAFE_BRANCH_HEIGHT;
        } else {
            return CoinDefinition.SAFE_BRANCH_HEIGHT;
        }
    }

    public static final int getDashDisabledHeight() {
        if (Constants.TEST) {
            return CoinDefinition.TEST_DASH_DISABLE_HEIGHT;
        } else {
            return CoinDefinition.DASH_DISABLE_HEIGHT;
        }
    }

    public static final int getBlockAmountHeight() {
        if (Constants.TEST) {
            return CoinDefinition.TEST_DASH_DISABLE_HEIGHT;
        } else {
            return CoinDefinition.DASH_DISABLE_HEIGHT + 1;
        }
    }

    public static final int getBeforehandBlockBodies() {
        if (Constants.TEST) {
            return CoinDefinition.testBeforehandBlockBodies;
        } else {
            return CoinDefinition.beforehandBlockBodies;
        }
    }

}
