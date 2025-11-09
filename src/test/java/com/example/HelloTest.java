package com.example;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HelloTest {
    @Test
    public void testAdd() {
        Hello hello = new Hello();
        assertEquals(5, hello.add(2, 3));
    }
}

