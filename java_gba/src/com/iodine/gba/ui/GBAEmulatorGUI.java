package com.iodine.gba.ui;

import com.iodine.gba.core.GameBoyAdvanceEmulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/**
 * GBAEmulatorGUI - Swing-based GUI for the GBA emulator
 */
public class GBAEmulatorGUI extends JFrame {
    private GameBoyAdvanceEmulator emulator;
    private DisplayPanel displayPanel;
    private Timer emulatorTimer;
    private boolean running = false;

    // Key mapping
    private static final int[] KEY_CODES = {
        KeyEvent.VK_Z,      // 0: A
        KeyEvent.VK_X,      // 1: B
        KeyEvent.VK_BACK_SPACE,  // 2: Select
        KeyEvent.VK_ENTER,  // 3: Start
        KeyEvent.VK_RIGHT,  // 4: Right
        KeyEvent.VK_LEFT,   // 5: Left
        KeyEvent.VK_UP,     // 6: Up
        KeyEvent.VK_DOWN,   // 7: Down
        KeyEvent.VK_A,      // 8: R
        KeyEvent.VK_S       // 9: L
    };

    public GBAEmulatorGUI() {
        super("IodineGBA - Java Edition");

        emulator = new GameBoyAdvanceEmulator();

        setupUI();
        setupInput();
        setupEmulatorTimer();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        // Display panel (240x160 scaled 2x = 480x320)
        displayPanel = new DisplayPanel();
        displayPanel.setPreferredSize(new Dimension(480, 320));
        add(displayPanel, BorderLayout.CENTER);

        // Control panel
        JPanel controlPanel = new JPanel();
        JButton loadBIOSButton = new JButton("Load BIOS");
        JButton loadROMButton = new JButton("Load ROM");
        JButton startButton = new JButton("Start");
        JButton stopButton = new JButton("Stop");
        JButton resetButton = new JButton("Reset");

        loadBIOSButton.addActionListener(e -> loadBIOS());
        loadROMButton.addActionListener(e -> loadROM());
        startButton.addActionListener(e -> startEmulation());
        stopButton.addActionListener(e -> stopEmulation());
        resetButton.addActionListener(e -> resetEmulation());

        controlPanel.add(loadBIOSButton);
        controlPanel.add(loadROMButton);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(resetButton);

        add(controlPanel, BorderLayout.SOUTH);

        // Status panel
        JPanel statusPanel = new JPanel();
        JLabel statusLabel = new JLabel("Ready - Load BIOS and ROM to start");
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.NORTH);
    }

    private void setupInput() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                for (int i = 0; i < KEY_CODES.length; i++) {
                    if (e.getKeyCode() == KEY_CODES[i]) {
                        emulator.keyDown(i);
                        break;
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                for (int i = 0; i < KEY_CODES.length; i++) {
                    if (e.getKeyCode() == KEY_CODES[i]) {
                        emulator.keyUp(i);
                        break;
                    }
                }
            }
        });

        setFocusable(true);
        requestFocus();
    }

    private void setupEmulatorTimer() {
        // Run at approximately 60 FPS
        emulatorTimer = new Timer(16, e -> {
            if (running) {
                long timestamp = System.currentTimeMillis();
                emulator.timerCallback(timestamp);
                displayPanel.updateDisplay();
                displayPanel.repaint();
            }
        });
    }

    private void loadBIOS() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load GBA BIOS");
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File biosFile = fileChooser.getSelectedFile();
                byte[] bios = Files.readAllBytes(biosFile.toPath());
                emulator.attachBIOS(bios);
                JOptionPane.showMessageDialog(this, "BIOS loaded successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading BIOS: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadROM() {
        JFileChooser fileChooser = new JFileChooser(new File("roms"));
        fileChooser.setDialogTitle("Load GBA ROM");
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File romFile = fileChooser.getSelectedFile();
                byte[] rom = Files.readAllBytes(romFile.toPath());
                emulator.attachROM(rom);
                JOptionPane.showMessageDialog(this, "ROM loaded successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading ROM: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void startEmulation() {
        if (!running) {
            emulator.startEmulation();
            running = true;
            emulatorTimer.start();
        }
    }

    private void stopEmulation() {
        if (running) {
            running = false;
            emulatorTimer.stop();
            emulator.pause();
        }
    }

    private void resetEmulation() {
        stopEmulation();
        emulator.restart();
        startEmulation();
    }

    // Display panel for rendering GBA screen
    class DisplayPanel extends JPanel {
        private BufferedImage display;

        public DisplayPanel() {
            display = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
        }

        public void updateDisplay() {
            if (emulator.IOCore != null && emulator.IOCore.gfxRenderer != null) {
                int[] frameBuffer = emulator.IOCore.gfxRenderer.getFrameBuffer();
                display.setRGB(0, 0, 240, 160, frameBuffer, 0, 240);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Scale 2x for better visibility
            g.drawImage(display, 0, 0, 480, 320, null);
        }
    }

    public static void main(String[] args) {
        if (args.length > 1 && args[0].equals("--headless")) {
            System.out.println("Starting in headless mode...");
            try {
                String romPath = args[1];
                File romFile = new File(romPath);
                byte[] rom = Files.readAllBytes(romFile.toPath());

                GameBoyAdvanceEmulator emulator = new GameBoyAdvanceEmulator();
                emulator.attachROM(rom);

                System.out.println("ROM loaded: " + romPath);
                System.out.println("Starting emulation loop...");
                emulator.startEmulation();

                // Run the emulator loop manually
                while (true) {
                    emulator.timerCallback(System.currentTimeMillis());
                    Thread.sleep(16);
                }

            } catch (Exception e) {
                System.err.println("Headless emulation failed:");
                e.printStackTrace();
            }

        } else {
            System.out.println("Starting in GUI mode...");
            SwingUtilities.invokeLater(() -> {
                new GBAEmulatorGUI();
            });
        }
    }
}
