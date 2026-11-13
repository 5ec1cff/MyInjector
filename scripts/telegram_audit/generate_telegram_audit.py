from pathlib import Path
import re

root = Path(__file__).resolve().parents[2] / 'app/src/main/java/io/github/a13e300/myinjector'
base = Path(__file__).resolve().parent
import argparse
parser = argparse.ArgumentParser(description='从生产代码提取 Telegram APK 静态适配检查')
parser.add_argument('--output', type=Path, required=True)
target = parser.parse_args().output

def mask(s):
    # Keep offsets while ignoring braces in Kotlin strings and comments.
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern, lambda m: ''.join('\n' if c == '\n' else ' ' for c in m.group()), s)

def function(s, name):
    m = re.search(r'(?m)^\s*(?:(?:private|internal|override)\s+)?fun '+name+r'\(', s)
    assert m, name
    masked = mask(s)
    start = masked.index('{', m.end())
    depth = 1
    end = start + 1
    while depth:
        depth += (masked[end] == '{') - (masked[end] == '}')
        end += 1
    return s[m.start():end].strip()

def source(name):
    return (root / 'telegram' / (name + '.kt')).read_text()

def body(s):
    return re.sub(r'(?m)^(package|import) [^\n]*\n', '', s).strip()

out = ['''package telegramaudit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.*
import org.luckypray.dexkit.wrap.*
import org.luckypray.dexkit.query.enums.*
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.ZoneId
import java.util.WeakHashMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import kotlin.math.min

typealias ObfsTable = Map<String, ObfsInfo>
data class ObfsInfo(val className: String, val memberName: String, val descriptor: String = "")
fun ClassData.toObfsInfo() = ObfsInfo(name, "")
fun MethodData.toObfsInfo() = ObfsInfo(className, name, descriptor)
fun FieldData.toObfsInfo() = ObfsInfo(className, name, descriptor)
fun logE(message: String) { System.err.println(message) }
class ObfsTableCreator(val bridge: DexKitBridge, base: ObfsInfo) {
    val obfsTable = linkedMapOf("BaseFragment" to base)
    fun create(key: String, callback: (DexKitBridge) -> ObfsInfo): ObfsInfo =
        obfsTable.getOrPut(key) {
            try {
                callback(bridge).also { println("MEMBER $key ${it.descriptor.ifEmpty { it.className.ifEmpty { it.memberName } }}") }
            } catch (t: Throwable) { throw IllegalStateException("$key: ${t.message}", t) }
        }
}
''']
out.append(body((root / 'arch/OpcodeUtils.kt').read_text()))
for name in ['TelegramFactoryFields', 'SearchStateDeobf', 'StickerStreamDeobf', 'EmojiDeobf', 'TelegramResources', 'TelegramApiDeobf', 'TelegramNamedMembers', 'AnchoredSearchDeobf', 'MessageSearchWindow', 'MessageSearchFilters', 'TelegramDexUtils', 'TelegramDexScan', 'ProfileAvatarBlurDeobf', 'ProfileActivityRowsDeobf', 'SecretMediaDeobf', 'ProfileMenuDeobf', 'InfoContainerDeobf', 'StickerPackRefreshDeobf']:
    out.append(body(source(name)))
for name, func in [('OpenLinkDialog', 'findOpenLinkDialog'), ('EmojiStickerMenu', 'findEmojiStickerMenu'), ('CopyPrivateChatLink', 'findCopyPrivateChatLink'), ('ShowDCInProfile', 'findProfileDataCenter'), ('HistoricalNewsOption', 'findHistoricalNews'), ('RepeatMessage', 'findRepeatMessage')]:
    out.append(function(source(name), func))
out.append(function(source('EmojiStickerMenu'), 'findEmojiStickerMenuDynamic'))

classes = ['Settings', 'ContactPermission', 'AutoCheckDeleteMessageOption', 'MutualContact', 'DisableVoiceOrCameraButton', 'DefaultSearchTab', 'HidePhoneNumber', 'AutoUncheckSharePhoneNumber', 'AlwaysShowStorySaveIcon', 'ShowMsgId', 'ProhibitChannelSwitching', 'FixHasAppToOpen', 'LongClickMention', 'AntiAntiCopy', 'AvatarPagerScrollToCurrent', 'OpenTgUserLink']
helpers = {
    'HidePhoneNumber': ['locateProfileActivityOnBindViewHolderAndRowFields'],
    'ShowMsgId': ['locateMegaGroup', 'locateTimeWidth'],
    'LongClickMention': ['locateMentionsAdapterPositionAndLength']
}
for name in classes:
    methods = [function(source(name), 'deobf').replace('private fun deobf', 'fun deobf')]
    methods += [function(source(name), helper) for helper in helpers.get(name, [])]
    combined = '\n'.join(methods)
    nullable = set(re.findall(r'var (\w+): (?:MethodData|FieldData|ClassData)\?', combined))
    for variable in nullable:
        combined = re.sub(r'\b'+variable+r'(?=\.|\s*[,\)])', variable+'!!', combined)
    out.append('private object Audit' + name + ' {\n' + combined + '\n}')

prefixes = {
    'HideFloatFab': ('val createView = creator.create', 'val buttons ='),
    'SendImageWithHighQualityByDefault': ('val setHighQuality = creator.create', 'val photoEntry = DexField'),
    'CustomMapPosition': ('val found by lazy', 'fun field(key:')
}
for name, (start, end) in prefixes.items():
    s = source(name)
    code = s[s.index(start):s.index(end)]
    out.append('private fun audit' + name + '(creator: ObfsTableCreator) {\n' + code + '\n}')

# 检查运行时单独解析的 API，避免只检查 findHistoricalNews 而漏掉初始化失败。
history_apis = set()
for name in ['HistoricalNewsOption', 'AnchoredMessageSearch', 'SearchFiltersUi']:
    for kind, args in re.findall(r'\b(telegramApi(?:Class|Method|Field))\((?:classLoader|loader), ("[^\n]+?)\)', source(name)):
        history_apis.add(f'    {kind}(bridge, {args})')
out.append('private fun auditHistoricalNewsApis(bridge: DexKitBridge) {\n' +
           '\n'.join(sorted(history_apis)) + '\n}')

out.append((base / 'telegram_audit_extra.kt').read_text())
permission_methods = '\n'.join(function(source('RepeatMessage'), name) for name in ['canSend', 'canSendWhileSearching'])
permission_methods = permission_methods.replace('private fun canSend(', 'fun canSend(')
out.append((base / 'telegram_repeat_permissions_test.kt').read_text().replace('// PRODUCTION_METHODS', permission_methods))

out.append('''
fun auditAll(bridge: DexKitBridge, features: Set<String> = emptySet()) {
    val base = (bridge.getClassData("org.telegram.ui.ActionBar.BaseFragment") ?: bridge.findClass { matcher { addField { name("fragmentView"); type("android.view.View") } } }.single()).toObfsInfo()
    val failures = mutableListOf<String>()
    var passed = 0
    fun checkFeature(name: String, action: () -> Unit) {
        if (features.isNotEmpty() && name !in features) return
        try { action(); println("PASS $name"); passed++ }
        catch (t: Throwable) { println("FAIL $name ${t.message}"); t.printStackTrace(); failures += name }
    }
    fun creator() = ObfsTableCreator(bridge, base)
    fun validateMap(members: Map<String, String>) {
        for ((key, value) in members) {
            println("MEMBER $key $value")
            when {
                value.isEmpty() || value.toIntOrNull() != null -> Unit
                "->" !in value -> check(bridge.getClassData(value) != null) { key }
                else -> value.lines().forEach { descriptor ->
                    check(if ('(' in descriptor) bridge.getMethodData(descriptor) != null else bridge.getFieldData(descriptor) != null) { key }
                }
            }
        }
    }
''')
for name in classes:
    out.append(f'    checkFeature("{name}") {{ Audit{name}.deobf(creator()) }}')
for name in prefixes:
    out.append(f'    checkFeature("{name}") {{ audit{name}(creator()) }}')
for name, call in [
    ('DisableProfileAvatarBlur', 'findProfileAvatarBlur(bridge, base.className).also { auditProfileBlurMembers(bridge, it) }'),
    ('ShowIdInProfile', 'findProfileActivityRows(bridge, base.className)'),
    ('ShowDCInProfile', 'findProfileDataCenter(bridge, bridge.getMethodData(findProfileActivityRows(bridge, base.className).getValue("updateRows"))!!.className).mapValues { it.value.descriptor }'),
    ('OpenLinkDialog', 'findOpenLinkDialog(bridge)'),
    ('EmojiStickerMenu', 'findEmojiStickerMenu(bridge, base.className)'),
    ('RefreshStickerPacks', 'findStickerPackRefresh(bridge).also { auditStickerPackRefresh(bridge, it) }'),
    ('CopyPrivateChatLink', 'findCopyPrivateChatLink(bridge)'),
    ('SaveSecretImage', 'findSaveSecretImage(bridge, base.className)'),
    ('AddSubItemChannel', 'findProfileMenu(bridge)'),
    ('AddInfoContainer', 'findInfoContainer(bridge, findProfileMenu(bridge).getValue("menu"))'),
    ('Anchored search state', 'findSearchState(bridge, bridge.getMethodData(findCopyPrivateChatLink(bridge).getValue("createMenu"))!!.className)'),
    ('HistoricalNewsOption', 'findHistoricalNews(bridge, bridge.getMethodData(findCopyPrivateChatLink(bridge).getValue("createMenu"))!!.className).also { auditHistoricalNewsApis(bridge) }'),
    ('Anchored search list', 'findAnchoredSearch(bridge, bridge.getMethodData(findCopyPrivateChatLink(bridge).getValue("createMenu"))!!.className)'),
    ('Search controls', 'findSearchControls(bridge, bridge.getMethodData(findCopyPrivateChatLink(bridge).getValue("createMenu"))!!.className)'),
    ('RepeatMessage', 'findCopyPrivateChatLink(bridge).let { findRepeatMessage(bridge, bridge.getMethodData(it.getValue("createMenu"))!!.className, it.getValue("createMenu"), it.getValue("options")) }')
]:
    out.append(f'    checkFeature("{name}") {{ validateMap({call}) }}')
out.append('''
    for (feature in listOf("UseSystemEmoji", "NoGoogleMaps", "ShowExactLastSeenTime", "ChannelDetailNumbers", "AlwaysShowDownloadManager", "RemoveArchiveFolder", "StickerLoadGuard")) {
        checkFeature(feature) { auditKeptApi(bridge, feature) }
    }
    checkFeature("Reflection shapes") { auditReflectionShapes(bridge) }
    checkFeature("Repeat menu availability") { RepeatPermissionTest.run() }
    println("AUDIT $passed passed, ${failures.size} failed: $failures")
    check(passed + failures.size > 0) { "No matching feature: $features" }
    check(failures.isEmpty()) { "Failed: $failures" }
}
''')
combined = '\n\n'.join(out) + '\n'
target.write_text(combined)
print(target, 'lines', sum(x.count('\n') + 2 for x in out))
