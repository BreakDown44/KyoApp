package de.scangolf.core.util;

/** Wachsende float-Liste ohne Autoboxing. */
public final class FloatList {

    private float[] data;
    private int size;

    public FloatList() {
        this(32);
    }

    public FloatList(int capacity) {
        data = new float[Math.max(4, capacity)];
    }

    public void add(float v) {
        if (size == data.length) {
            float[] n = new float[data.length * 2];
            System.arraycopy(data, 0, n, 0, size);
            data = n;
        }
        data[size++] = v;
    }

    public int size() {
        return size;
    }

    public float get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("Index " + i);
        }
        return data[i];
    }

    public float[] toArray() {
        float[] r = new float[size];
        System.arraycopy(data, 0, r, 0, size);
        return r;
    }
}
