package com.secondhand.platform.shared.infra;

public class SystemTimeIdGenerator implements IdGenerator {
    @Override
    public long nextId() {
        return System.currentTimeMillis();
    }
}
