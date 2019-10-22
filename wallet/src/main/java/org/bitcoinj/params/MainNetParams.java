/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.params;

import android.util.Log;

import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.*;

import java.net.*;

import de.schildbach.wallet.Constants;

import static com.google.common.base.Preconditions.*;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {

    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public MainNetParams() {
        super();
        interval = CoinDefinition.INTERVAL;
        targetTimespan = CoinDefinition.TARGET_TIMESPAN;
        maxTarget = CoinDefinition.proofOfWorkLimit;
        dumpedPrivateKeyHeader = 204;
        addressHeader = CoinDefinition.AddressHeader;
        p2shHeader = CoinDefinition.p2shHeader;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        if (Constants.TEST) {
            port = CoinDefinition.TEST_PORT;
            packetMagic = CoinDefinition.TEST_PACKET_MAGIC;
        } else {
            port = CoinDefinition.PORT;
            packetMagic = CoinDefinition.PACKET_MAGIC;
        }
        bip32HeaderPub = 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"

        if (Constants.TEST) {
            genesisBlock.setDifficultyTarget(CoinDefinition.testGenesisBlockDifficultyTarget);
            genesisBlock.setTime(CoinDefinition.testGenesisBlockTime);
            genesisBlock.setNonce(CoinDefinition.testGenesisBlockNonce);
        } else {
            genesisBlock.setDifficultyTarget(CoinDefinition.genesisBlockDifficultyTarget);
            genesisBlock.setTime(CoinDefinition.genesisBlockTime);
            genesisBlock.setNonce(CoinDefinition.genesisBlockNonce);
        }

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        id = ID_MAINNET;
        subsidyDecreaseBlockCount = CoinDefinition.subsidyDecreaseBlockCount;
        spendableCoinbaseDepth = CoinDefinition.spendableCoinbaseDepth;
        String genesisHash = genesisBlock.getHashAsString();

        if (Constants.TEST) { //测试链
            dnsSeeds = CoinDefinition.TEST_SEEDS;
            budgetPaymentsStartBlock = 500;
            strSporkKey = CoinDefinition.TESTNET_SPORK_KEY;
            checkState(genesisHash.equals(CoinDefinition.testGenesisHash), genesisHash);
        } else { //生产链
            dnsSeeds = CoinDefinition.SEEDS;
            budgetPaymentsStartBlock = 328008;
            strSporkKey = CoinDefinition.SPORK_KEY;
            checkState(genesisHash.equals(CoinDefinition.genesisHash), genesisHash);
            checkpoints.put(1500, Sha256Hash.wrap("000000aaf0300f59f49bc3e970bad15c11f961fe2347accffff19d96ec9778e3"));
            checkpoints.put(4991, Sha256Hash.wrap("000000003b01809551952460744d5dbb8fcbd6cbae3c220267bf7fa43f837367"));
            checkpoints.put(9918, Sha256Hash.wrap("00000000213e229f332c0ffbe34defdaa9e74de87f2d8d1f01af8d121c3c170b"));
            checkpoints.put(16912, Sha256Hash.wrap("00000000075c0d10371d55a60634da70f197548dbbfa4123e12abfcbc5738af9"));
            checkpoints.put(23912, Sha256Hash.wrap("0000000000335eac6703f3b1732ec8b2f89c3ba3a7889e5767b090556bb9a276"));
            checkpoints.put(35457, Sha256Hash.wrap("0000000000b0ae211be59b048df14820475ad0dd53b9ff83b010f71a77342d9f"));
            checkpoints.put(45479, Sha256Hash.wrap("000000000063d411655d590590e16960f15ceea4257122ac430c6fbe39fbf02d"));
            checkpoints.put(55895, Sha256Hash.wrap("0000000000ae4c53a43639a4ca027282f69da9c67ba951768a20415b6439a2d7"));
            checkpoints.put(68899, Sha256Hash.wrap("0000000000194ab4d3d9eeb1f2f792f21bb39ff767cb547fe977640f969d77b7"));
            checkpoints.put(74619, Sha256Hash.wrap("000000000011d28f38f05d01650a502cc3f4d0e793fbc26e2a2ca71f07dc3842"));
            checkpoints.put(75095, Sha256Hash.wrap("0000000000193d12f6ad352a9996ee58ef8bdc4946818a5fec5ce99c11b87f0d"));
            checkpoints.put(88805, Sha256Hash.wrap("00000000001392f1652e9bf45cd8bc79dc60fe935277cd11538565b4a94fa85f"));
            checkpoints.put(107996, Sha256Hash.wrap("00000000000a23840ac16115407488267aa3da2b9bc843e301185b7d17e4dc40"));
            checkpoints.put(137993, Sha256Hash.wrap("00000000000cf69ce152b1bffdeddc59188d7a80879210d6e5c9503011929c3c"));
            checkpoints.put(167996, Sha256Hash.wrap("000000000009486020a80f7f2cc065342b0c2fb59af5e090cd813dba68ab0fed"));
            checkpoints.put(207992, Sha256Hash.wrap("00000000000d85c22be098f74576ef00b7aa00c05777e966aff68a270f1e01a5"));
            checkpoints.put(312645, Sha256Hash.wrap("0000000000059dcb71ad35a9e40526c44e7aae6c99169a9e7017b7d84b1c2daf"));
            checkpoints.put(407452, Sha256Hash.wrap("000000000003c6a87e73623b9d70af7cd908ae22fee466063e4ffc20be1d2dbc"));
            checkpoints.put(523412, Sha256Hash.wrap("000000000000e54f036576a10597e0e42cc22a5159ce572f999c33975e121d4d"));
            checkpoints.put(523930, Sha256Hash.wrap("0000000000000bccdb11c2b1cfb0ecab452abf267d89b7f46eaf2d54ce6e652c"));
            checkpoints.put(750000, Sha256Hash.wrap("00000000000000b4181bbbdddbae464ce11fede5d0292fb63fdede1e7c8ab21c"));
            checkpoints.put(807085, Sha256Hash.wrap("a39e69b248f2ecf4b3a0d881722d339ba14dc6c4e28a88f1e35eb4b3aef05b82"));
            checkpoints.put(934502, Sha256Hash.wrap("000000000000046b287f99b4af92dccc7df328677c4eab94880ec39c1e8c9042"));
        }

        addrSeeds = null;
        httpSeeds = null;
        if(Constants.TEST){
            nCriticalHeight = CoinDefinition.TEST_SAFE_BRANCH_HEIGHT;
            startSposHeight = CoinDefinition.TEST_START_SPOS_HEIGHT;
            adjustMinRewardHeight = CoinDefinition.TEST_ADJUST_MIN_REWARD_HEIGHT;
            forbidOldVersionHeight = CoinDefinition.TEST_FORBID_OLD_VERSION_HEIGHT;
            minIncentives = CoinDefinition.TEST_MIN_INCENTIVES;
            sposMinIncentives = CoinDefinition.TEST_SPOS_MIN_INCENTIVES;
            sposStartDeterministicMasternodeHeight = CoinDefinition.test_sposStart_deterministic_masternode_height;
        } else {
            nCriticalHeight = CoinDefinition.SAFE_BRANCH_HEIGHT;
            startSposHeight = CoinDefinition.START_SPOS_HEIGHT;
            adjustMinRewardHeight = CoinDefinition.ADJUST_MIN_REWARD_HEIGHT;
            forbidOldVersionHeight = CoinDefinition.FORBID_OLD_VERSION_HEIGHT;
            minIncentives = CoinDefinition.MIN_INCENTIVES;
            sposMinIncentives = CoinDefinition.SPOS_MIN_INCENTIVES;
            sposStartDeterministicMasternodeHeight = CoinDefinition.sposStart_deterministic_masternode_height;
        }

    }

    private static MainNetParams instance;

    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
