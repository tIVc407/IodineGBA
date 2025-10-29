package com.iodinegba;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestMain {
    @Test
    public void testMainInitialization() {
        Main main = new Main();
        assertNotNull(main);
    }
}
