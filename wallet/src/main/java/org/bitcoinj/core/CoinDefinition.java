package org.bitcoinj.core;

import java.math.BigInteger;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Hash Engineering Solutions
 * Date: 5/3/14
 * To change this template use File | Settings | File Templates.
 * zhangmiao update | SAFE配置相关都在这里
 */
public class CoinDefinition {

    public static final String coinName = "Safe";
    public static final String coinTicker = "SAFE";
    public static final String coinURIScheme = "safe";
    public static final String cryptsyMarketCurrency = "BTC";
    public static final String PATTERN_PRIVATE_KEY_START_UNCOMPRESSED = "[7]";
    public static final String PATTERN_PRIVATE_KEY_START_COMPRESSED = "[X]";

    public static final String BLOCKEXPLORER_ADDRESS_PATH = "address/";
    public static final String BLOCKEXPLORER_TRANSACTION_PATH = "tx/";
    public static final String BLOCKEXPLORER_BLOCK_PATH = "block/";

    public static final String UNSPENT_API_URL = "https://chainz.cryptoid.info/dash/api.dws?q=unspent";
    public static final String DONATION_ADDRESS = "Xdeh9YTLNtci5zSL4DDayRSVTLf299n9jv";  //Hash Engineering donation DASH address

    public static boolean checkpointFileSupport = true;

    public static final int TARGET_TIMESPAN = (int) (24 * 60 * 60);  // 24 hours per difficulty cycle, on average.

    public static final int SPOS_TARGET_SPACING = 30;  //SPOS产块的时间间隙秒单位

    public static final int getInterval(int height, boolean testNet) { //108
        return INTERVAL;
    }

    public static final int getIntervalCheckpoints() {
        return INTERVAL;
    }

    public static final int getTargetTimespan(int height, boolean testNet) {  //72 min
        return TARGET_TIMESPAN;
    }

    public static int spendableCoinbaseDepth = 100; //main.h: static const int COINBASE_MATURITY
    public static final long MAX_COINS = 37000000;                 //main.h:  MAX_MONEY
    public static final long MAX_ASSET_COINS = 2000000000000000000L;                 //main.h:  MAX_MONEY

    public static final long DEFAULT_MIN_TX_FEE = 10000;   // MIN_TX_FEE
    public static final long DUST_LIMIT = 30000; //main.h CTransaction::GetMinFee        0.01 coins
    public static final long INSTANTX_FEE = 100000; //0.001 DASH (updated for 12.1)
    public static final boolean feeCanBeRaised = false;

    // Dash 0.12.1.x
    public static final int PROTOCOL_VERSION = 70210;          //version.h PROTOCOL_VERSION
    public static final int MIN_PROTOCOL_VERSION = 70210;        //version.h MIN_PROTO_VERSION

    public static final int MAX_BLOCK_SIZE = 1000 * 1000;

    //  Production
    public static final int AddressHeader = 76;             //base58.h CBitcoinAddress::PUBKEY_ADDRESS
    public static final int p2shHeader = 16;             //base58.h CBitcoinAddress::SCRIPT_ADDRESS

    static public String genesisMerkleRoot = "e0028eb9648db56b1ac77cf090b99048a8007e2bb64b68f092c03c7f56a662c7";
    static public int genesisBlockValue = 50;                                                              //main.cpp: LoadBlockIndex
    //taken from the raw data of the block explorer
    static public String genesisTxInBytes = "04ffff001d01044c5957697265642030392f4a616e2f3230313420546865204772616e64204578706572696d656e7420476f6573204c6976653a204f76657273746f636b2e636f6d204973204e6f7720416363657074696e6720426974636f696e73";   //"limecoin se convertira en una de las monedas mas segura del mercado, checa nuestros avances"
    static public String genesisTxOutBytes = "040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9";

    public static int minBroadcastConnections = 0;   //0 for default; Using 3 like BreadWallet.

    public static int subsidyDecreaseBlockCount = 210240;     //main.cpp GetBlockValue(height, fee)

    public static BigInteger proofOfWorkLimit = Utils.decodeCompactBits(0x1f0fffffL);  //main.cpp bnProofOfWorkLimit (~uint256(0) >> 20); // digitalcoin: starting difficulty is 1 / 2^12

    // TestNet - DASH
    public static final boolean supportsTestNet = true;
    public static final int testnetPort = 19999;     //protocol.h GetDefaultPort(testnet=true)
    public static final int testnetAddressHeader = 140;             //base58.h CBitcoinAddress::PUBKEY_ADDRESS_TEST
    public static final int testnetp2shHeader = 19;             //base58.h CBitcoinAddress::SCRIPT_ADDRESS_TEST
    public static final long testnetPacketMagic = 0xcee2caff;      //
    public static final String testnetGenesisHash = "00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c";
    static public long testnetGenesisBlockDifficultyTarget = (0x1e0ffff0L);         //main.cpp: LoadBlockIndex
    static public long testnetGenesisBlockTime = 1390666206L;                       //main.cpp: LoadBlockIndex
    static public long testnetGenesisBlockNonce = (3861367235L);                         //main.cpp: LoadBlockIndex
    public static final String TESTNET_SATOSHI_KEY = "034e7922968f837f384ffa717b459fe144715dbe293c96aaca7ff3d61592fcc18f";
    public static final String TESTNET_SPORK_KEY = "03df675c49736a1f46e3d12f5cd89cd423ef5fd2850a09e9685aff0895e75c4b7b";

    static public String[] testnetDnsSeeds = new String[]{
            "testnet-seed.dashdot.io",
            "test.dnsseed.masternode.io",
    };

    //from main.h: CAlert::CheckSignature
    public static final String SATOSHI_KEY = "03947ee980fe0cfaf4b407fe9e3b88f0dae288d0a664891b0b8186cb2ac111fe77";

    public static final String SPORK_KEY = "02af43fcf4425952f659a24dd629378cfab8ae778aaf59036dea7ac93722dce009";

    /**
     * The string returned by getId() for the main, production network where people trade things.
     */
    public static final String ID_MAINNET = "org.safecoin.production";
    /**
     * The string returned by getId() for the testnet.
     */
    public static final String ID_TESTNET = "org.safecoin.test";
    /**
     * Unit test network.
     */
    public static final String ID_UNITTESTNET = "com.google.safecoin.unittest";

    //Unit Test Information
    public static final String UNITTEST_ADDRESS = "XgxQxd6B8iYgEEryemnJrpvoWZ3149MCkK";
    public static final String UNITTEST_ADDRESS_PRIVATE_KEY = "XDtvHyDHk4S3WJvwjxSANCpZiLLkKzoDnjrcRhca2iLQRtGEz1JZ";


    /**************************************************切换环境相关配置**************************************************/

    //生产链相关配置
    public static final int PORT = 5555; //生产端口
    public static final long PACKET_MAGIC = 0x62696ecc; //生产魔数
    public static final int TARGET_SPACING = 150;  //产块的时间间隙秒单位
    public static final int INTERVAL = TARGET_TIMESPAN / TARGET_SPACING;  //一天产生的块
    public static final int SAFE_BRANCH_HEIGHT = 807085; // 分叉高度
    public static final int DASH_DISABLE_HEIGHT = 943809; // 达世封存高度
    public static final int START_SPOS_HEIGHT = 1092826; // 开始SPOS高度
    public static final int ADJUST_MIN_REWARD_HEIGHT = 1109103; // 调整奖励高度
    public static final int FORBID_OLD_VERSION_HEIGHT = 1101183; // 节点强制更新高度
    public static final long MIN_INCENTIVES = 310662692L;  //最小挖矿激励
    public static final long SPOS_MIN_INCENTIVES = 345180768L;  //SPOS最小挖矿激励
    public static final String BLOCKEXPLORER_BASE_URL = "http://chain.anwang.com/"; //区块浏览器
    public static final long genesisBlockDifficultyTarget = (0x1e0ffff0L);
    public static final long genesisBlockTime = 1390095618L;
    public static final long genesisBlockNonce = (28917698);
    public static final String genesisHash = "00000ffd590b1485b3caadc19b22e6379c733355108f107a430458cdf3407ab6";
    public static final int beforehandBlockBodies = 20; //提前下载区块体
    public static final long sposStart_deterministic_masternode_height = 1500000L;
    public static String[] SEEDS = new String[]{
            "120.78.227.96",
            "114.215.31.37",
            "47.95.23.220",
            "47.96.254.235",
            "106.14.66.206",
            "47.52.9.168",
            "47.75.17.223",
            "47.88.247.232",
            "47.89.208.160",
            "47.74.13.245"
    };

    //测试链相关配置
//    public static final int TEST_PORT = 4466;       //测试端口
//    public static final long TEST_PACKET_MAGIC = 0x646466c6;  //测试魔数
//    public static final int TEST_TARGET_SPACING = SPOS_TARGET_SPACING;  // 产块的时间间隙秒单位
//    public static final int TEST_SAFE_BRANCH_HEIGHT = 175; //测试分叉高度
//    public static final int TEST_DASH_DISABLE_HEIGHT = 400; //测试达世封存高度
//    public static final int TEST_START_SPOS_HEIGHT = 104104; // 开始SPOS高度
//    public static final int TEST_ADJUST_MIN_REWARD_HEIGHT = 104914; // 调整奖励高度
//    public static final int TEST_FORBID_OLD_VERSION_HEIGHT = 104905; // 节点强制更新高度
//    public static final long TEST_MIN_INCENTIVES = 450000000L;  //最小挖矿激励
//    public static final long TEST_SPOS_MIN_INCENTIVES = 500000000L;  //SOPS最小挖矿激励
//    public static final String TEST_BLOCKEXPLORER_BASE_URL = "http://106.12.144.124/"; //区块浏览器
//    public static final long testGenesisBlockDifficultyTarget = (0x1f0ffff0);
//    public static final long testGenesisBlockTime = 1515222820L;
//    public static final long testGenesisBlockNonce = (4705);
//    public static final String testGenesisHash = "000d8b21044326f9b58110404510ce2b4ee30af5d97dd7de30d551c34dfdc9a2";
//    public static final int testBeforehandBlockBodies = 20;  //提前下载区块体
//    public static final int test_sposStart_deterministic_masternode_height = 112258L;
//    public static String[] TEST_SEEDS = new String[]{
//            "182.61.39.212",
//            "182.61.13.68",
//            "182.61.22.240",
//            "182.61.39.172",
//            "182.61.15.31",
//            "182.61.37.132",
//    };

    //DEV相关配置
    public static final int TEST_PORT = 4499;       //测试端口
    public static final long TEST_PACKET_MAGIC = 0x646469c9;  //测试魔数
    public static final int TEST_TARGET_SPACING = SPOS_TARGET_SPACING;  // 产块的时间间隙秒单位
    public static final int TEST_SAFE_BRANCH_HEIGHT = 175; //测试分叉高度
    public static final int TEST_DASH_DISABLE_HEIGHT = 400; //测试达世封存高度
    public static final int TEST_START_SPOS_HEIGHT = 801; // 开始SPOS高度
    public static final int TEST_ADJUST_MIN_REWARD_HEIGHT = 780; // 调整奖励高度
    public static final int TEST_FORBID_OLD_VERSION_HEIGHT = 760; // 节点强制更新高度
    public static final long TEST_MIN_INCENTIVES = 450000000L;  //最小挖矿激励
    public static final long TEST_SPOS_MIN_INCENTIVES = 500000000L;  //SOPS最小挖矿激励
    public static final String TEST_BLOCKEXPLORER_BASE_URL = "http://10.0.0.76/"; //区块浏览器
    public static final long testGenesisBlockDifficultyTarget = (0x1f0ffff0);
    public static final long testGenesisBlockTime = 1515222820L;
    public static final long testGenesisBlockNonce = (4705);
    public static final String testGenesisHash = "000d8b21044326f9b58110404510ce2b4ee30af5d97dd7de30d551c34dfdc9a2";
    public static final int testBeforehandBlockBodies = 20;  //提前下载区块体
    public static final long test_sposStart_deterministic_masternode_height = 4157L;
    public static String[] TEST_SEEDS = new String[]{
            "106.13.9.129",
            "106.13.12.55",
            "106.12.118.160",
            "106.13.2.195",
    };
    /**************************************************切换环境相关配置**************************************************/

}
