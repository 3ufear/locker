package ru.locker.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import ru.locker.domain.ThreadLocksHolder;
import ru.locker.exception.PossibleDeadLockException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

import static java.lang.Thread.currentThread;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * Simple lock preventor, based on locks ordering
 * Can find simple locks, when A wait B and B wait A only ob 2 threads
 *
 * @param <ID>
 */
@Slf4j
@SuppressWarnings("java:S119")
public class ListBasedDeadLockPreventor<ID> implements DeadLockPreventor<ID> {

    ThreadLocksHolder<ID> lockHolder = new ThreadLocksHolder<>();

    @Override
    public boolean registerLock(ID to, BooleanSupplier supplier) {
        if (to != null) {
            synchronized (this) {
                if (!canLock(to)) {
                    log.warn("Possible deadlock");
                    throw new PossibleDeadLockException();
                }
                lockHolder.updateLocks(to);
            }
        }
        return supplier.getAsBoolean();
    }

    @Override
    public void deregisterLock(ID to, Runnable supplier) {
           if (to != null) {
               lockHolder.deleteThreadLocks(to);
           }
           supplier.run();
    }

    private boolean canLock(ID to) {
        List<List<ID>> currentLocks = lockHolder.findLocksBlockedWithCurrentLock(to);
        if (isEmpty(currentLocks)) return true;

        List<ID> myLocks = lockHolder.findLockByThreadId(currentThread().getId());
        if (isEmpty(myLocks)) return true;

        List<ID> tmpLocks = new ArrayList<>(myLocks);
        tmpLocks.add(to);

        for (List<ID> tmpList : currentLocks.stream().filter(l -> l.size() > 1).collect(toList())) {
            if (!locksInRightOrder(tmpList, tmpLocks)) {
                return false;
            }
        }
        return true;

    }

    private boolean locksInRightOrder(List<ID> firstLocks, List<ID> secondLocks) {
        Collection<ID> intersections = CollectionUtils.intersection(firstLocks, secondLocks);
        //intersected id, must be always greater
        //Maybe better to use trees
        int firstListId = 0;
        int secondListId = 0;
        for (ID id : intersections) {
            int tmpFirstId = firstLocks.indexOf(id);
            if (checkOrder(firstListId, tmpFirstId)) {
                firstListId = tmpFirstId;
            } else {
                return false;
            }

            int tmpSecondId = secondLocks.indexOf(id);
            if (checkOrder(secondListId, tmpSecondId)) {
                secondListId  = tmpSecondId;
            } else {
                return false;
            }

        }
        return true;
    }

    private boolean checkOrder(int previous, int next) {
        return previous <= next;
    }

}
