#!/usr/bin/env python3
"""用本地 DexKit JVM 工程检查 Telegram APK，直接提取当前生产代码的查询和权限逻辑。"""
import argparse
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--dexkit', type=Path, default=Path.home() / 'DexKit')
parser.add_argument('--feature', action='append', default=[], help='只检查指定功能，可重复传入')
parser.add_argument('apks', nargs='+', type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
dexkit = args.dexkit.resolve()
for apk in args.apks:
    if not apk.is_file():
        parser.error(f'APK 不存在：{apk}')
with tempfile.TemporaryDirectory(prefix='myinjector-tg-audit-') as directory:
    source = Path(directory)
    subprocess.run(['python3', str(repo / 'scripts/telegram_audit/generate_telegram_audit.py'),
                    '--output', str(source / 'TelegramAudit.kt')], check=True)
    (source / 'ApkAuditMain.kt').write_text('''package telegramaudit
import org.luckypray.dexkit.DexKitBridge
fun main(args: Array<String>) {
    System.loadLibrary("dexkit")
    val features = args.filter { it.startsWith("--feature=") }.map { it.substringAfter('=') }.toSet()
    for (apk in args.filterNot { it.startsWith("--feature=") }) {
        println("APK $apk")
        TelegramResources.apkPaths = listOf(apk) + java.io.File(apk).parentFile
            .listFiles().orEmpty().filter { it.name.startsWith("split") && it.extension == "apk" }
            .map { it.absolutePath }
        DexKitBridge.create(apk).use { auditAll(it, features) }
    }
}
''')
    # 用临时 source set 执行，不改动 DexKit 工程里的现有源文件。
    init = source / 'audit.gradle'
    init.write_text('''gradle.projectsEvaluated {
    def p = gradle.rootProject.project(':main')
    p.sourceSets.main.java.setSrcDirs([])
    p.kotlin.sourceSets.main.kotlin.setSrcDirs([System.getenv('TELEGRAM_AUDIT_SOURCE_DIR')])
    p.application.mainClass.set('telegramaudit.ApkAuditMainKt')
}
''')
    env = dict(os.environ, TELEGRAM_AUDIT_SOURCE_DIR=str(source))
    subprocess.run([str(dexkit / 'gradlew'), '-I', str(init), ':main:run', '--console=plain',
                    '--args=' + shlex.join([*('--feature=' + name for name in args.feature),
                                           *(str(apk.resolve()) for apk in args.apks)])],
                   cwd=dexkit, env=env, check=True)
