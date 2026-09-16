#include "main.h"
#include "game/Streaming.h"
#include "vendor/armhook/patch.h"
#include <android/log.h>
#include <sys/stat.h>
#include <cerrno>
#include <cstdarg>
#include <climits>
#include <cstdint>
#include <limits>

static void Trace(const char* fmt, ...) {
    int saved = errno;
    va_list ap; va_start(ap, fmt);
    __android_log_vprint(ANDROID_LOG_ERROR, "VOSTOK_CDTRACE", fmt, ap);
    va_end(ap); errno = saved;
}

static thread_local bool inRead = false;
static thread_local uint32_t sector = 0, sectors = 0;
static thread_local uintptr_t osCaller = 0;
static char images[256][256]{};
static int (*openOriginal)(const char*, bool);
static bool (*queueOriginal)(int, void*, uint32_t, uint32_t);
static int (*osReadOriginal)(void*, void*, int);
static size_t (*freadOriginal)(void*, size_t, size_t, FILE*);
static ssize_t (*readChkOriginal)(int, void*, size_t, size_t);
static ssize_t (*preadChkOriginal)(int, void*, size_t, off_t, size_t);
static ssize_t (*readOriginal)(int, void*, size_t);
static ssize_t (*preadOriginal)(int, void*, size_t, off_t);

static void TraceModel596(const char* stage) {
    if (596 < 0 || 596 >= RESOURCE_ID_TOTAL) return;
    const auto& info = CStreaming::ms_aInfoForModel[596];
    Trace("MODEL596 stage=%s imageId=%u sector=%u sectors=%u state=%u next=%d prev=%d",
        stage, info.m_nImgId, info.m_nCdPosn, info.m_nCdSize,
        (unsigned)info.m_nLoadState, (int)info.m_nNextIndex, (int)info.m_nPrevIndex);
}

static void FileTrace(const char* stage, int fd, size_t count, uintptr_t caller, off_t explicitOffset = -1) {
    int saved = errno;
    char link[64], path[512]{};
    snprintf(link, sizeof(link), "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, path, sizeof(path)-1);
    if (n >= 0) path[n] = 0;
    struct stat st{}; fstat(fd, &st);
    off_t off = explicitOffset >= 0 ? explicitOffset : lseek(fd, 0, SEEK_CUR);
    Dl_info di{}; dladdr(reinterpret_cast<void*>(caller), &di);
    Trace("%s fd=%d image=%s offset=%lld fileSize=%lld sector=%u sectors=%u bytes64=%llu actualCount=%zu hex=%zx caller=%p library=%s relative=%zx osCaller=%zx", stage, fd, path,
        (long long)off, (long long)st.st_size, sector, sectors, (unsigned long long)sectors*2048,
        count, count, (void*)caller, di.dli_fname ? di.dli_fname : "?", caller-(uintptr_t)di.dli_fbase, osCaller-g_libGTASA);
    errno = saved;
}

static int Open(const char* name, bool flag) {
    int result = openOriginal(name, flag);
    unsigned image = (uint32_t)result >> 24;
    snprintf(images[image], sizeof(images[image]), "%s", name);
    Trace("OPEN handle=%08x imageId=%u image=%s", result, image, name);
    if (image == 0 || strstr(name, "GTA3") || strstr(name, "gta3")) {
        TraceModel596("CdStreamOpen");
    }
    return result;
}

static bool Queue(int channel, void* buffer, uint32_t packed, uint32_t count) {
    uintptr_t caller = (uintptr_t)__builtin_return_address(0);
    unsigned image = packed >> 24;
    uint32_t pos = packed & 0xffffff;
    const uint64_t bytes64 = (uint64_t)count * 2048ULL;
    const uint64_t offset64 = (uint64_t)pos * 2048ULL;

    Trace("QUEUE channel=%d imageId=%u image=%s buffer=%p sector=%u sectors=%u bytes64=%llu bytes32=%08x signedBytes32=%d offset64=%llu caller=%zx", channel, image, images[image], buffer, pos, count,
        (unsigned long long)bytes64, count<<11, (int32_t)(count<<11), (unsigned long long)offset64, caller-g_libGTASA);

    bool zeroCdModel = false;
    int zeroCdModelId = -1;
    if (channel >= 0 && channel < 2) {
        for (int i=0; i<16; ++i) {
            int model = CStreaming::ms_channel[channel].modelIds[i];
            if (model < 0 || model >= RESOURCE_ID_TOTAL) continue;
            const auto& info = CStreaming::ms_aInfoForModel[model];
            Trace("MODEL channel=%d slot=%d model=%d imageId=%u sector=%u sectors=%u bufferOffset=%d state=%u", channel, i, model, info.m_nImgId,
                info.m_nCdPosn, info.m_nCdSize, CStreaming::ms_channel[channel].modelStreamingBufferOffsets[i], (unsigned)info.m_nLoadState);

            if (model == 596) {
                Trace("MODEL596 stage=Queue channel=%d slot=%d requestSector=%u requestSectors=%u imageId=%u sector=%u sectors=%u state=%u",
                    channel, i, pos, count, info.m_nImgId, info.m_nCdPosn, info.m_nCdSize, (unsigned)info.m_nLoadState);
            }
            if (info.m_nCdPosn == 0 && info.m_nCdSize == 0) {
                zeroCdModel = true;
                zeroCdModelId = model;
            }
        }
    }

    // CdStream ultimately converts sector count to an `int` byte count for
    // OS_FileRead. Anything above INT_MAX/2048 wraps negative and then becomes
    // a huge size_t inside fread on Android 12 (Bionic FORTIFY abort).
    const bool byteCountOverflow = count > (uint32_t)(INT_MAX / 2048);
    const bool emptyRequest = count == 0;

    if (emptyRequest || byteCountOverflow || zeroCdModel) {
        const char* reason = emptyRequest ? "zero-sector-count" :
                             byteCountOverflow ? "int-byte-count-overflow" :
                             "queued-model-has-zero-cd-range";
        Trace("INVALID_CDREAD stage=QUEUE reason=%s channel=%d model=%d imageId=%u sector=%u sectors=%u bytes64=%llu offset64=%llu caller=%zx action=reject",
            reason, channel, zeroCdModelId, image, pos, count,
            (unsigned long long)bytes64, (unsigned long long)offset64, caller-g_libGTASA);
        return false;
    }

    return queueOriginal(channel, buffer, packed, count);
}

static int OSRead(void* file, void* buffer, int bytes) {
    bool previous = inRead;
    inRead = true; osCaller = (uintptr_t)__builtin_return_address(0);
    sector = sectors = 0;
    auto* streams = *reinterpret_cast<uint8_t**>(g_libGTASA+0x8e41a0);
    int streamCount = *reinterpret_cast<int*>(g_libGTASA+0x8e41b8);
    int matchedChannel = -1;
    for (int i=0; streams && i<streamCount && i<64; ++i) {
        auto* stream = streams+i*0x30;
        if (*reinterpret_cast<void**>(stream+0x28) == file && *reinterpret_cast<void**>(stream+8) == buffer) {
            sector = *reinterpret_cast<uint32_t*>(stream);
            sectors = *reinterpret_cast<uint32_t*>(stream+4);
            matchedChannel = i;
            Trace("OSREAD channel=%d file=%p buffer=%p sector=%u sectors=%u bytesArg=%d hex=%08x calculated64=%llu caller=%zx", i, file, buffer, sector, sectors, bytes, (uint32_t)bytes, (unsigned long long)sectors*2048, osCaller-g_libGTASA);
        }
    }

    // Secondary fail-closed guard in case a bad request reaches OS_FileRead by
    // another path. A non-positive byte count is never a valid CdStream read.
    if (matchedChannel >= 0 && sectors > 0 && bytes <= 0) {
        Trace("INVALID_CDREAD stage=OSREAD reason=non-positive-byte-count channel=%d sector=%u sectors=%u bytesArg=%d hex=%08x action=return0",
            matchedChannel, sector, sectors, bytes, (uint32_t)bytes);
        inRead = previous;
        return 0;
    }

    int result = osReadOriginal(file, buffer, bytes);
    inRead = previous;
    return result;
}

static size_t FRead(void* data, size_t size, size_t count, FILE* file) {
    if (inRead) {
        const uintptr_t caller = (uintptr_t)__builtin_return_address(0);
        const int fd = fileno(file);
        bool multiplyOverflow = size != 0 && count > std::numeric_limits<size_t>::max() / size;
        size_t total = multiplyOverflow ? std::numeric_limits<size_t>::max() : size * count;
        FileTrace("FREAD", fd, total, caller, ftello(file));

        if (sectors > 0) {
            struct stat st{};
            off_t off = ftello(file);
            bool haveStat = fd >= 0 && fstat(fd, &st) == 0;
            bool ssizeOverflow = multiplyOverflow || total > (size_t)SSIZE_MAX;
            bool outsideFile = false;
            if (haveStat && off >= 0 && st.st_size >= off) {
                outsideFile = total > (size_t)(st.st_size - off);
            }

            if (ssizeOverflow || outsideFile) {
                Trace("INVALID_CDREAD stage=FREAD reason=%s fd=%d offset=%lld fileSize=%lld sector=%u sectors=%u size=%zu count=%zu total=%zu action=return0",
                    ssizeOverflow ? "size_t-or-ssize-overflow" : "range-outside-file",
                    fd, (long long)off, (long long)(haveStat ? st.st_size : -1), sector, sectors,
                    size, count, total);
                return 0;
            }
        }
    }
    return freadOriginal(data, size, count, file);
}

static ssize_t ReadChk(int fd, void* data, size_t count, size_t capacity) {
    if (inRead) FileTrace("READ_CHK", fd, count, (uintptr_t)__builtin_return_address(0));
    return readChkOriginal(fd, data, count, capacity);
}
static ssize_t PReadChk(int fd, void* data, size_t count, off_t offset, size_t capacity) {
    if (inRead) FileTrace("PREAD_CHK", fd, count, (uintptr_t)__builtin_return_address(0), offset);
    return preadChkOriginal(fd, data, count, offset, capacity);
}
static ssize_t Read(int fd, void* data, size_t count) {
    if (inRead) FileTrace("READ", fd, count, (uintptr_t)__builtin_return_address(0));
    return readOriginal(fd, data, count);
}
static ssize_t PRead(int fd, void* data, size_t count, off_t offset) {
    if (inRead) FileTrace("PREAD", fd, count, (uintptr_t)__builtin_return_address(0), offset);
    return preadOriginal(fd, data, count, offset);
}

// Diagnostic prerequisite: baseline crashes in ColAccel before reaching CdStream.
// Only disable replay for this trace/guard; all four model/store fixes remain identical.
static void (*colStartOriginal)();
static void ColStart() {
    colStartOriginal();
    auto* state = reinterpret_cast<int32_t*>(g_libGTASA+0xC23048);
    Trace("DIAGNOSTIC ColAccel state=%d -> 0; baseline model fixes unchanged", *state);
    *state = 0;
}

void InstallCdStreamTrace() {
    CHook::InlineHook("_Z12CdStreamOpenPKcb", &Open, &openOriginal);
    CHook::InlineHook("_Z12CdStreamReadiPvjj", &Queue, &queueOriginal);
    CHook::InlineHook("_Z11OS_FileReadPvS_i", &OSRead, &osReadOriginal);
    CHook::InlineHook("_ZN9CColAccel10startCacheEv", &ColStart, &colStartOriginal);
    void* a=shadowhook_hook_sym_name("libc.so", "fread", (void*)&FRead, (void**)&freadOriginal);
    void* b=shadowhook_hook_sym_name("libc.so", "__read_chk", (void*)&ReadChk, (void**)&readChkOriginal);
    void* c=shadowhook_hook_sym_name("libc.so", "__pread_chk", (void*)&PReadChk, (void**)&preadChkOriginal);
    shadowhook_hook_sym_name("libc.so", "read", (void*)&Read, (void**)&readOriginal);
    shadowhook_hook_sym_name("libc.so", "pread", (void*)&PRead, (void**)&preadOriginal);
    Trace("INSTALLED guard=CDSTREAM_GUARD_001 fread=%p read_chk=%p pread_chk=%p", a,b,c);
    TraceModel596("InstallCdStreamTrace");
}
