package ru.locker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("java:S119")
public class LockHelper<ID> {

    private final DeadLockPreventor<ID> deadLockPreventor;

    public boolean tryLock(Long timeout, final Lock lock, ID id) {
        if (timeout > 0) {
            return tryLockWithTimeout(timeout, lock, id);
        } else {
            return deadLockPreventor.registerLock(id, () -> {
                lock.lock();
                return true;
            });
        }
    }

    public void unlock(ID id, Lock lock) {
        deadLockPreventor.deregisterLock(id, lock::unlock);
    }

    @SuppressWarnings("java:S2142")
    private boolean tryLockWithTimeout(Long timeout, Lock lock, ID id) {
        return deadLockPreventor.registerLock(id, () -> {
            try {
                return lock.tryLock(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                deadLockPreventor.deregisterLock(id, lock::unlock);
                log.info("Can't acquire lock", e);
                return false;
            }
        });
    }

}
