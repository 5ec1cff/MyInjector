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
    print('PASS: 系统二级页开关、无设置应用开关、独立设置入口', flush=True)
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
    locate('系统界面')
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
