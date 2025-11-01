package com.iodine.gba;

import com.iodine.gba.ui.GBAEmulatorGUI;

import javax.swing.SwingUtilities;

/**
 * Main - Entry point for the GBA emulator
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  IodineGBA - Java Edition");
        System.out.println("  Game Boy Advance Emulator");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("Controls:");
        System.out.println("  Arrow Keys - D-Pad");
        System.out.println("  Z - A Button");
        System.out.println("  X - B Button");
        System.out.println("  Enter - Start");
        System.out.println("  Backspace - Select");
        System.out.println("  A - R Shoulder");
        System.out.println("  S - L Shoulder");
        System.out.println();
        System.out.println("Starting emulator...");
        System.out.println();

        SwingUtilities.invokeLater(() -> {
            new GBAEmulatorGUI();
        });
    }
}
