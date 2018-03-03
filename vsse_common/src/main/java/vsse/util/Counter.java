package vsse.util;

public class Counter {

    private int i = 0;

    public int inc() {
        synchronized (this) {
            return ++i;
        }
    }

    public int peek() {
        return i;
    }
}
