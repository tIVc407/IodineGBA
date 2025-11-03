package com.iodine.gba;

import com.iodine.gba.core.GameBoyAdvanceEmulator;
import com.iodine.gba.ui.GBAEmulatorGUI;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--headless")) {
            runHeadless(args);
        } else {
            runGUI(args);
        }
    }

    private static void runGUI(String[] args) {
        System.out.println("Starting GUI...");
        SwingUtilities.invokeLater(() -> {
            new GBAEmulatorGUI();
        });
    }

    private static void runHeadless(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: --headless <rom_path>");
            return;
        }

        String romPath = args[1];
        System.out.println("Running in headless mode with ROM: " + romPath);

        try {
            byte[] rom = Files.readAllBytes(new File(romPath).toPath());
            GameBoyAdvanceEmulator emulator = new GameBoyAdvanceEmulator();
            emulator.startEmulation();
            emulator.attachROM(rom);

            while (true) {
                emulator.timerCallback(System.currentTimeMillis());
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to read ROM file: " + e.getMessage());
        }
    }
}
