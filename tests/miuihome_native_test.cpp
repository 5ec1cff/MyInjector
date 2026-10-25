#include "gesture_state.h"
#include "sha256.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

using namespace miuihome;
static Task task(int id, int user, const char *package) {
    Task value{};
    value.id = id;
    value.user = user;
    strcpy(value.package, package);
    return value;
}
int main() {
    const auto first = task(42, 0, "example.first"), other = task(43, 0, "example.other");
    const auto profile = task(42, 999, "example.first");
    constexpr uint64_t t = 1000 * kMillisecond;
    Drag drag;
    drag.begin(first, 1, t);
    drag.finish(t + 400 * kMillisecond);
    assert(!drag.commit(first, t + 1100 * kMillisecond)); // animation time cannot arm a short drag
    drag.begin(first, 2, t);
    drag.finish(t + 700 * kMillisecond);
    assert(!drag.commit(profile, t + 800 * kMillisecond)); // same ID in another Android user
    assert(!drag.commit(other, t + 800 * kMillisecond));
    assert(drag.commit(first, t + 800 * kMillisecond));
    assert(!drag.commit(first, t + 900 * kMillisecond)); // consumed exactly once
    drag.begin(first, 3, t);
    assert(!drag.hintDue(t + 699 * kMillisecond));
    assert(drag.hintDue(t + 700 * kMillisecond));
    drag.hinted = true;
    assert(!drag.hintDue(t + 900 * kMillisecond));
    drag.finish(t + 800 * kMillisecond);
    drag.cancel();
    assert(!drag.commit(first, t + 1000 * kMillisecond));
    drag.begin(first, 4, t);
    drag.finish(t + 800 * kMillisecond);
    assert(!drag.commit(first, t + 6000 * kMillisecond)); // stale delayed cleanup
    drag.begin(first, 5, t);
    drag.finish(t + 800 * kMillisecond);
    drag.begin(other, 6, t + 900 * kMillisecond);
    assert(!drag.commit(first, t + 1000 * kMillisecond)); // replacement owns a new generation

    LongPress press;
    press.begin(first, 7, t);
    assert(press.duration(t + 499 * kMillisecond) == 499);
    assert(press.duration(t + 500 * kMillisecond) == 500);
    press.finish(t + 500 * kMillisecond);
    assert(press.duration(t + 900 * kMillisecond) == 500);
    press.begin(first, 8, t);
    press.finish(t + 300 * kMillisecond);
    assert(press.duration(t + 700 * kMillisecond) ==
           300); // worker delay cannot turn a tap into a long press
    press.begin(first, 9, t);
    press.move(10, 0);
    assert(press.tracking);
    press.move(0, 9);
    assert(!press.tracking); // leaving the tap slop cancels the timer
    assert(press.duration(t + 1000 * kMillisecond) == 0);
    press.begin(first, 10, t);
    press.cancel();
    assert(!press.tracking);

    char digest[65];
    Sha256 empty;
    empty.finish(digest);
    assert(strcmp(digest, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") == 0);
    Sha256 abc;
    const char text[] = "abc";
    for (unsigned i = 0; i < 3; ++i)
        abc.update(text + i, 1);
    abc.finish(digest);
    assert(strcmp(digest, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") == 0);
    Sha256 large;
    char block[1000];
    memset(block, 'a', sizeof(block));
    for (unsigned i = 0; i < 1000; ++i)
        large.update(block, sizeof(block));
    large.finish(digest);
    assert(strcmp(digest, "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0") == 0);
    puts("PASS: drag timing/cancellation/identity/generation and SHA-256 streaming");
}
