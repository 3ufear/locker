package ru.locker.service;

import java.util.function.BooleanSupplier;

@SuppressWarnings("java:S119")
public interface DeadLockPreventor<ID> {

    boolean registerLock(ID id, BooleanSupplier locker);

    void deregisterLock(ID id, Runnable locker);
}
