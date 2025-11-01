package com.iodine.gba.graphics;

/**
 * GraphicsFrameCallback - Interface for receiving rendered frames
 */
public interface GraphicsFrameCallback {
    void onFrame(byte[] swizzledFrame);
}
