#include "main.h"
#include "game/Streaming.h"
#include "vendor/armhook/patch.h"
#include <android/log.h>
#include <sys/stat.h>
#include <cerrno>
#include <cstdarg>

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
    return result;
}
static bool Queue(int channel, void* buffer, uint32_t packed, uint32_t count) {
    uintptr_t caller = (uintptr_t)__builtin_return_address(0);
    unsigned image = packed >> 24;
    uint32_t pos = packed & 0xffffff;
    Trace("QUEUE channel=%d imageId=%u image=%s buffer=%p sector=%u sectors=%u bytes64=%llu bytes32=%08x signedBytes32=%d offset64=%llu caller=%zx", channel, image, images[image], buffer, pos, count,
        (unsigned long long)count*2048, count<<11, (int32_t)(count<<11), (unsigned long long)pos*2048, caller-g_libGTASA);
    if (channel >= 0 && channel < 2) {
        for (int i=0; i<16; ++i) {
            int model = CStreaming::ms_channel[channel].modelIds[i];
            if (model < 0 || model >= RESOURCE_ID_TOTAL) continue;
            const auto& info = CStreaming::ms_aInfoForModel[model];
            Trace("MODEL channel=%d slot=%d model=%d imageId=%u sector=%u sectors=%u bufferOffset=%d state=%u", channel, i, model, info.m_nImgId,
                info.m_nCdPosn, info.m_nCdSize, CStreaming::ms_channel[channel].modelStreamingBufferOffsets[i], (unsigned)info.m_nLoadState);
        }
    }
    return queueOriginal(channel, buffer, packed, count);
}
static int OSRead(void* file, void* buffer, int bytes) {
    bool previous = inRead;
    inRead = true; osCaller = (uintptr_t)__builtin_return_address(0);
    sector = sectors = 0;
    auto* streams = *reinterpret_cast<uint8_t**>(g_libGTASA+0x8e41a0);
    int streamCount = *reinterpret_cast<int*>(g_libGTASA+0x8e41b8);
    for (int i=0; streams && i<streamCount && i<64; ++i) {
        auto* stream = streams+i*0x30;
        if (*reinterpret_cast<void**>(stream+0x28) == file && *reinterpret_cast<void**>(stream+8) == buffer) {
            sector = *reinterpret_cast<uint32_t*>(stream);
            sectors = *reinterpret_cast<uint32_t*>(stream+4);
            Trace("OSREAD channel=%d file=%p buffer=%p sector=%u sectors=%u bytesArg=%d hex=%08x calculated64=%llu caller=%zx", i, file, buffer, sector, sectors, bytes, (uint32_t)bytes, (unsigned long long)sectors*2048, osCaller-g_libGTASA);
        }
    }
    int result = osReadOriginal(file, buffer, bytes);
    inRead = previous;
    return result;
}
static size_t FRead(void* data, size_t size, size_t count, FILE* file) {
    if (inRead) FileTrace("FREAD", fileno(file), size*count, (uintptr_t)__builtin_return_address(0), ftello(file));
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
// Only disable replay for this trace; all four model/store fixes remain identical.
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
    Trace("INSTALLED fread=%p read_chk=%p pread_chk=%p", a,b,c);
}