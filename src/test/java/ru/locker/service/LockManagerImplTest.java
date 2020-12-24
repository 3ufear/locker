package ru.locker.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import ru.locker.domain.CustomEntity;
import ru.locker.domain.LockType;
import ru.locker.exception.PossibleDeadLockException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;

@Slf4j
public class LockManagerImplTest {

    LockManagerImpl<CustomEntity, Integer> lockService = new LockManagerImpl<>(
            new ListBasedDeadLockPreventor<>()
    );

    @Test
    public void testExecuteWithLock() {
        var customEntity = new CustomEntity(1, 0);

        int tasksCount = 100000;
        CountDownLatch latch = new CountDownLatch(tasksCount);

        ExecutorService executor = newFixedThreadPool(100);
        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                try {
                    lockService.execute(customEntity, LockType.WRITE, this::execute);
                } catch (Throwable t) {
                    log.error("t", t);
                } finally {
                    latch.countDown();
                }
            });

        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Exception thrown:", e);
            Assert.fail();
        }

        assertEquals(100000, customEntity.getPayload().longValue());

    }

    @Test
    public void testExecuteWithGlobalLock() {
        var firstEntity = new CustomEntity(1, 0);
        var secondEntity = new CustomEntity(2, 0);

        int tasksCount = 50;
        CountDownLatch latch = new CountDownLatch(tasksCount*2);

        ExecutorService executor = newFixedThreadPool(200);
        long start = currentTimeMillis();

        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                try {
                    lockService.execute(firstEntity, LockType.GLOBAL, this::executeAndSleep);
                } finally {
                    latch.countDown();
                }
            });
        }
        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                try {
                    lockService.execute(secondEntity, LockType.WRITE, this::executeAndSleep);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Exception thrown:", e);
            Assert.fail();
        }

        assertThat("timestamp",
                currentTimeMillis() - start,
                greaterThan(10000L));

    }

    @Test
    public void testExecuteWithLockAndTimeout() {
        var customEntity = new CustomEntity(1, 0);

        int tasksCount = 10;
        CountDownLatch latch = new CountDownLatch(tasksCount);
        Set<Future> tasks = new HashSet<>();
        ExecutorService executor = newFixedThreadPool(200);
        for (int i = 0; i < tasksCount; i++) {
            Future future = executor.submit(() -> {
                try {
                    lockService.execute(customEntity, LockType.WRITE, 10L, this::executeAndSleep);
                } catch (TimeoutException e) {
                    throw new RuntimeException("Test fail");
                } finally {
                    latch.countDown();
                }
            });
            tasks.add(future);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Exception thrown:", e);
            Assert.fail();
        }
        boolean exceptionThrown = false;
        for (Future task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                log.error("Exception thrown:", e);
                Assert.fail();
            } catch (ExecutionException e) {
                exceptionThrown = true;
                break;
            }
        }
        Assert.assertTrue(exceptionThrown);

    }


    @Test
    //@Ignore("Bad test, but is first idea how to show || processing for 2 entities")
    public void testLock2DifferentEntities() {

        var firstEntity = new CustomEntity(1, 0);
        var secondEntity = new CustomEntity(2, 0);

        int tasksCount = 50;
            CountDownLatch latch = new CountDownLatch(tasksCount * 2);

        ExecutorService executor = newFixedThreadPool(200);
        long start = currentTimeMillis();

        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                try {
                    lockService.execute(firstEntity, LockType.WRITE, this::executeAndSleep);
                } finally {
                    latch.countDown();
                }
            });
        }
        for (int i = 0; i < tasksCount; i++) {
            executor.submit(() -> {
                lockService.execute(secondEntity, LockType.WRITE, this::executeAndSleep);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Exception thrown:", e);
            Assert.fail();
        }

        assertThat("timestamp",
                currentTimeMillis() - start,
                lessThan(10000L));

    }

    @Test
    // @Ignore("Prevention from deadlocks hasn't been implemented")
    public void testDeadLocks() {

        var firstEntity = new CustomEntity(1, 0);
        var secondEntity = new CustomEntity(2, 0);

        int tasksCount = 10;

        ExecutorService executor = newFixedThreadPool(200);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < tasksCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    lockService.execute(firstEntity, LockType.WRITE, e ->
                            lockService.execute(secondEntity, LockType.WRITE, this::executeAndSleep));
                } catch (Throwable t) {
                    log.error("error occured:", t);
                    throw t;
                }
            }));
        }
        for (int i = 0; i < tasksCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    lockService.execute(secondEntity, LockType.WRITE, e ->
                            lockService.execute(firstEntity, LockType.WRITE, this::executeAndSleep));
                    //TODO
                } catch (Throwable t) {
                    log.error("error occured:", t);
                    throw t;
                }
            }));
        }

        for (Future future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                Assert.assertTrue(e.getCause() instanceof PossibleDeadLockException);
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }

    }

    private CustomEntity execute(CustomEntity e) {
        return execute(e, false);
    }

    private CustomEntity executeAndSleep(CustomEntity e) {
        return execute(e, true);
    }

    private CustomEntity execute(CustomEntity e, boolean sleep) {
        if (sleep) {
            try {
                sleep(100);
            } catch (InterruptedException interruptedException) {
                Assert.fail();
            }
        }
        e.setPayload(e.getPayload() + 1);
        return e;
    }

}