package ru.locker.service;

import ru.locker.domain.LockType;
import ru.locker.domain.Lockable;

import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Reusable utility class that provides synchronization mechanism similar to row-level DB locking.
 */
@SuppressWarnings("java:S119")
public interface LockManager<E extends Lockable<ID>, ID> {

    /**
     * Execute current row wih blocking on entity.id
     * @param entity - entity to block
     * @param lockType - type of lock
     * @param function - function
     */
    <R> R execute(E entity, LockType lockType, Function<E, R> function);

    /**
     * Execute current row wih blocking on entity.id
     * @param entity - entity to block
     * @param lockType - type of lock
     * @param timeout - timeout in ms waiting for acquire lock, zero is infinity wait
     * @param function - function
     */
    <R> R execute(E entity, LockType lockType, Long timeout, Function<E, R> function) throws TimeoutException;


}
