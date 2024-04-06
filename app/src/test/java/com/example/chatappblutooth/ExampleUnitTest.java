package com.example.chatappblutooth;

import org.junit.Test;

import static org.junit.Assert.*;

import java.util.UUID;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }


    @Test
    public void test(){
        String str= UUID.randomUUID().toString();
        System.out.println(str);
    }
}