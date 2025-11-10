# 自定义 Telegram emoji 映射

该功能允许你将文本映射到 telegram emoji ，并在粘贴时自动替换，便于从其他平台复制含 emoji 文本时将文本化的
emoji 用 Telegram 已有的 emoji 包替换

注：

1. 你需要自己准备 emoji 包和映射文件（仓库中包含一个[样例](tg-custom-emoji-mapping-sample.json)
   ，包含作者自制的来自 bilibili、贴吧和酷安的部分表情）
2. 目前，只有使用消息输入框中长按菜单的粘贴按钮才能对文本进行替换

## 映射文件格式

```json lines
{
  // 文本映射到单个 emoji
  "emoji-text": [
    "emoji-id",
    [
      "associated-emoji"
    ]
  ],
  // 文本映射到多个 emoji
  "emoji-text2": [
    [
      "emoji-id",
      [
        "associated-emoji"
      ],
      "key"
    ],
    [
      // ...
    ]
  ]
}
```

说明：

- emoji-text ：要用于替换的 emoji 文本
- emoji-id ：telegram 中的 emoji id ([custom_emoji_id](https://core.telegram.org/bots/api#sticker))
- associated-emoji ：emoji-id 所对应的 telegram emoji 中关联的 emoji 字符（至少要有一个）
- key ：当文本映射到多个 emoji 时需要，用于区分不同的 emoji （参见「多个 emoji」）

## 多个 emoji

默认情况下，如果映射表提供了多个 emoji ，则取数组的第一个 emoji 使用。

你可以在要粘贴的文本前加上偏好设置字符串 `!!use:key1,key2,...` ，即 `!!use:` 跟着一串逗号分隔的
key，后还需跟一个空格或者换行，
用于选择当前粘贴时偏好的 emoji key ，提供的 key 的顺序决定优先使用哪一个 key 的 emoji
。粘贴之后，偏好设置字符串会被从粘贴结果中移除。
