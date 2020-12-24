package ru.locker.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.Thread.currentThread;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@SuppressWarnings("java:S119")
public class ThreadLocksHolder<ID> {

    private final Map<Long, List<ID>> threadIdtoIdLocks = new ConcurrentHashMap<>();
    private final Map<ID, List<Long>> idToThreadIdLocks = new ConcurrentHashMap<>();

    public synchronized void updateLocks(ID to) {
        idToThreadIdLocks.compute(to,
                (k,v) ->
                {
                    if (v == null) {
                        var res = new CopyOnWriteArrayList<Long>();
                        res.add(currentThread().getId());
                        return res;
                    } else {
                        v.add(currentThread().getId());
                        return v;
                    }
                });
        threadIdtoIdLocks.compute(currentThread().getId(),
                (aLong, ids) -> {
                    if (ids == null) {
                        var res = new CopyOnWriteArrayList<ID>();
                        res.add(to);
                        return res;
                    } else {
                        ids.add(to);
                        return ids;
                    }
                }
        );
    }

    public List<ID> findLockByThreadId(Long threadId) {
        return threadIdtoIdLocks.get(threadId);
    }


    public synchronized void deleteThreadLocks(ID to) {
        idToThreadIdLocks.computeIfPresent(to, (k, v) -> {
            v.remove(currentThread().getId());
            return v;
        });
        threadIdtoIdLocks.computeIfPresent(currentThread().getId(),
                (aLong, ids) -> {
                    ids.remove(to);
                    return ids;
                }
        );
    }

    public List<List<ID>> findLocksBlockedWithCurrentLock(ID to) {
        var threadList = idToThreadIdLocks.get(to);
        if (isEmpty(threadList)) {
            return emptyList();
        }
        var res = threadList.stream().filter(thread -> !thread.equals(currentThread().getId())).collect(toList());
        if (isEmpty(res)) {
            return emptyList();
        }

        var result =new ArrayList<List<ID>>();
        res.forEach(threadId -> result.add(threadIdtoIdLocks.get(threadId)));
        return result;
    }

}
