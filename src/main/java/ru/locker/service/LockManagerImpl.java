package ru.locker.service;

import lombok.extern.slf4j.Slf4j;
import ru.locker.domain.LockType;
import ru.locker.domain.Lockable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.*;
import java.util.function.Function;


@Slf4j
@SuppressWarnings("java:S119")
public class LockManagerImpl<E extends Lockable<ID>, ID> implements LockManager<E, ID> {

    //locks
    private final Map<ID, ReadWriteLock> locks = new ConcurrentHashMap<>();

    private final LockHelper<ID> locker;
    private final GlobalLockResolver globalLockResolver;

    public LockManagerImpl(DeadLockPreventor<ID> deadLockPreventor) {
        this.locker = new LockHelper<>(deadLockPreventor);
        this.globalLockResolver = new GlobalLockResolver();
    }

    @Override
    public <R> R execute(E entity, LockType lockType, Function<E, R> function) {
        try {
            return exec(entity, lockType, 0L, function);
        } catch (TimeoutException e) {
            //Impossible case
            log.error("Unexpected exception:", e);
            //Todo: throw some another exception, to disable sonarlint warning
            throw new RuntimeException(e);
        }
    }

    @Override
    public <R> R execute(E entity, LockType lockType, Long timeout, Function<E, R> function) throws TimeoutException {
        return exec(entity, lockType, timeout, function);
    }

    private <R> R exec(E entity, LockType lockType, Long timeout, Function<E, R> function) throws
            TimeoutException {
        log.debug("Start executing for id {}", entity.getId());

        var rwLock = locks.computeIfAbsent(entity.getId(), id -> new ReentrantReadWriteLock());
        var lock = globalLockResolver.getLock(rwLock, lockType);
        if (locker.tryLock(timeout, lock, entity.getId())) {
            globalLockResolver.checkForGlobalWaiting(lockType);
            try {
                R result = function.apply(entity);
                log.debug("Finish executing for id {}", entity.getId());
                return result;
            } finally {
                log.debug("Unlocking entity witj id {}", entity.getId());
                globalLockResolver.processGlobalUnlocking(lockType);
                locker.unlock(entity.getId(), lock);
            }
        } else {
            throw new TimeoutException("Failed to acquire the lock in the specified time");
        }
    }





}
