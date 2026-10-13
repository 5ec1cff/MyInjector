"""Verify the legacy224 hook schema in an APK, using only Python's stdlib.

Usage: python scripts/verify_exteragram_apk.py path/to/exteragram.apk
SHA-256 is evidence only, never an acceptance gate. Check *definitions*, not just
references, including exact prototypes, static flags, and paint inheritance.
"""
import argparse
import hashlib
import struct
import zipfile

CELL = 'Lorg/telegram/ui/Cells/ChatMessageCell;'
MSG = 'Lorg/telegram/messenger/MessageObject;'
OWNER = 'Lorg/telegram/tgnet/TLRPC$Message;'
THEME = 'Lorg/telegram/ui/ActionBar/Theme;'
PULL = 'Lorg/telegram/ui/ChatPullingDownDrawable;'
PAINT = 'Lcom/exteragram/messenger/utils/ui/TextPaint;'
FIELDS = {
    (CELL, 'currentTimeString', 'Landroid/text/SpannableStringBuilder;'): False,
    (CELL, 'timeWidth', 'I'): False,
    (CELL, 'timeTextWidth', 'I'): False,
    (CELL, 'isMegagroup', 'Z'): False,
    (THEME, 'chat_timePaint', PAINT): True,
    (MSG, 'messageOwner', OWNER): False,
    (OWNER, 'id', 'I'): False,
    (OWNER, 'realId', 'I'): False,
    (OWNER, 'post_author', 'Ljava/lang/String;'): False,
    (PULL, 'isTopic', 'Z'): False,
}
METHODS = {
    (CELL, 'measureTime', '(' + MSG + ')V'): False,
    (MSG, 'getFromChatId', '()J'): False,
    (MSG, 'getDialogId', '()J'): False,
    (PULL, 'getNextUnreadDialog', '(JIIZ[I)Lorg/telegram/tgnet/TLRPC$Dialog;'): True,
    (PULL, 'getChatId', '()J'): False,
    (PULL, 'draw', '(Landroid/graphics/Canvas;Landroid/view/View;FF)V'): False,
    (PULL, 'drawBottomPanel', '(Landroid/graphics/Canvas;III)V'): False,
    (PULL, 'needDrawBottomPanel', '()Z'): False,
}


class Dex:
    def __init__(self, data):
        self.data = data
        if not data.startswith(b'dex\n'):
            raise ValueError('Not a standard DEX file')
        self.strings = []
        for i in range(self.u32(56)):
            _, pos = self.uleb(self.u32(self.u32(60) + 4 * i))
            end = data.index(0, pos)
            self.strings.append(data[pos:end].decode('utf-8', 'replace'))
        self.types = [self.strings[self.u32(self.u32(68) + 4 * i)]
                      for i in range(self.u32(64))]

    def u32(self, pos):
        return struct.unpack_from('<I', self.data, pos)[0]

    def u16(self, pos):
        return struct.unpack_from('<H', self.data, pos)[0]

    def uleb(self, pos):
        value = shift = 0
        while True:
            byte = self.data[pos]
            pos += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, pos
            shift += 7
            if shift > 35:
                raise ValueError('Invalid ULEB128')

    def field(self, index):
        pos = self.u32(84) + 8 * index
        return (self.types[self.u16(pos)], self.strings[self.u32(pos + 4)],
                self.types[self.u16(pos + 2)])

    def method(self, index):
        pos = self.u32(92) + 8 * index
        proto = self.u32(76) + 12 * self.u16(pos + 2)
        params = self.u32(proto + 8)
        args = '' if not params else ''.join(
            self.types[self.u16(params + 4 + 2 * i)]
            for i in range(self.u32(params)))
        descriptor = '(' + args + ')' + self.types[self.u32(proto + 4)]
        return (self.types[self.u16(pos)], self.strings[self.u32(pos + 4)], descriptor)

    def definitions(self):
        for i in range(self.u32(96)):
            pos = self.u32(100) + 32 * i
            cls = self.types[self.u32(pos)]
            superclass = self.u32(pos + 8)
            yield 'class', cls, None if superclass == 0xffffffff else self.types[superclass]
            data_pos = self.u32(pos + 24)
            if not data_pos:
                continue
            counts = []
            for _ in range(4):
                value, data_pos = self.uleb(data_pos)
                counts.append(value)
            for group, count in enumerate(counts):
                index = 0
                for _ in range(count):
                    diff, data_pos = self.uleb(data_pos)
                    index += diff
                    flags, data_pos = self.uleb(data_pos)
                    if group < 2:
                        yield 'field', self.field(index), flags
                    else:
                        _, data_pos = self.uleb(data_pos)  # code_off
                        yield 'method', self.method(index), flags


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    args = parser.parse_args()
    digest = hashlib.sha256()
    with open(args.apk, 'rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    print('SHA256:', digest.hexdigest())
    found = {'field': {}, 'method': {}, 'class': {}}
    with zipfile.ZipFile(args.apk) as apk:
        for name in apk.namelist():
            if not name.endswith('.dex'):
                continue
            for kind, key, value in Dex(apk.read(name)).definitions():
                if (kind == 'field' and key in FIELDS or
                        kind == 'method' and key in METHODS or
                        kind == 'class' and key == PAINT):
                    if key in found[kind]:
                        raise SystemExit('FAIL: duplicate definition: ' + str(key))
                    found[kind][key] = (value, name)
    errors = []
    for kind, expected in [('field', FIELDS), ('method', METHODS)]:
        for key, want_static in expected.items():
            actual = found[kind].get(key)
            if actual is None:
                errors.append('missing ' + kind + ': ' + str(key))
            elif bool(actual[0] & 8) != want_static:
                errors.append('wrong static flag: ' + str(key))
            else:
                print('PASS:', actual[1], kind, '->'.join(key),
                      'static' if want_static else 'instance')
    paint = found['class'].get(PAINT)
    if paint is None or paint[0] != 'Landroid/text/TextPaint;':
        errors.append('exteraGram TextPaint must extend android.text.TextPaint')
    else:
        print('PASS: exteraGram TextPaint extends android.text.TextPaint')
    if errors:
        raise SystemExit('FAIL:\n' + '\n'.join(errors))
    print('PASS: all 10 field and 8 method definitions match both legacy224 hooks.')
    print('This verifies APK schema, not on-device rendering or runtime hook execution.')


if __name__ == '__main__':
    main()
