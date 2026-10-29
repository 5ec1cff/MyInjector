#!/usr/bin/env python3
"""在指定模拟器回归设置导航和普通 Activity 中的 Compose 弹窗（需 debug APK）。"""
import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--out', type=Path, default=Path('app/build/reports/ui-smoke'))
parser.add_argument('--resource-context-package', help='仅加载此已安装包的资源，复现无 Application 的 Context')
parser.add_argument('--dialogs-only', action='store_true')
parser.add_argument('--insets-only', action='store_true', help='验证状态栏和键盘开关后的列表边界')
parser.add_argument('--sticky-search-only', action='store_true', help='验证首页及二级页面搜索栏滚动吸顶')
parser.add_argument('--launcher-icon-only', action='store_true', help='验证桌面 Activity、可见性开关及框架设置入口')
parser.add_argument('--scope-only', action='store_true', help='验证作用域开关与独立设置入口')
parser.add_argument('--service-state-only', action='store_true', help='回归首页缓存入口的异步服务状态更新')
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
package = 'io.github.a13e300.myinjector'


def adb(*parts):
    return subprocess.check_output(['adb', '-s', args.serial, *parts], timeout=30)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/myinjector-test.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/myinjector-test.xml'))


def locate(label):
    for _ in range(4):
        root = tree()
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in root.iter('node'):
            if node.get('text') == label or node.get('content-desc') == label:
                return node, parents
        time.sleep(0.25)
    raise AssertionError('未找到：' + label)


def tap(label):
    node, _ = locate(label)
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def switch(label):
    node, parents = locate(label)
    while node.get('checkable') != 'true':
        node = parents[node]
    return node.attrib


def capture(name):
    tree()  # 等待 UI 稳定后再截图。
    (args.out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))


def start(activity):
    adb('shell', 'am', 'force-stop', package)
    extra = ['--es', 'resource_context_package', args.resource_context_package] if (
        args.resource_context_package and activity == '.ui.DialogTestActivity'
    ) else []
    adb('shell', 'am', 'start', '-W', '-n', package + '/' + activity, *extra)


if args.launcher_icon_only:
    entry = package + '/.LauncherActivity'
    def resolve(category):
        return adb('shell', 'cmd', 'package', 'query-activities', '--brief', '--user', '0',
            '-a', 'android.intent.action.MAIN', '-c', category, '-p', package).decode()

    def launcher():
        return resolve('android.intent.category.LAUNCHER')

    def module_settings():
        result = resolve('de.robv.android.xposed.category.MODULE_SETTINGS')
        assert package + '/.SettingsActivity' in result, ('模块设置入口被禁用', result)

    try:
        adb('shell', 'pm', 'default-state', '--user', '0', entry)
        start('.LauncherActivity')
        assert switch('显示桌面图标')['checked'] == 'true'
        assert launcher().count(entry) == 1, '应只有一个桌面入口且由独立 Activity 提供'
        pid = adb('shell', 'pidof', package).strip()
        tap('显示桌面图标')
        assert switch('显示桌面图标')['checked'] == 'false'
        assert entry not in launcher(), '关闭后仍有桌面入口'
        assert adb('shell', 'pidof', package).strip() == pid, '切换图标不应杀死模块进程'
        module_settings()
        capture('launcher-icon-hidden')
        start('.SettingsActivity')
        assert switch('显示桌面图标')['checked'] == 'false', '重启后应从组件读取隐藏状态'
        tap('显示桌面图标')
        assert switch('显示桌面图标')['checked'] == 'true'
        assert launcher().count(entry) == 1
        # debug 接收器只修改 PM 状态；实际 PACKAGE_CHANGED 接收器必须在前台实时刷新 UI。
        def resumed_activity():
            return next(line.strip() for line in adb('shell', 'dumpsys', 'activity', 'activities').decode().splitlines()
                if 'topResumedActivity=' in line and package in line)
        activity_record = resumed_activity()
        pid = adb('shell', 'pidof', package).strip()
        for visible in [False, True]:
            adb('shell', 'am', 'broadcast', '--user', '0', '-n', package + '/.ui.LauncherStateTestReceiver',
                '--ez', 'visible', str(visible).lower())
            deadline = time.monotonic() + 30
            while switch('显示桌面图标')['checked'] != str(visible).lower():
                assert time.monotonic() < deadline, 'PACKAGE_CHANGED 未刷新图标开关'
                time.sleep(0.25)
            assert resumed_activity() == activity_record, '广播更新不应重新打开 Activity'
            assert adb('shell', 'pidof', package).strip() == pid, '广播更新不应重启进程'
            assert (entry in launcher()) == visible
        # 从系统侧修改组件状态，重新打开页面必须反映真实状态，不能使用独立偏好缓存。
        adb('shell', 'pm', 'disable', '--user', '0', entry)
        start('.SettingsActivity')
        assert switch('显示桌面图标')['checked'] == 'false'
        adb('shell', 'pm', 'enable', '--user', '0', entry)
        start('.SettingsActivity')
        assert switch('显示桌面图标')['checked'] == 'true'
        module_settings()
        capture('launcher-icon-visible')
        print('PASS: Activity 唯一桌面入口、显示/隐藏、PACKAGE_CHANGED 前台同步、进程及 Activity 保留、重启及外部组件状态同步、框架入口保留', flush=True)
    finally:
        adb('shell', 'pm', 'default-state', '--user', '0', entry)
    sys.exit(0)

if args.sticky_search_only:
    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))

    def geometry(list_id):
        root = tree()
        def tagged(tag):
            return next(n for n in root.iter('node') if n.get('resource-id') == tag)
        return bounds(tagged('settings_search_header')), bounds(tagged(list_id)), bounds(tagged('settings_top_bar'))

    def scroll_up(list_id):
        _, content, _ = geometry(list_id)
        x = (content[0] + content[2]) // 2
        height = content[3] - content[1]
        adb('shell', 'input', 'swipe', str(x), str(content[1] + height * 4 // 5),
            str(x), str(content[1] + height // 5), '350')

    def check_pinned(list_id, name):
        header, content, _ = geometry(list_id)
        assert header[1] > content[1], '搜索栏应从列表原位置开始滚动'
        for _ in range(3):
            scroll_up(list_id)
        header, content, bar = geometry(list_id)
        assert abs(header[1] - content[1]) <= 1, ('搜索栏没有吸顶', header, content)
        assert header[1] >= bar[3], ('搜索栏被标题栏遮挡', header, bar)
        scroll_up(list_id)
        after, _, _ = geometry(list_id)
        assert after == header, ('继续滚动后搜索栏位置改变', header, after)
        capture(name)

    # 固定长列表，避免当前设备安装应用太少、滚动到底仍无法到达吸顶位置。
    activity = '.ui.SettingsStateTestActivity'
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package + '/' + activity,
        '--ez', 'connected', 'true', '--ez', 'scope_test', 'true', '--ez', 'scroll_test', 'true')
    check_pinned('settings_home', 'sticky-search-home')
    start(activity)
    tap('系统框架')
    check_pinned('section_system', 'sticky-search-section')
    tap('搜索此页设置')
    adb('shell', 'input', 'text', 'FORCENEWTASKRULES')
    adb('shell', 'input', 'keyevent', '4')
    locate('强制 NEW_TASK 规则')
    tap('清除搜索')
    locate('搜索此页设置')
    print('PASS: 首页及二级页搜索栏随列表上移、吸顶后保持位置且可继续搜索', flush=True)
    sys.exit(0)

if args.insets_only:
    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))

    def frame(kind):
        output = adb('shell', 'dumpsys', 'window', 'displays').decode()
        pattern = (r'type=(?:ITYPE_' + kind.upper() + '|' + kind + r')\b[^\n]*?'
                   r'frame=\[(\d+),\s*(\d+)\]\[(\d+),\s*(\d+)\][^\n]*?visible=true')
        matches = re.findall(pattern, output)
        assert matches, '未找到可见 Insets: ' + kind
        return tuple(map(int, matches[0]))

    def check_layout(list_id, keyboard=False):
        root = tree()
        bar = next(n for n in root.iter('node') if n.get('resource-id') == 'settings_top_bar')
        content = next(n for n in root.iter('node') if n.get('resource-id') == list_id)
        status_bottom = frame('statusBars')[3] if 'ITYPE_STATUS_BAR' not in adb('shell', 'dumpsys', 'window', 'displays').decode() else frame('status_bar')[3]
        # 标题栏背景延伸到状态栏，标题文字与返回按钮仍在安全区域内。
        titles = [n for n in bar.iter('node') if n.get('text') in ('MyInjector', '系统框架')]
        assert titles, '未找到标题文字'
        assert all(bounds(n)[1] >= status_bottom for n in titles), ('标题文字进入状态栏', status_bottom)
        assert bounds(bar)[1] == 0, ('标题栏背景未延伸到状态栏', bounds(bar))
        if keyboard:
            keyboard_top = frame('ime')[1]
            assert bounds(content)[3] <= keyboard_top, ('列表被键盘覆盖', bounds(content), keyboard_top)
        return bounds(content)

    start('.SettingsActivity')
    home_before = check_layout('settings_home')
    tap('搜索应用或系统组件')
    home_keyboard = check_layout('settings_home', True)
    assert home_keyboard[3] < home_before[3]
    capture('insets-home-ime')
    adb('shell', 'input', 'swipe', '540', '1200', '540', '520', '350')
    check_layout('settings_home', True)
    capture('insets-home-collapsed-ime')
    adb('shell', 'input', 'keyevent', '4')
    assert check_layout('settings_home')[3] == home_before[3]
    start('.SettingsActivity')
    tap('系统框架')
    section_before = check_layout('section_system')
    tap('搜索此页设置')
    section_keyboard = check_layout('section_system', True)
    assert section_keyboard[3] < section_before[3]
    adb('shell', 'input', 'swipe', '540', '1200', '540', '520', '350')
    check_layout('section_system', True)
    capture('insets-section-ime')
    adb('shell', 'input', 'keyevent', '4')
    assert check_layout('section_system')[3] == section_before[3]
    tap('返回首页')
    check_layout('settings_home')
    print('PASS: 首页/二级页键盘避让、收起恢复、折叠标题和返回导航安全边界', flush=True)
    sys.exit(0)

if args.scope_only:
    activity = '.ui.SettingsStateTestActivity'
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package + '/' + activity,
        '--ez', 'scope_test', 'true', '--ez', 'connected', 'true')
    tap('系统框架')
    assert switch('启用作用域')['checked'] == 'false'
    tap('启用作用域')
    assert switch('启用作用域')['checked'] == 'true'
    tap('返回首页')
    # 系统组件按设置能力排序；无模块内设置者与普通应用共用列表开关。
    tap('搜索应用或系统组件')
    adb('shell', 'input', 'text', 'SysApp')
    adb('shell', 'input', 'keyevent', '4')
    root = tree()
    texts = [n.get('text') for n in root.iter('node') if n.get('text')]
    assert texts.index('Z SysApp Telegram') < texts.index('A SysApp Settings'), '有应用内设置者应排在前面'
    assert '系统组件' in texts and '应用作用域' not in texts, '系统应用应只出现在系统组件组'
    assert switch('A SysApp Settings作用域')['checked'] == 'false'
    tap('A SysApp Settings作用域')
    assert switch('A SysApp Settings作用域')['checked'] == 'true'
    assert not any(n.get('content-desc') == '返回首页' for n in tree().iter('node')), '普通系统应用不应进入二级页面'
    tap('应用内设置')
    assert switch('Z SysApp Telegram作用域')['checked'] == 'false', '系统应用设置入口不能切换作用域'
    capture('scope-system-apps')
    tap('清除搜索')
    tap('搜索应用或系统组件')
    adb('shell', 'input', 'text', 'Termux')
    adb('shell', 'input', 'keyevent', '4')
    assert switch('Termux作用域')['checked'] == 'false'
    tap('Termux作用域')
    assert switch('Termux作用域')['checked'] == 'true'
    assert not any(n.get('text') == '应用内设置' for n in tree().iter('node'))
    capture('scope-no-settings')
    tap('清除搜索')
    tap('搜索应用或系统组件')
    adb('shell', 'input', 'text', 'Chrome')
    adb('shell', 'input', 'keyevent', '4')
    locate('应用内设置')
    tap('应用内设置')
    assert switch('Chrome作用域')['checked'] == 'false', '设置入口不能切换作用域'
    capture('scope-with-settings')
    print('PASS: 系统模块设置二级开关、系统/普通应用列表开关、设置优先排序与独立设置入口', flush=True)
    sys.exit(0)

if args.service_state_only:
    activity = '.ui.SettingsStateTestActivity'
    connected = '模块服务已连接 · 按应用管理你的设置'
    disconnected = '模块服务未连接 · 请在框架中启用模块'

    def set_connected(value):
        # SINGLE_TOP 调用同一 Activity 的 onNewIntent，不重建 Activity 或导航栈。
        adb('shell', 'am', 'start', '-W', '-f', '0x20000000', '-n', package + '/' + activity,
            '--ez', 'connected', str(value).lower())

    start(activity)
    locate(disconnected)
    set_connected(True)
    locate(connected)
    set_connected(False)
    locate(disconnected)
    tap('系统框架')
    set_connected(True)
    locate('禁用 MIUI wake path')
    tap('返回首页')
    locate(connected)
    set_connected(False)
    locate(disconnected)
    capture('service-state')
    print('PASS: 首页连接／断开实时刷新；二级页面更新状态后返回显示最新值', flush=True)
    sys.exit(0)

if not args.dialogs_only:
    start('.SettingsActivity')
    locate('系统框架')
    capture('home')
    tap('系统框架')
    before = switch('禁用 MIUI wake path')['checked']
    tap('禁用 MIUI wake path')
    assert switch('禁用 MIUI wake path')['checked'] != before
    start('.SettingsActivity')
    tap('系统框架')
    assert switch('禁用 MIUI wake path')['checked'] != before, '开关未持久化'
    tap('禁用 MIUI wake path')
    assert switch('禁用 MIUI wake path')['checked'] == before
    capture('system')
    tap('搜索此页设置')
    adb('shell', 'input', 'text', 'forceNewTaskRules')
    adb('shell', 'input', 'keyevent', '4')
    tap('强制 NEW_TASK 规则')
    locate('已添加的条目：')
    capture('rules')
    tap('取消')
    tap('清除搜索')
    tap('剪切板白名单列表')
    locate('保存')
    capture('clipboard')
    tap('取消')
    tap('返回首页')
    locate('系统框架')
    print('PASS: 模块导航、开关持久化、搜索、规则编辑和剪切板选择入口', flush=True)

start('.ui.DialogTestActivity')
if args.resource_context_package:
    locate('资源 Context：无 Application')
tap('打开注入设置')
assert switch('仅对自己隐藏')['enabled'] == 'false'
tap('隐藏手机号')
assert switch('隐藏手机号')['checked'] == 'true'
assert switch('仅对自己隐藏')['enabled'] == 'true'
tap('仅对自己隐藏')
assert switch('仅对自己隐藏')['checked'] == 'true'
tap('拒绝修改')
assert switch('拒绝修改')['checked'] == 'false'
capture('injected')
tap('错误详情')
locate('测试错误：保留详情与关闭操作')
tap('关闭')
assert switch('仅对自己隐藏')['checked'] == 'true', '关闭嵌套弹窗后状态丢失'
tap('搜索设置')
adb('shell', 'input', 'text', 'feature24')
locate('功能 24')
adb('shell', 'input', 'keyevent', '4')
tap('功能 24')
assert switch('功能 24')['checked'] == 'true'
capture('injected-search')
tap('清除搜索')
locate('隐私')
tap('隐藏手机号')
assert switch('仅对自己隐藏')['enabled'] == 'false'
tap('返回')
locate('配置回调：0')
tap('打开文本输入')
locate('1,2')
capture('input-keyboard')
tap('确定')
locate('打开注入设置')
locate('配置回调：0')
assert b'1,2' in adb('shell', 'run-as', package, 'cat', 'shared_prefs/ui_smoke.xml')
tap('打开注入设置')
locate('隐私')
tap('返回')
locate('配置回调：0')
print('PASS: 普通 Activity 弹窗、联动、拒绝写入、嵌套弹窗、搜索、键盘提交及再次打开后回调释放', flush=True)
