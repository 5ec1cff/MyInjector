import asyncio
import os
import sys
from telethon import TelegramClient
import json

API_ID = 611335
API_HASH = "d524b414d21f4d37f08684c1df41ac9c"


BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
TITLE = os.environ.get("TITLE")
RUN_URL = os.environ.get("RUN_URL")

GITHUB_EVENT = json.loads(os.environ.get("GITHUB_EVENT"))

if 'commits' in GITHUB_EVENT:
    commits = GITHUB_EVENT['commits']
    commit_message = ''
    i = len(commits)
    for commit in commits[::-1]:
        msg = commit['message']
        if len(msg) > 256:
            msg = msg[:253] + '...'
        if len(msg) + 1 + len(commit_message) > 980:
            commit_message = f'(other {i} commits)\n{commit_message}'
        else:
            commit_message = f'{msg}\n{commit_message}'
        i -= 1
    if 'forced' in GITHUB_EVENT and GITHUB_EVENT['forced']:
        fp_info = '**Force Pushed**\n'
    else:
        fp_info = ''
    commit_message = f'```{commit_message.strip()}\n```{fp_info}'
elif 'head_commit' in GITHUB_EVENT:
    msg = GITHUB_EVENT["head_commit"]["msg"]
    if len(msg) > 256:
        msg = msg[:253] + '...'
    commit_message = f'```{msg.strip()}\n```'
else:
    commit_message = ''

if 'compare' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['compare']
    commit_line = '[Compare](' + commit_url + ')\n'
elif 'head_commit' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['head_commit']['url']
    commit_line = '[Commit](' + commit_url + ')\n'
else:
    commit_line = ''


MSG_TEMPLATE = """
**{title}**
{commit_message}{commit_url}[Workflow run]({run_url})
""".strip()


def get_caption():
    msg = MSG_TEMPLATE.format(
        title=TITLE,
        commit_message=commit_message,
        commit_url=commit_line,
        run_url=RUN_URL,
    )
    if len(msg) > 1024:
        return RUN_URL
    return msg


def check_environ():
    global CHAT_ID, MESSAGE_THREAD_ID
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if CHAT_ID is None:
        print("[-] Invalid CHAT_ID")
        exit(1)
    else:
        try:
            CHAT_ID = int(CHAT_ID)
        except:
            pass
    if RUN_URL is None:
        print("[-] Invalid RUN_URL")
        exit(1)
    if TITLE is None:
        print("[-] Invalid TITLE")
        exit(1)
    if MESSAGE_THREAD_ID is not None and MESSAGE_THREAD_ID != "":
        try:
            MESSAGE_THREAD_ID = int(MESSAGE_THREAD_ID)
        except:
            print("[-] Invaild MESSAGE_THREAD_ID")
            exit(1)
    else:
        MESSAGE_THREAD_ID = None


async def main():
    print("[+] Uploading to telegram")
    check_environ()
    files = sys.argv[1:]
    print("[+] Files:", files)
    if len(files) <= 0:
        print("[-] No files to upload")
        exit(1)
    print("[+] Logging in Telegram with bot")
    script_dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    session_dir = os.path.join(script_dir, "ksubot")
    async with await TelegramClient(session=session_dir, api_id=API_ID, api_hash=API_HASH).start(bot_token=BOT_TOKEN) as bot:
        caption = [""] * len(files)
        caption[-1] = get_caption()
        print("[+] Caption: ")
        print("---")
        print(caption)
        print("---")
        print("[+] Sending")
        await bot.send_file(entity=CHAT_ID, file=files, caption=caption, reply_to=MESSAGE_THREAD_ID, parse_mode="markdown")
        print("[+] Done!")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")
