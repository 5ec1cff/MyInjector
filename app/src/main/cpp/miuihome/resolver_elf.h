#pragma once
#include "runtime_profile.h"
#include <elf.h>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace miuihome::resolver {
bool fail(ResolverError &error, const char *format, ...);
struct Bytes {
    const uint8_t *data = nullptr;
    size_t size = 0;
    bool contains(uint64_t offset, uint64_t count) const {
        return data && offset <= size && count <= size - offset;
    }
    Bytes slice(uint64_t offset, uint64_t count) const {
        return contains(offset, count) ? Bytes{data + offset, size_t(count)} : Bytes{};
    }
    template <typename T> bool read(uint64_t offset, T &out) const {
        if (!contains(offset, sizeof(T)))
            return false;
        __builtin_memcpy(&out, data + offset, sizeof(T));
        return true;
    }
};
class File {
    void *mapping_ = nullptr;
    size_t size_ = 0;

  public:
    Bytes bytes{};
    ~File();
    bool open(const char *path, ResolverError &error);
};
struct Symbol {
    std::string_view name;
    uintptr_t rva;
    size_t size;
    uint8_t type;
};
class Elf {
    Bytes bytes_{};
    std::vector<Elf64_Shdr> sections_;
    Bytes sectionNames_{};

  public:
    std::vector<Elf64_Phdr> loads;
    std::vector<Symbol> symbols;
    std::unordered_map<uintptr_t, uintptr_t> relocs;
    std::unordered_map<uintptr_t, std::string_view> imports;
    bool init(Bytes bytes, ResolverError &error, bool miniature = false);
    Bytes section(std::string_view name) const;
    Bytes at(uintptr_t rva, size_t size, unsigned flags = PF_R) const;
    bool contains(uintptr_t rva, size_t size, unsigned flags, unsigned forbidden = 0) const;
    bool word(uintptr_t rva, uint64_t &out) const { return at(rva, 8).read(0, out); }
    bool relative(uintptr_t rva, uintptr_t &out) const;
    uint32_t instruction(uintptr_t rva) const;
};
struct MiniDebug {
    std::unique_ptr<uint8_t[]> buffer;
    Elf elf;
    bool init(const Elf &source, ResolverError &error);
    std::vector<Symbol> named(std::string_view name, size_t minSize = 0) const;
    bool unique(std::string_view name, Symbol &out, ResolverError &error, size_t minSize = 0) const;
};
// 只解码本解析器需要的 AArch64 指令，不引入完整反汇编器。
bool unscaled(uint32_t word, uint32_t opcode, unsigned target, unsigned base, int &offset);
bool ldr64(uint32_t word, unsigned target, unsigned base, uint32_t &offset);
bool branch(uint32_t word, uintptr_t pc, uintptr_t &target);
bool addressPair(uint32_t first, uint32_t second, uintptr_t pc, unsigned reg, uintptr_t &target);
std::vector<uintptr_t> references(const Elf &elf, const Symbol &symbol);
} // namespace miuihome::resolver
