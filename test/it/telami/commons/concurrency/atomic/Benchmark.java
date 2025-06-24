package it.telami.commons.concurrency.atomic;

import it.telami.commons.util.OperatingSystem;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.SecureRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"resource", "ExtractMethodRecommender"})
final class Benchmark {
    private static final int WARMUP_CYCLES = 100_000;
    //128 -> Medium contention
    private static final int TOTAL_CYCLES = 128 + WARMUP_CYCLES;
    private static final long ADDEND = new SecureRandom().nextLong();
    //Optimization for constant additions!
    private static final long OPT = 0xffffffffffffffffL - ADDEND;

    public static void main (final String[] args) {
        libraryImpl();
        withReferenceImpl();
        withLockImpl();
    }

    private static void libraryImpl () {
        final i128 container = new i128();
        final Runnable r = OperatingSystem.currentOS == OperatingSystem.WINDOWS
                ? () -> {
            final i128 a = new i128(), b = new i128();
            for (;;) {
                a.copyFrom(container);
                if (a.getLow() > OPT)
                    b.set(a.getHigh() + 1, a.getLow() + ADDEND);
                else b.setLow(a.getLow() + ADDEND);
                VarHandle.fullFence();
                if (container.compareAndSet(a, b))
                    break;
                else Thread.yield();
            }
        } : () -> {
            final i128 a = new i128(), b = new i128();
            for (;;) {
                container.getAcquire(a);
                if (a.getLow() > OPT)
                    b.set(a.getHigh() + 1, a.getLow() + ADDEND);
                else b.setLow(a.getLow() + ADDEND);
                if (container.weakCompareAndSetRelease(a, b))
                    break;
                else Thread.yield();
            }
        };
        int i = -1;
        while (++i != WARMUP_CYCLES)
            ForkJoinPool.commonPool().execute(r);
        if (!ForkJoinPool.commonPool().awaitQuiescence(10L, TimeUnit.SECONDS))
            System.out.println("The library implementation took more than 10 seconds to warm up!");
        final long start = System.nanoTime();
        while (++i != TOTAL_CYCLES)
            ForkJoinPool.commonPool().execute(r);
        if (!ForkJoinPool.commonPool().awaitQuiescence(10L, TimeUnit.SECONDS))
            System.out.println("The library implementation took more than 10 seconds to execute!");
        final long end = System.nanoTime() - start;
        System.out.println("Library implementation: " + (end / (TOTAL_CYCLES - WARMUP_CYCLES)) + " ns/ops");
    }

    private static void withReferenceImpl () {
        final class i128_ref {
            private long high;
            private long low;
            private i128_ref () {
                high = 0L;
                low = 0L;
            }
        }
        final class ref_container {
            @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
            private i128_ref ref;
            ref_container () {
                this.ref = new i128_ref();
            }
        }
        final VarHandle refVar;
        try {
            refVar = MethodHandles
                    .lookup()
                    .findVarHandle(
                            ref_container.class,
                            "ref",
                            i128_ref.class);
        } catch (final NoSuchFieldException |
                       IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        final ref_container container = new ref_container();
        final Runnable r = () -> {
            i128_ref a;
            final i128_ref b = new i128_ref();
            for (;;) {
                a = (i128_ref) refVar.getAcquire(container);
                if (a.low > OPT) {
                    b.low = a.low + ADDEND;
                    b.high = a.high + 1;
                } else b.low = a.low + ADDEND;
                if (refVar.weakCompareAndSetRelease(container, a, b))
                    break;
                else Thread.yield();
            }
        };
        int i = -1;
        while (++i != WARMUP_CYCLES)
            ForkJoinPool.commonPool().execute(r);
        if (!ForkJoinPool.commonPool().awaitQuiescence(10L, TimeUnit.SECONDS))
            System.out.println("The reference implementation took more than 10 seconds to warm up!");
        final long start = System.nanoTime();
        while (++i != TOTAL_CYCLES)
            ForkJoinPool.commonPool().execute(r);
        if (!ForkJoinPool.commonPool().awaitQuiescence(10L, TimeUnit.SECONDS))
            System.out.println("The reference implementation took more than 10 seconds to execute!");
        final long end = System.nanoTime() - start;
        System.out.println("Reference implementation: " + (end / (TOTAL_CYCLES - WARMUP_CYCLES)) + " ns/ops");
    }

    private static void withLockImpl () {
        final class i128 {
            @SuppressWarnings("unused")
            private long high;
            private long low;
            private i128 () {
                high = 0L;
                low = 0L;
            }
        }
        final i128 ref = new i128();
        final Runnable r = () -> {
            synchronized (ref) {
                if (ref.low > OPT) {
                    ref.low += ADDEND;
                    ++ref.high;
                } else ref.low += ADDEND;
            }
        };
        int i = -1;
        while (++i != WARMUP_CYCLES)
            ForkJoinPool.commonPool().execute(r);
        if (!ForkJoinPool.commonPool().awaitQuiescence(10L, TimeUnit.SECONDS))
            System.out.println("The lock implementation took more than 10 seconds to warm up!");
        final long start = System.nanoTime();
        while (++i != TOTAL_CYCLES)
            ForkJoinPool.commonPool().execute(r);
        if (!ForkJoinPool.commonPool().awaitQuiescence(10L, TimeUnit.SECONDS))
            System.out.println("The lock implementation took more than 10 seconds to execute!");
        final long end = System.nanoTime() - start;
        System.out.println("Lock implementation: " + (end / (TOTAL_CYCLES - WARMUP_CYCLES)) + " ns/ops");
    }
}
