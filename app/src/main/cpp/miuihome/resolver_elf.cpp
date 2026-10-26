#include "resolver_elf.h"
#include <algorithm>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <mutex>
extern "C" {
#include "xz/xz.h"
}

namespace miuihome::resolver {
bool fail(ResolverError &error, const char *format, ...) {
    if (!error.message[0]) {
        va_list args;
        va_start(args, format);
        vsnprintf(error.message, sizeof(error.message), format, args);
        va_end(args);
    }
    return false;
}
static std::string_view stringAt(Bytes bytes, uint64_t offset) {
    if (offset >= bytes.size)
        return {};
    const char *start = reinterpret_cast<const char *>(bytes.data + offset);
    auto *end = static_cast<const char *>(memchr(start, 0, bytes.size - offset));
    return end ? std::string_view(start, size_t(end - start)) : std::string_view{};
}
File::~File() {
    if (mapping_)
        munmap(mapping_, size_);
}
bool File::open(const char *path, ResolverError &error) {
    if (!path || mapping_)
        return fail(error, "invalid image path");
    std::string_view full(path);
    size_t delimiter = full.find("!/");
    std::string file(full.substr(0, delimiter));
    int fd = ::open(file.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return fail(error, "cannot open image: %s", file.c_str());
    struct stat st{};
    bool valid = fstat(fd, &st) == 0 && S_ISREG(st.st_mode) && st.st_size >= 64 &&
                 st.st_size <= 512 * 1024 * 1024;
    void *mapped =
        valid ? mmap(nullptr, size_t(st.st_size), PROT_READ, MAP_PRIVATE, fd, 0) : MAP_FAILED;
    close(fd);
    if (mapped == MAP_FAILED)
        return fail(error, "cannot map image (size/type/IO)");
    mapping_ = mapped;
    size_ = size_t(st.st_size);
    Bytes all{static_cast<const uint8_t *>(mapped), size_};
    if (delimiter == std::string_view::npos) {
        bytes = all;
        return true;
    }
    std::string_view wanted = full.substr(delimiter + 2);
    // APK 的 native 库按 STORED 存储。只接受普通单盘 ZIP，拒绝歧义和越界。
    size_t stop = size_ > 65557 ? size_ - 65557 : 0;
    Bytes directory{};
    uint16_t count = 0;
    bool foundEnd = false;
    for (size_t pos = size_ - 22;; --pos) {
        uint32_t signature = 0;
        uint16_t disk = 1, cdDisk = 1, diskCount = 0, comment = 0;
        uint32_t cdSize = 0, cdOffset = 0;
        if (all.read(pos, signature) && signature == 0x06054b50 && all.read(pos + 20, comment) &&
            pos + 22 + comment == size_) {
            all.read(pos + 4, disk);
            all.read(pos + 6, cdDisk);
            all.read(pos + 8, diskCount);
            all.read(pos + 10, count);
            all.read(pos + 12, cdSize);
            all.read(pos + 16, cdOffset);
            if (disk || cdDisk || diskCount != count || count == 0xffff ||
                !all.contains(cdOffset, cdSize) || uint64_t(cdOffset) + cdSize > pos)
                return fail(error, "unsupported ZIP directory");
            directory = all.slice(cdOffset, cdSize);
            foundEnd = true;
            break;
        }
        if (pos == stop)
            break;
    }
    if (!foundEnd)
        return fail(error, "ZIP end not found");
    size_t pos = 0;
    unsigned matches = 0;
    for (unsigned i = 0; i < count; ++i) {
        uint32_t signature = 0, compressed = 0, uncompressed = 0, local = 0;
        uint16_t flags = 0, method = 0, nameSize = 0, extra = 0, comment = 0, disk = 0;
        if (!directory.contains(pos, 46) || !directory.read(pos, signature) ||
            signature != 0x02014b50)
            return fail(error, "invalid ZIP entry");
        directory.read(pos + 8, flags);
        directory.read(pos + 10, method);
        directory.read(pos + 20, compressed);
        directory.read(pos + 24, uncompressed);
        directory.read(pos + 28, nameSize);
        directory.read(pos + 30, extra);
        directory.read(pos + 32, comment);
        directory.read(pos + 34, disk);
        directory.read(pos + 42, local);
        size_t length = size_t(46) + nameSize + extra + comment;
        if (!directory.contains(pos, length))
            return fail(error, "ZIP entry out of bounds");
        std::string_view name(reinterpret_cast<const char *>(directory.data + pos + 46), nameSize);
        if (name == wanted) {
            uint32_t localSignature = 0;
            uint16_t localName = 0, localExtra = 0, localMethod = 0, localFlags = 0;
            if (disk || method || (flags & 1) || compressed != uncompressed || !uncompressed ||
                uncompressed > 128 * 1024 * 1024 || !all.contains(local, 30) ||
                !all.read(local, localSignature) || localSignature != 0x04034b50)
                return fail(error, "native ZIP entry must be unencrypted STORED ELF");
            all.read(uint64_t(local) + 6, localFlags);
            all.read(uint64_t(local) + 8, localMethod);
            all.read(uint64_t(local) + 26, localName);
            all.read(uint64_t(local) + 28, localExtra);
            uint64_t start = uint64_t(local) + 30 + localName + localExtra;
            if (localMethod != method || localFlags != flags || localName != nameSize ||
                !all.contains(uint64_t(local) + 30, localName) ||
                memcmp(all.data + local + 30, name.data(), nameSize) ||
                !all.contains(start, uncompressed))
                return fail(error, "invalid native ZIP local header");
            bytes = all.slice(start, uncompressed);
            ++matches;
        }
        pos += length;
    }
    return (pos == directory.size && matches == 1) ||
           fail(error, "native ZIP entry not unique/present");
}

bool Elf::init(Bytes bytes, ResolverError &error, bool miniature) {
    bytes_ = bytes;
    Elf64_Ehdr h{};
    if (!bytes.read(0, h) || memcmp(h.e_ident, "\177ELF\2\1\1", 7) || h.e_machine != EM_AARCH64 ||
        h.e_ehsize != sizeof(h) || h.e_shentsize != sizeof(Elf64_Shdr) || !h.e_shnum ||
        h.e_shnum > 4096 || h.e_shstrndx >= h.e_shnum ||
        !bytes.contains(h.e_shoff, uint64_t(h.e_shnum) * sizeof(Elf64_Shdr)) ||
        (!miniature && h.e_type != ET_DYN) || h.e_phnum > 128 ||
        (h.e_phnum && (h.e_phentsize != sizeof(Elf64_Phdr) ||
                       !bytes.contains(h.e_phoff, uint64_t(h.e_phnum) * sizeof(Elf64_Phdr)))))
        return fail(error, "invalid AArch64 ELF headers");
    for (unsigned i = 0; i < h.e_phnum; ++i) {
        Elf64_Phdr p{};
        bytes.read(h.e_phoff + uint64_t(i) * sizeof(p), p);
        if (p.p_type != PT_LOAD)
            continue;
        if (p.p_filesz > p.p_memsz || p.p_vaddr > UINTPTR_MAX - p.p_memsz ||
            !bytes.contains(p.p_offset, p.p_filesz))
            return fail(error, "ELF segment out of bounds");
        loads.push_back(p);
    }
    for (unsigned i = 0; i < h.e_shnum; ++i) {
        Elf64_Shdr s{};
        bytes.read(h.e_shoff + uint64_t(i) * sizeof(s), s);
        if (s.sh_type != SHT_NOBITS && !bytes.contains(s.sh_offset, s.sh_size))
            return fail(error, "ELF section out of bounds");
        sections_.push_back(s);
    }
    const auto &names = sections_[h.e_shstrndx];
    sectionNames_ = bytes.slice(names.sh_offset, names.sh_size);
    if (!sectionNames_.size)
        return fail(error, "missing ELF section names");
    for (const auto &s : sections_) {
        if (s.sh_type != SHT_SYMTAB && s.sh_type != SHT_DYNSYM && s.sh_type != SHT_RELA)
            continue;
        if (s.sh_link >= sections_.size())
            return fail(error, "invalid ELF linked section");
        if (s.sh_type == SHT_RELA) {
            if (s.sh_entsize != sizeof(Elf64_Rela) || s.sh_size % sizeof(Elf64_Rela) ||
                s.sh_size / sizeof(Elf64_Rela) > 1000000)
                return fail(error, "invalid relocation table");
            const auto &syms = sections_[s.sh_link];
            if (syms.sh_link >= sections_.size() || syms.sh_entsize != sizeof(Elf64_Sym))
                return fail(error, "invalid relocation symbol table");
            const auto &str = sections_[syms.sh_link];
            Bytes strings = bytes.slice(str.sh_offset, str.sh_size);
            for (size_t off = 0; off < s.sh_size; off += sizeof(Elf64_Rela)) {
                Elf64_Rela r{};
                bytes.read(s.sh_offset + off, r);
                auto type = ELF64_R_TYPE(r.r_info);
                if (type == R_AARCH64_RELATIVE) {
                    if (r.r_addend < 0 || !contains(r.r_offset, 8, PF_R) ||
                        !relocs.emplace(r.r_offset, uintptr_t(r.r_addend)).second)
                        return fail(error, "invalid/duplicate relative relocation");
                } else if (type == R_AARCH64_JUMP_SLOT) {
                    uint64_t index = ELF64_R_SYM(r.r_info);
                    Elf64_Sym sym{};
                    if (index >= syms.sh_size / sizeof(sym) || !contains(r.r_offset, 8, PF_R) ||
                        !bytes.read(syms.sh_offset + index * sizeof(sym), sym))
                        return fail(error, "import symbol out of bounds");
                    auto name = stringAt(strings, sym.st_name);
                    if (name.empty() || !imports.emplace(r.r_offset, name).second)
                        return fail(error, "invalid/duplicate import");
                }
            }
        } else {
            if (s.sh_entsize != sizeof(Elf64_Sym) || s.sh_size % sizeof(Elf64_Sym) ||
                s.sh_size / sizeof(Elf64_Sym) > 300000)
                return fail(error, "invalid symbol table");
            const auto &str = sections_[s.sh_link];
            Bytes strings = bytes.slice(str.sh_offset, str.sh_size);
            for (size_t off = 0; off < s.sh_size; off += sizeof(Elf64_Sym)) {
                Elf64_Sym sym{};
                bytes.read(s.sh_offset + off, sym);
                if (!sym.st_name || sym.st_shndx == SHN_UNDEF)
                    continue;
                auto name = stringAt(strings, sym.st_name);
                if (name.empty())
                    return fail(error, "invalid symbol name");
                symbols.push_back(
                    {name, sym.st_value, size_t(sym.st_size), uint8_t(ELF64_ST_TYPE(sym.st_info))});
            }
        }
    }
    return true;
}
Bytes Elf::section(std::string_view name) const {
    Bytes result{};
    unsigned count = 0;
    for (const auto &s : sections_)
        if (stringAt(sectionNames_, s.sh_name) == name) {
            if (s.sh_type == SHT_NOBITS)
                return {};
            result = bytes_.slice(s.sh_offset, s.sh_size);
            ++count;
        }
    return count == 1 ? result : Bytes{};
}
Bytes Elf::at(uintptr_t rva, size_t size, unsigned flags) const {
    for (const auto &p : loads)
        if ((p.p_flags & flags) == flags && rva >= p.p_vaddr && rva - p.p_vaddr <= p.p_filesz &&
            size <= p.p_filesz - (rva - p.p_vaddr))
            return bytes_.slice(p.p_offset + rva - p.p_vaddr, size);
    return {};
}
bool Elf::contains(uintptr_t rva, size_t size, unsigned flags, unsigned forbidden) const {
    for (const auto &p : loads)
        if ((p.p_flags & flags) == flags && !(p.p_flags & forbidden) && rva >= p.p_vaddr &&
            rva - p.p_vaddr <= p.p_memsz && size <= p.p_memsz - (rva - p.p_vaddr))
            return true;
    return false;
}
bool Elf::relative(uintptr_t rva, uintptr_t &out) const {
    auto i = relocs.find(rva);
    if (i != relocs.end()) {
        out = i->second;
        return true;
    }
    uint64_t value = 0;
    if (!word(rva, value))
        return false;
    out = value;
    return true;
}
uint32_t Elf::instruction(uintptr_t rva) const {
    uint32_t word = 0;
    at(rva, 4, PF_R | PF_X).read(0, word);
    return word;
}
bool MiniDebug::init(const Elf &source, ResolverError &error) {
    Bytes input = source.section(".gnu_debugdata");
    constexpr size_t limit = 16 * 1024 * 1024;
    if (!input.size || input.size > limit)
        return fail(error, "missing/oversized .gnu_debugdata");
    buffer.reset(new (std::nothrow) uint8_t[limit]);
    if (!buffer)
        return fail(error, "cannot allocate mini debug buffer");
    static std::once_flag crcReady;
    std::call_once(crcReady, [] {
        xz_crc32_init();
        xz_crc64_init();
    });
    xz_dec *decoder = xz_dec_init(XZ_SINGLE, 0);
    if (!decoder)
        return fail(error, "cannot allocate XZ decoder");
    xz_buf b{input.data, 0, input.size, buffer.get(), 0, limit};
    xz_ret status = xz_dec_run(decoder, &b);
    xz_dec_end(decoder);
    // ELF 节末尾可能有 XZ stream padding，只接受至多 3 字节的对齐零。
    size_t padding = input.size - b.in_pos;
    if (status != XZ_STREAM_END || padding > 3 ||
        std::any_of(input.data + b.in_pos, input.data + input.size,
                    [](uint8_t v) { return v != 0; }))
        return fail(error, "invalid/unsupported XZ mini debug stream (%d)", int(status));
    if (!elf.init({buffer.get(), b.out_pos}, error, true))
        return false;
    for (const auto &symbol : elf.symbols) {
        // 只把真正落在源 ELF 可执行段中的符号当作函数，数据符号另从外层取得。
        if (symbol.type == STT_FUNC &&
            !source.at(symbol.rva, std::max(symbol.size, size_t{4}), PF_R | PF_X).size)
            return fail(error, "mini debug function outside executable segments");
    }
    return true;
}
std::vector<Symbol> MiniDebug::named(std::string_view name, size_t minSize) const {
    std::vector<Symbol> out;
    for (const auto &s : elf.symbols)
        if (s.type == STT_FUNC && s.name == name && s.size >= minSize)
            out.push_back(s);
    return out;
}
bool MiniDebug::unique(std::string_view name, Symbol &out, ResolverError &error,
                       size_t minSize) const {
    auto values = named(name, minSize);
    if (values.size() != 1)
        return fail(error, "symbol %.*s: %zu candidates", int(name.size()), name.data(),
                    values.size());
    out = values[0];
    return true;
}
bool unscaled(uint32_t w, uint32_t opcode, unsigned target, unsigned base, int &offset) {
    if ((w & 0xffe00fff) != (opcode | (base << 5) | target))
        return false;
    offset = int((w >> 12) & 0x1ff);
    if (offset & 0x100)
        offset -= 0x200;
    return true;
}
bool ldr64(uint32_t w, unsigned target, unsigned base, uint32_t &offset) {
    if ((w & 0xffc003ff) != (0xf9400000 | (base << 5) | target))
        return false;
    offset = ((w >> 10) & 0xfff) * 8;
    return true;
}
static int64_t signExtend(uint64_t value, unsigned bits) {
    return int64_t(value ^ (uint64_t{1} << (bits - 1))) - int64_t(uint64_t{1} << (bits - 1));
}
bool branch(uint32_t w, uintptr_t pc, uintptr_t &target) {
    if ((w & 0xfc000000) != 0x94000000)
        return false;
    int64_t value = int64_t(pc) + signExtend(w & 0x3ffffff, 26) * 4;
    if (value < 0)
        return false;
    target = uintptr_t(value);
    return true;
}
bool addressPair(uint32_t a, uint32_t b, uintptr_t pc, unsigned reg, uintptr_t &target) {
    if ((a & 0x9f00001f) != (0x90000000 | reg) ||
        (b & 0xff8003ff) != (0x91000000 | (reg << 5) | reg))
        return false;
    uint64_t bits = ((a >> 29) & 3) | (((a >> 5) & 0x7ffff) << 2);
    int64_t page = int64_t(pc & ~uintptr_t{4095}) + signExtend(bits, 21) * 4096;
    if (page < 0)
        return false;
    target = uintptr_t(page) + (uint64_t((b >> 10) & 0xfff) << ((b & (1 << 22)) ? 12 : 0));
    return true;
}
std::vector<uintptr_t> references(const Elf &elf, const Symbol &s) {
    std::vector<uintptr_t> result;
    if (!elf.at(s.rva, s.size, PF_R | PF_X).size)
        return result;
    for (size_t off = 0; off + 4 <= s.size; off += 4) {
        uint32_t w = elf.instruction(s.rva + off);
        uintptr_t value = 0;
        if (off + 8 <= s.size &&
            addressPair(w, elf.instruction(s.rva + off + 4), s.rva + off, w & 31, value))
            result.push_back(value);
        else if ((w & 0x9f000000) == 0x10000000) {
            int64_t address = int64_t(s.rva + off) +
                              signExtend(((w >> 29) & 3) | (((w >> 5) & 0x7ffff) << 2), 21);
            if (address >= 0)
                result.push_back(uintptr_t(address));
        }
    }
    return result;
}
} // namespace miuihome::resolver
