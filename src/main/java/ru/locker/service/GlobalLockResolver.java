package ru.locker.service;

import lombok.SneakyThrows;
import ru.locker.domain.LockType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalLockResolver {

    //global lock
    private volatile boolean globalLockAcquired = false;
    private final AtomicInteger locksCount = new AtomicInteger();
    private final Lock globalLock = new ReentrantLock();
    private final Condition freeLock = globalLock.newCondition();
    private final Condition freeGlobalLock = globalLock.newCondition();

    public void checkForGlobalWaiting(LockType lockType) {
        if (lockType == LockType.GLOBAL) {
            waitReleaseOthers();
        } else {
            waitReleaseGlobal();
            locksCount.incrementAndGet();
        }
    }

    public void processGlobalUnlocking(LockType lockType) {
        if (lockType == LockType.GLOBAL) {
            globalLockAcquired = false;
            freeGlobalLock.signalAll();
        } else {
            locksCount.decrementAndGet();
            syncOperation(freeLock::signalAll);
        }
    }

    public Lock getLock(ReadWriteLock lock, LockType lockType) {
        switch (lockType) {
            case GLOBAL:
                return globalLock;
            case READ:
                return lock.readLock();
            case WRITE:
            default:
                return lock.writeLock();
        }
    }

    @SneakyThrows
    @SuppressWarnings({"java:S2274", "java:S2142"})
    private void waitReleaseGlobal() {
        //some microopmization
        //double check locking
        if (globalLockAcquired) {
            syncOperation(() -> {
                try {
                    freeGlobalLock.await();
                } catch (InterruptedException e) {
                    //TODO, in real world another exception
                   throw new RuntimeException();
                }
            });
        }
    }

    @SneakyThrows
    private void waitReleaseOthers() {
        globalLockAcquired = true;
        while (locksCount.get() > 0) {
            freeLock.await();
        }
    }

    private void syncOperation(Runnable apply) {
        try {
            globalLock.lock();
            while (globalLockAcquired) {
                apply.run();
            }
        } finally {
            globalLock.unlock();
        }
    }
}
