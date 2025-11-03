package com.iodine.gba.audio;

public class GameBoyAdvanceFIFO {
    private final byte[] buffer = new byte[32];
    private int start = 0;
    private int end = 0;
    private int count = 0;

    public void push8(int data) {
        if (count < 32) {
            buffer[end++] = (byte) data;
            if (end == 32) {
                end = 0;
            }
            count++;
        }
    }

    public void push16(int data) {
        push8(data);
        push8(data >> 8);
    }

    public void push32(int data) {
        push16(data);
        push16(data >> 16);
    }

    public int shift() {
        if (count > 0) {
            int data = buffer[start++] & 0xFF;
            if (start == 32) {
                start = 0;
            }
            count--;
            return data;
        }
        return 0;
    }

    public void clear() {
        start = 0;
        end = 0;
        count = 0;
    }

    public boolean requestingDMA() {
        return count <= 16;
    }

    public int samplesUntilDMATrigger() {
        return 16 - count;
    }
}
