#ifdef _WIN32

#include <windows.h>
#include <intrin.h>
#include <stdbool.h>

__declspec(dllexport)
bool compareAndSet_i128 (__int64 volatile* addr,
                         __int64* expected,
                         const __int64* desired) {
    return _InterlockedCompareExchange128(
        addr,
        desired[1],
        desired[0],
        expected);
}

#else

#include <stdint.h>
#include <stdbool.h>
#include <stdatomic.h>

typedef struct {
    uint64_t low;
    uint64_t high;
} atomic128_t;

void getOpaque_i128 (const volatile atomic128_t* addr,
                     atomic128_t* out) {
    __atomic_load(addr, out, __ATOMIC_RELAXED);
}
void getAcquire_i128 (const volatile atomic128_t* addr,
                      atomic128_t* out) {
    __atomic_load(addr, out, __ATOMIC_ACQUIRE);
}
void getVolatile_i128 (const volatile atomic128_t* addr,
                       atomic128_t* out) {
    __atomic_load(addr, out, __ATOMIC_SEQ_CST);
}

bool compareAndSet_i128 (volatile atomic128_t* addr,
                         atomic128_t* expected,
                         const atomic128_t* desired) {
    return __atomic_compare_exchange(
        addr,
        expected,
        desired,
        false,
        __ATOMIC_SEQ_CST,
        __ATOMIC_SEQ_CST);
}

bool weakCompareAndSetRelease_i128 (volatile atomic128_t* addr,
                                    atomic128_t* expected,
                                    const atomic128_t* desired) {
    return __atomic_compare_exchange(
        addr,
        expected,
        desired,
        true,
        __ATOMIC_RELEASE,
        __ATOMIC_RELAXED);
}

void setOpaque_i128 (volatile atomic128_t* addr,
                     const atomic128_t* value) {
    __atomic_store(addr, value, __ATOMIC_RELAXED);
}
void setRelease_i128 (volatile atomic128_t* addr,
                      const atomic128_t* value) {
    __atomic_store(addr, value, __ATOMIC_RELEASE);
}
void setVolatile_i128 (volatile atomic128_t* addr,
                       const atomic128_t* value) {
    __atomic_store(addr, value, __ATOMIC_SEQ_CST);
}

#endif