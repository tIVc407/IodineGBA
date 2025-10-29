package com.iodinegba.core;

import com.iodinegba.utils.TypedArrays;
import java.nio.ByteBuffer;

public class GameBoyAdvanceFIFO {
    private int count = 0;
    private int position = 0;
    private ByteBuffer buffer = TypedArrays.getUint8Array(0x20);

    public void push(int sample) {
        int writePosition = (position + count);
        buffer.put(writePosition & 0x1F, (byte) (sample & 0xFF));
        if (count < 0x20) {
            ++count;
        }
    }

    public void push8(int sample) {
        push(sample);
        push(sample);
        push(sample);
        push(sample);
    }

    public void push16(int sample) {
        push(sample);
        push(sample >> 8);
        push(sample);
        push(sample >> 8);
    }

    public void push32(int sample) {
        push(sample);
        push(sample >> 8);
        push(sample >> 16);
        push(sample >> 24);
    }

    public int shift() {
        int output = 0;
        if (count > 0) {
            --count;
            output = buffer.get(position & 0x1F) << 3;
            position = (position + 1) & 0x1F;
        }
        return output;
    }

    public boolean requestingDMA() {
        return count <= 0x10;
    }

    public int samplesUntilDMATrigger() {
        return count - 0x10;
    }

    public void clear() {
        count = 0;
    }
}
