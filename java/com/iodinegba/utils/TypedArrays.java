package com.iodinegba.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class TypedArrays {
    public static ByteBuffer getInt8Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }

    public static ByteBuffer getUint8Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }

    public static ShortBuffer getInt16Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.asShortBuffer();
    }

    public static ShortBuffer getUint16Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.asShortBuffer();
    }

    public static IntBuffer getInt32Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.asIntBuffer();
    }

    public static IntBuffer getUint32Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.asIntBuffer();
    }

    public static FloatBuffer getFloat32Array(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.asFloatBuffer();
    }
}
