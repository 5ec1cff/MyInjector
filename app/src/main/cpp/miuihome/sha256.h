#pragma once
#include <stddef.h>
#include <stdint.h>
#include <string.h>

namespace miuihome {
// Streaming SHA-256 used only by the loader worker, before installing hooks.
class Sha256 {
    uint32_t h[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                     0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    uint8_t buffer[64]{};
    size_t used = 0;
    uint64_t total = 0;
    static uint32_t rotate(uint32_t x, unsigned n) { return (x >> n) | (x << (32 - n)); }
    void block(const uint8_t *data) {
        static constexpr uint32_t k[64] = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4,
            0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe,
            0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
            0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc,
            0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
            0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116,
            0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
            0xc67178f2};
        uint32_t w[64];
        for (unsigned i = 0; i < 16; ++i)
            w[i] = uint32_t(data[i * 4]) << 24 | uint32_t(data[i * 4 + 1]) << 16 |
                   uint32_t(data[i * 4 + 2]) << 8 | data[i * 4 + 3];
        for (unsigned i = 16; i < 64; ++i) {
            auto x = w[i - 15], y = w[i - 2];
            w[i] = w[i - 16] + (rotate(x, 7) ^ rotate(x, 18) ^ (x >> 3)) + w[i - 7] +
                   (rotate(y, 17) ^ rotate(y, 19) ^ (y >> 10));
        }
        auto a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (unsigned i = 0; i < 64; ++i) {
            uint32_t t1 = hh + (rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25)) +
                          ((e & f) ^ (~e & g)) + k[i] + w[i];
            uint32_t t2 =
                (rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22)) + ((a & b) ^ (a & c) ^ (b & c));
            hh = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }
        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
        h[4] += e;
        h[5] += f;
        h[6] += g;
        h[7] += hh;
    }

  public:
    void update(const void *input, size_t size) {
        auto *data = static_cast<const uint8_t *>(input);
        total += size;
        while (size) {
            size_t count = 64 - used;
            if (count > size)
                count = size;
            memcpy(buffer + used, data, count);
            used += count;
            data += count;
            size -= count;
            if (used == 64) {
                block(buffer);
                used = 0;
            }
        }
    }
    void finish(char output[65]) {
        uint64_t bits = total * 8;
        uint8_t one = 0x80, zero = 0;
        update(&one, 1);
        while (used != 56)
            update(&zero, 1);
        uint8_t length[8];
        for (unsigned i = 0; i < 8; ++i)
            length[7 - i] = uint8_t(bits >> (i * 8));
        update(length, 8);
        constexpr char digits[] = "0123456789abcdef";
        for (unsigned i = 0; i < 32; ++i) {
            uint8_t b = uint8_t(h[i / 4] >> (24 - 8 * (i % 4)));
            output[i * 2] = digits[b >> 4];
            output[i * 2 + 1] = digits[b & 15];
        }
        output[64] = 0;
    }
};
} // namespace miuihome
