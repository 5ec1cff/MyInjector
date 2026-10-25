#pragma once
#include <math.h>
#include <stdint.h>
#include <string.h>

namespace miuihome {
constexpr uint64_t kMillisecond = 1000000;
struct Task {
    int32_t id = -1;
    int32_t user = -1;
    char package[256]{};
    bool valid() const { return id >= 0 && user >= 0 && package[0]; }
    bool operator==(const Task &other) const {
        return id == other.id && user == other.user && strcmp(package, other.package) == 0;
    }
};

// Contains copied scalars only. Never retain a Dart object outside its callback.
struct Drag {
    Task task{};
    uint64_t generation = 0, start = 0, end = 0;
    bool active = false, pending = false, hinted = false;
    void begin(const Task &target, uint64_t token, uint64_t now) {
        task = target;
        generation = token;
        start = now;
        end = 0;
        active = target.valid();
        pending = false;
        hinted = false;
    }
    void finish(uint64_t now) {
        if (!active)
            return;
        end = now;
        active = false;
        pending = now >= start;
    }
    void cancel() {
        active = false;
        pending = false;
    }
    uint64_t duration() const { return pending && end >= start ? (end - start) / kMillisecond : 0; }
    bool commit(const Task &target, uint64_t now) {
        bool match = pending && target == task && duration() >= 700 && now >= end &&
                     now - end <= 5000 * kMillisecond;
        if (target == task)
            cancel();
        return match;
    }
    bool hintDue(uint64_t now) const {
        return active && !hinted && now >= start && now - start >= 700 * kMillisecond;
    }
};

struct LongPress {
    Task task{};
    uint64_t generation = 0, start = 0, end = 0;
    double distance = 0;
    bool tracking = false;
    void begin(const Task &target, uint64_t token, uint64_t now) {
        task = target;
        generation = token;
        start = now;
        end = 0;
        distance = 0;
        tracking = target.valid();
    }
    void finish(uint64_t now) {
        if (tracking && !end)
            end = now;
    }
    void cancel() { tracking = false; }
    void move(double dx, double dy) {
        if (!isfinite(dx) || !isfinite(dy)) {
            cancel();
            return;
        }
        distance += sqrt(dx * dx + dy * dy);
        if (distance > 18)
            cancel();
    }
    uint64_t duration(uint64_t now) const {
        uint64_t until = end ? end : now;
        return tracking && until >= start ? (until - start) / kMillisecond : 0;
    }
};
} // namespace miuihome
