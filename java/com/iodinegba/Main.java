package com.iodinegba;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import com.iodinegba.core.CoreExposed;
import com.iodinegba.core.IOCore;

public class Main extends JPanel implements CoreExposed {
    private static final int GBA_WIDTH = 240;
    private static final int GBA_HEIGHT = 160;
    private static final int SCALE = 3;

    private IOCore ioCore;

    public Main() {
        this.ioCore = new IOCore(this);
        this.ioCore.sound.initialize();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("IodineGBA");
        Main panel = new Main();
        frame.add(panel);
        frame.setSize(GBA_WIDTH * SCALE, GBA_HEIGHT * SCALE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    @Override
    public void outputAudio(int left, int right) {
        // TODO: Implement audio output
    }
}
