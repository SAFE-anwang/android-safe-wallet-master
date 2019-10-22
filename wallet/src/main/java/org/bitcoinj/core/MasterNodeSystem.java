package org.bitcoinj.core;

import org.darkcoinj.ActiveMasterNode;
import org.darkcoinj.MasterNodePayments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.*;

/**
 * Created by Eric on 2/8/2015.
 */
public class MasterNodeSystem {

    private static final Logger log = LoggerFactory.getLogger(MasterNodeSystem.class);
    public final static int MASTERNODE_NOT_PROCESSED              = 0; // initial state
    public final static int MASTERNODE_IS_CAPABLE                 = 1;
    public final static int MASTERNODE_NOT_CAPABLE                = 2;
    public final static int MASTERNODE_STOPPED                    = 3;
    public final static int MASTERNODE_INPUT_TOO_NEW              = 4;
    public final static int MASTERNODE_PORT_NOT_OPEN              = 6;
    public final static int MASTERNODE_PORT_OPEN                  = 7;
    public final static int MASTERNODE_SYNC_IN_PROCESS            = 8;
    public final static int MASTERNODE_REMOTELY_ENABLED           = 9;

    public final static int MASTERNODE_MIN_CONFIRMATIONS          = 15;
    public final static int MASTERNODE_MIN_DSEEP_SECONDS          = (30*60);
    public final static int MASTERNODE_MIN_DSEE_SECONDS           = (5*60);
    public final static int MASTERNODE_PING_SECONDS               = 60;
    public final static int MASTERNODE_EXPIRATION_SECONDS         = (65*60) ;
    public final static int MASTERNODE_REMOVAL_SECONDS            = (70*60);

    public static MasterNodeSystem mns;
    public static MasterNodeSystem get() {
        if(mns == null)
            mns = new MasterNodeSystem();
        return mns;
    }
}
