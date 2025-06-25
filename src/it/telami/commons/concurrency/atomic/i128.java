package it.telami.commons.concurrency.atomic;

import it.telami.commons.util.OperatingSystem;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Class representing an integer <b>128-bit</b> long supporting many atomic operations. <br>
 * The <b><i>high</i></b> long represents the highest priority bits, vice versa for the
 * <b><i>low</i></b> long. <br>
 * Instancing a new i128 may require an {@link Arena} for defining its scope, if not, a
 * {@link Arena#ofAuto() default arena} is used. <br>
 * The same goes for the {@link ByteOrder}, the default byte order is {@link ByteOrder#nativeOrder()}
 * and it's necessary for any operation that interacts directly with part of the bits like
 * {@link i128#add(i128) addition} or a {@link Number#longValue() cast}. <br>
 * The instance may also accept an initial value, the default initial value is 0. <br>
 * Taking this {@link Number} as a parameter is equal to passing the reference, not the copy! <br>
 * Deserialized objects will always be re-constructed using the {@link Arena#ofAuto() default arena}
 * because out-of-scope. (<b>This will change after ScopedValues release, trying to detect if there
 * is a valid arena currently in parent scopes</b>)
 * <h2> Bits: priority and significance </h2>
 * Deriving a 128-bit number from a system with a byte order equal to {@link ByteOrder#LITTLE_ENDIAN Little Endian}
 * will result in something like this:
 * <pre> {@code
 * //Representing 128-bit '1' in memory...
 * long[] i128_longs = new long[2];
 * i128_bytes[0] = 1L;
 * //In little endian the first long represents
 * //the LEAST significant bits, but, since they
 * //are in the first position, they have the
 * //HIGHEST priority!
 * i128 var = new i128(
 *         ByteOrder.LITTLE_ENDIAN,
 *         i128_bytes[0],
 *         i128_bytes[1]);
 * } </pre>
 * <br>
 * While in a system with a byte order equal to {@link ByteOrder#BIG_ENDIAN Big Endian}:
 * <pre> {@code
 * //Representing 128-bit '1' in memory...
 * long[] i128_longs = new long[2];
 * i128_bytes[1] = 1L;
 * //In big endian the first long represents
 * //the MOST significant bits, and this is
 * //why '1' is present in the LOWEST priority!
 * i128 var = new i128(
 *         ByteOrder.BIG_ENDIAN,
 *         i128_bytes[0],
 *         i128_bytes[1]);
 * } </pre>
 * <br>
 * For granting the correctness of the operations it's important to specify the right {@link ByteOrder order}!
 * @apiNote The support for some atomic operations is restricted depending on the {@link OperatingSystem}.
 * @author Telami
 * @since 1.0.1
 */
@SuppressWarnings("JavadocDeclaration")
public final class i128 extends Number implements Comparable<i128> {
    private static final Arena defaultArena = Arena.ofAuto();
    private static final StructLayout I128 = MemoryLayout.structLayout(
            JAVA_LONG.withName("high"),
            JAVA_LONG.withName("low"));

    /**
     * Common coordinate to use when getting the <b>long</b>
     * value of <i>high</i> and <i>low</i> variables.
     * @author Telami
     * @since 1.0.1
     */
    public static final long COORDINATE = 0L;
    /**
     * {@link VarHandle} for working with the <i>high</i> <b>long</b> variable.
     * @author Telami
     * @since 1.0.1
     */
    public static final VarHandle i128_high = I128
            .varHandle(MemoryLayout
                    .PathElement
                    .groupElement("high"));
    /**
     * {@link VarHandle} for working with the <i>low</i> <b>long</b> variable.
     * @author Telami
     * @since 1.0.1
     */
    public static final VarHandle i128_low = I128
            .varHandle(MemoryLayout
                    .PathElement
                    .groupElement("low"));

    //Used for avoiding branches based on the byte order
    private final VarHandle mostSignificantBits;
    private final VarHandle leastSignificantBits;
    //Native memory segment
    private final MemorySegment i128;


    /**
     * Create a new instance using the default
     * values as described by {@link i128}.
     * @author Telami
     * @since 1.0.1
     */
    public i128 () {
        this(defaultArena, ByteOrder.nativeOrder());
    }
    /**
     * Create a new instance given an {@link Arena} and using
     * the other default values as described by {@link i128}.
     * @param arena the {@link Arena} where to instance this {@link Number}
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final Arena arena) {
        this(arena, ByteOrder.nativeOrder());
    }
    /**
     * Create a new instance given the {@link ByteOrder} and using
     * the other default values as described by {@link i128}.
     * @param order the {@link ByteOrder} used for choosing the most significant bits
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final ByteOrder order) {
        this(defaultArena, order);
    }
    /**
     * Create a new instance given an {@link Arena}, the
     * {@link ByteOrder} and a default value of 0.
     * @param arena the {@link Arena} where to instance this {@link Number}
     * @param order the {@link ByteOrder} used for choosing the most significant bits
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final Arena arena,
                 final ByteOrder order) {
        if (order != ByteOrder.LITTLE_ENDIAN) {
            mostSignificantBits = i128_high;
            leastSignificantBits = i128_low;
        } else {
            mostSignificantBits = i128_low;
            leastSignificantBits = i128_high;
        }
        i128 = arena.allocate(I128);
    }

    /**
     * Create a new instance given the <i>high</i> and <i>low</i>
     * longs and using the default values as described by {@link i128}.
     * @param high the highest priority bits of the initial value
     * @param low the lowest priority bits of the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final long high,
                 final long low) {
        this(defaultArena, ByteOrder.nativeOrder(), high, low);
    }
    /**
     * Create a new instance given an {@link Arena}, the <i>high</i>
     * and <i>low</i> longs and using the default values as described
     * by {@link i128}.
     * @param arena the {@link Arena} where to instance this {@link Number}
     * @param high the highest priority bits of the initial value
     * @param low the lowest priority bits of the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final Arena arena,
                 final long high,
                 final long low) {
        this(arena, ByteOrder.nativeOrder(), high, low);
    }
    /**
     * Create a new instance given the {@link ByteOrder}, the <i>high</i>
     * and <i>low</i> longs and using the default values as described
     * by {@link i128}.
     * @param order the {@link ByteOrder} used for choosing the most significant bits
     * @param high the highest priority bits of the initial value
     * @param low the lowest priority bits of the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final ByteOrder order,
                 final long high,
                 final long low) {
        this(defaultArena, order, high, low);
    }
    /**
     * Create a new instance given an {@link Arena}, the {@link ByteOrder}
     * and the <i>high</i> and <i>low</i> longs.
     * @param arena the {@link Arena} where to instance this {@link Number}
     * @param order the {@link ByteOrder} used for choosing the most significant bits
     * @param high the highest priority bits of the initial value
     * @param low the lowest priority bits of the initial value
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final Arena arena,
                 final ByteOrder order,
                 final long high,
                 final long low) {
        if (order != ByteOrder.LITTLE_ENDIAN) {
            mostSignificantBits = i128_high;
            leastSignificantBits = i128_low;
        } else {
            mostSignificantBits = i128_low;
            leastSignificantBits = i128_high;
        }
        i128 = arena.allocate(I128);
        set(high, low);
    }

    /**
     * Create a new instance given the {@link i128 initial value}
     * and using the default values as described by {@link i128}.
     * @param src the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final i128 src) {
        this(defaultArena, ByteOrder.nativeOrder(), src);
    }
    /**
     * Create a new instance given an {@link Arena}, the
     * {@link i128 initial value} and using the default values
     * as described by {@link i128}.
     * @param arena the {@link Arena} where to instance this {@link Number}
     * @param src the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final Arena arena,
                 final i128 src) {
        this(arena, ByteOrder.nativeOrder(), src);
    }
    /**
     * Create a new instance given the {@link ByteOrder}, the
     * {@link i128 initial value} and using the default values
     * as described by {@link i128}.
     * @param order the {@link ByteOrder} used for choosing the most significant bits
     * @param src the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final ByteOrder order,
                 final i128 src) {
        this(defaultArena, order, src);
    }
    /**
     * Create a new instance given an {@link Arena}, the {@link ByteOrder}
     * and the {@link i128 initial value}.
     * @param arena the {@link Arena} where to instance this {@link Number}
     * @param order the {@link ByteOrder} used for choosing the most significant bits
     * @param src the initial value
     * @author Telami
     * @since 1.0.1
     */
    public i128 (final Arena arena,
                 final ByteOrder order,
                 final i128 src) {
        if (order != ByteOrder.LITTLE_ENDIAN) {
            mostSignificantBits = i128_high;
            leastSignificantBits = i128_low;
        } else {
            mostSignificantBits = i128_low;
            leastSignificantBits = i128_high;
        }
        i128 = arena.allocate(I128);
        copyFrom(src);
    }


    /**
     * Copy the value from the given <i>source</i>.
     * @param src the given source
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void copyFrom (final i128 src) {
        i128.copyFrom(src.i128);
    }

    /**
     * Return the <i>high</i> long.
     * @return the highest priority bits
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public long getHigh () {
        return (long) i128_high.get(i128, COORDINATE);
    }
    /**
     * Return the <i>low</i> long.
     * @return the lowest priority bits
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public long getLow () {
        return (long) i128_low.get(i128, COORDINATE);
    }

    /**
     * Set a new <i>high</i> long.
     * @param high the highest priority bits
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void setHigh (final long high) {
        i128_high.set(i128, COORDINATE, high);
    }
    /**
     * Set a new <i>low</i> long.
     * @param low the lowest priority bits
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void setLow (final long low) {
        i128_low.set(i128, COORDINATE, low);
    }
    /**
     * Set the new <i>high</i> and <i>low</i> longs.
     * @param high the highest priority bits
     * @param low the lowest priority bits
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void set (final long high,
                     final long low) {
        i128_high.set(i128, COORDINATE, high);
        i128_low.set(i128, COORDINATE, low);
    }

    /**
     * Perform a <b>non-atomic</b> 128-bit unbranched addition
     * using the given <i>addend</i>.
     * @param addend the least significant bits to add
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void add (final long addend) {
        add(0L, addend);
    }
    /**
     * Perform a <b>non-atomic</b> 128-bit unbranched addition
     * using the given bits.
     * @param msb the most significant bits to add
     * @param lsb the least significant bits to add
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void add (final long msb, /* Most Significant Bits */
                     final long lsb /* Least Significant Bits */) {
        final long x;
        mostSignificantBits.set(i128, COORDINATE,
                (long) mostSignificantBits
                        .get(i128, COORDINATE)
                        + msb
                        + ((((x = (long) leastSignificantBits
                        .get(i128, COORDINATE)) >>> 1)
                        + (lsb >>> 1 ^ lsb >> 63)
                        + (lsb >>> 63))
                        + ((x & 1L)
                        + (lsb & 1L ^ lsb >> 63)
                        + (lsb >>> 63)
                        >> 1)
                        >> 63
                        & (lsb >> 63 | 1)));
        leastSignificantBits.set(i128, COORDINATE, x + lsb);
    }
    /**
     * Performs a <b>non-atomic</b> 128-bit unbranched addition
     * using the given <i>addend</i>.
     * @param addend the {@link i128 128-bit} addend
     * @author Telami
     * @since 1.0.1
     */
    public void add (final i128 addend) {
        add((long) mostSignificantBits
                        .get(addend.i128, COORDINATE),
                (long) leastSignificantBits
                        .get(addend.i128, COORDINATE));
    }


    private static final MethodHandle windows_compareAndSet_i128;
    private static final UnsupportedOperationException windowsException;
    private static final MethodHandle getOpaque_i128;
    private static final MethodHandle getAcquire_i128;
    private static final MethodHandle getVolatile_i128;
    private static final MethodHandle compareAndSet_i128;
    private static final MethodHandle weakCompareAndSetRelease_i128;
    private static final MethodHandle setOpaque_i128;
    private static final MethodHandle setRelease_i128;
    private static final MethodHandle setVolatile_i128;

    static {
        try {
            final String path;
            if ((path = i128
                    .class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath()
                    .substring(1))
                    .endsWith(".jar")) {
                String resource;
                try (final InputStream is = i128.class.getResourceAsStream(resource
                        = "/natives/"
                        + switch (OperatingSystem.currentOS) {
                    case WINDOWS -> "atomic128.dll";
                    case LINUX -> "atomic128.so";
                    case MAC -> "atomic128.dylib";
                    default -> throw new IllegalStateException("Operating System not supported: " + OperatingSystem.currentOS);
                })) {
                    if (is != null) {
                        final File f;
                        if (!(f = new File(resource = path.substring(
                                0,
                                path.lastIndexOf('/') + 1)
                                + "TelLib"
                                + resource))
                                .exists())
                            if (f.getParentFile().mkdirs()) {
                                if (!f.createNewFile())
                                    throw new IllegalStateException("Cannot create base files!");
                            } else throw new IllegalStateException("Cannot create directories!");
                        try (final BufferedOutputStream bos = new BufferedOutputStream(
                                new FileOutputStream(f),
                                is.available())) {
                            bos.write(is.readAllBytes());
                            bos.flush();
                        }
                        System.load(resource);
                    } else throw new IllegalStateException("Native files not found!");
                }
            } else System.load(path + switch (OperatingSystem.currentOS) {
                case WINDOWS -> "atomic128.dll";
                case LINUX -> "atomic128.so";
                case MAC -> "atomic128.dylib";
                default -> throw new IllegalStateException("Operating System not supported: " + OperatingSystem.currentOS);
            });
            final Linker linker = Linker.nativeLinker();
            final SymbolLookup sl = SymbolLookup.loaderLookup();
            windows_compareAndSet_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? null
                    : linker
                    .downcallHandle(
                            sl.findOrThrow("compareAndSet_i128"),
                            FunctionDescriptor.of(
                                    JAVA_BOOLEAN,
                                    ADDRESS,
                                    ADDRESS,
                                    ADDRESS));
            windowsException = new UnsupportedOperationException("Not supported on Windows!");
            getOpaque_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("getOpaque_i128"),
                            FunctionDescriptor.ofVoid(
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            getAcquire_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("getAcquire_i128"),
                            FunctionDescriptor.ofVoid(
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            getVolatile_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("getVolatile_i128"),
                            FunctionDescriptor.ofVoid(
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            compareAndSet_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("compareAndSet_i128"),
                            FunctionDescriptor.of(
                                    JAVA_BOOLEAN,
                                    ADDRESS,
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            weakCompareAndSetRelease_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("weakCompareAndSetRelease_i128"),
                            FunctionDescriptor.of(
                                    JAVA_BOOLEAN,
                                    ADDRESS,
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            setOpaque_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("setOpaque_i128"),
                            FunctionDescriptor.ofVoid(
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            setRelease_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("setRelease_i128"),
                            FunctionDescriptor.ofVoid(
                                    ADDRESS,
                                    ADDRESS))
                    : null;
            setVolatile_i128 = OperatingSystem
                    .currentOS
                    != OperatingSystem
                    .WINDOWS
                    ? linker
                    .downcallHandle(
                            sl.findOrThrow("setVolatile_i128"),
                            FunctionDescriptor.ofVoid(
                                    ADDRESS,
                                    ADDRESS))
                    : null;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Get as described in {@link VarHandle#getOpaque(Object...)}
     * the current value and put it in the given {@link i128 result container}.
     * @param result the result container
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @author Telami
     * @since 1.0.1
     */
    public void getOpaque (final i128 result) {
        if (getOpaque_i128 != null) try {
            getOpaque_i128.invokeExact(i128, result.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }
    /**
     * Get as described in {@link VarHandle#getAcquire(Object...)}
     * the current value and put it in the given {@link i128 result container}.
     * @param result the result container
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @author Telami
     * @since 1.0.1
     */
    public void getAcquire (final i128 result) {
        if (getAcquire_i128 != null) try {
            getAcquire_i128.invokeExact(i128, result.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }
    /**
     * Get as described in {@link VarHandle#getVolatile(Object...)}
     * the current value and put it in the given {@link i128 result container}.
     * @param result the result container
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @author Telami
     * @since 1.0.1
     */
    public void getVolatile (final i128 result) {
        if (getVolatile_i128 != null) try {
            getVolatile_i128.invokeExact(i128, result.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }

    /**
     * Act as described in {@link VarHandle#compareAndSet(Object...)}.
     * @param expectedValue the expected current value
     * @param newValue the value to set if the operation succeed
     * @return {@code true} if the current value matched with
     *         the <i>expected value</i>, {@code false} otherwise
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public boolean compareAndSet (final i128 expectedValue,
                                  final i128 newValue) {
        try {
            return (boolean) (compareAndSet_i128 != null
                    ? compareAndSet_i128
                    : windows_compareAndSet_i128)
                    .invokeExact(
                            i128,
                            expectedValue.i128,
                            newValue.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }
    /**
     * Act as described in {@link VarHandle#weakCompareAndSetRelease(Object...)}.
     * @param expectedValue the expected current value
     * @param newValue the value to set if the operation succeed
     * @return {@code true} if the current value matched with
     *         the <i>expected value</i>, {@code false} otherwise
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public boolean weakCompareAndSetRelease (final i128 expectedValue,
                                             final i128 newValue) {
        if (weakCompareAndSetRelease_i128 != null) try {
            return (boolean) weakCompareAndSetRelease_i128.invokeExact(
                    i128,
                    expectedValue.i128,
                    newValue.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }

    /**
     * Set as described in {@link VarHandle#setOpaque(Object...)}.
     * @param newValue the new current value
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void setOpaque (final i128 newValue) {
        if (setOpaque_i128 != null) try {
            setOpaque_i128.invokeExact(i128, newValue.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }
    /**
     * Set as described in {@link VarHandle#setRelease(Object...)}.
     * @param newValue the new current value
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void setRelease (final i128 newValue) {
        if (setRelease_i128 != null) try {
            setRelease_i128.invokeExact(i128, newValue.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }
    /**
     * Set as described in {@link VarHandle#setVolatile(Object...)}.
     * @param newValue the new current value
     * @apiNote Not supported on {@link OperatingSystem#WINDOWS}
     * @see i128
     * @author Telami
     * @since 1.0.1
     */
    public void setVolatile (final i128 newValue) {
        if (setVolatile_i128 != null) try {
            setVolatile_i128.invokeExact(i128, newValue.i128);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } else throw windowsException;
    }


    public int intValue () {
        return (int) longValue();
    }
    public long longValue () {
        return (long) leastSignificantBits.get(i128, COORDINATE);
    }
    public float floatValue () {
        return (float) longValue();
    }
    public double doubleValue () {
        return (double) longValue();
    }

    public int compareTo (final i128 o) {
        return i128.asByteBuffer().compareTo(o.i128.asByteBuffer());
    }
}
