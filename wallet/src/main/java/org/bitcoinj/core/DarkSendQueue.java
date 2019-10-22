/**
 * Copyright 2014 Hash Engineering Solutions
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
package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.core.Utils.int64ToByteStreamLE;
import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class DarkSendQueue extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DarkSendQueue.class);

    TransactionInput vin;
    long time;
    int denom;
    boolean ready;
    byte[] vchSig;

    DarkCoinSystem system;

    DarkSendQueue() {
        denom = 0;
        vin = null;
        time = 0;
        vchSig = null;
        ready = false;
        this.system = null;
    }

    DarkSendQueue(NetworkParameters params, byte[] bytes)
    {
        super(params, bytes, 0);
    }

    DarkSendQueue(NetworkParameters params, byte[] bytes, int cursor) {
        super(params, bytes, cursor);
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;
        // jump past version (uint32)
        int cursor = offset;
        cursor += 4; //denom
        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();
        //time
        cursor += 8;
        //ready
        cursor += 1;
        //vchSig
        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        return cursor - offset;
    }

    @Override
    protected void parse() throws ProtocolException {


        cursor = offset;

        denom = (int) readUint32();


        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        time = readInt64();

        byte[] readyByte = readBytes(1);
        ready = readyByte[0] != 0 ? true : false;

        vchSig = readByteArray();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {


        uint32ToByteStreamLE(denom, stream);

        vin.bitcoinSerialize(stream);

        int64ToByteStreamLE(time, stream);

        byte data [] = new byte[1];
        data[0] = (byte)(ready ? 1 : 0);
        stream.write(data);

        stream.write(new VarInt(vchSig.length).encode());
        stream.write(vchSig);
    }

}
