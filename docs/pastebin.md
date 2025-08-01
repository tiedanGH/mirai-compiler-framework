# pb指令帮助文档
- 本文内容为`#pb`指令的使用说明，帮助你快速掌握指令和使用多种输出格式，和使用存储功能让程序支持多段式运行

## 主要内容
- [1. 指令基本功能和使用帮助](#一指令基本功能和使用帮助)
- [2. 输出格式：输出图片或同时输出多条文字和图片](#二输出格式)
- [3. 使用存储功能关联程序多次运行](#三数据存储功能)

---

## 一、指令基本功能和使用帮助
查看本文下方内容前，您需要先了解`#pb`和`#run`指令的基本功能，如您已经掌握基本功能并熟练运用，可直接跳至[第二点](#二输出格式)

## 基本功能和指令使用
### 基本功能
- 本指令基于glot在线编译器接口制作，通过调用glot在线编译器的接口来远程执行代码
    + 具体项目详情可通过指令「`#glot help`」查看
- 本插件支持绝大多数的编程语言
    + 使用指令「`#glot list`」来查看全部支持的语言
- 「`#run <名称> [输入]`」用于运行保存在pb列表中的代码，注意程序执行仅有**15秒时间限制**，且输出内容大小最大**不能超过100wKB**
### 指令使用
- 指令「`#pb help`」可以查看全部功能的帮助
- 指令「`#pb list`」可查看完整数据列表
- 指令「`#pb info <名称>`」可查看对应项目的相关信息和运行示例
### 添加新的代码链接
- 在执行添加指令前，您需要先学会如何上传代码至pastebin网站，目前bot支持的pastebin网站有：
    + [https://pastebin.ubuntu.com/](https://pastebin.ubuntu.com/) （需要登录，支持缓存）
    + [https://glot.io/snippets/](https://glot.io/snippets/) （更新无需修改链接，可直接调试）
    + [https://pastebin.com/](https://pastebin.com/) （需要`raw`，更新无需修改链接）
    + [https://gist.github.com/](https://gist.github.com/) （需登录GitHub，支持修改+缓存）
    + [https://www.toptal.com/developers/hastebin/](https://www.toptal.com/developers/hastebin/)（支持缓存）
    + [https://bytebin.lucko.me/](https://bytebin.lucko.me/)（支持缓存）
    + [https://pastes.dev/](https://pastes.dev/)（支持缓存）
    + [https://p.ip.fi/](https://p.ip.fi/) （支持缓存）
- 部分网站需要注册账户登录后才能使用，已进行标注
- pastebin.com可以游客身份创建链接（但部分功能受限，仍建议注册账户）。内容可以点击`edit`进行修改，而无需创建新链接，但要使用时需要加上`/raw`参数，例如：https://pastebin.com/raw/114514
- GitHub的Gist支持修改内容，但修改后需要点击`raw`按钮获取新链接并替换
- 将您的代码上传后会获得一个链接（或者直接复制浏览器上方的链接），使用指令：
  ```
  #pb add <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]`
  ```
  将链接填写于`源代码URL`处，即可添加新的pb项目
- 如果提示无效的链接，可能原因为链接没有以“https://”开头或没有以“/”结尾
### 修改参数
- 使用指令：
  ```
  #pb set <名称> <参数名> <内容>
  ```
  来修改相关参数
    + 例如：`#pb set test name test2`，将 test 的名字修改为 test2
- **链接隐藏：** 当修改`hide`为开启状态时，对应的代码在info查看相关信息时会隐藏源代码链接（可用于一些包含加密功能的程序）
- **仅限群聊：** 当修改`groupOnly`为开启状态时，对应的代码会被限制为仅群聊执行

## 辅助文件使用帮助
- 辅助文件用于提供部分程序可用的通用功能，使用时需要使用set指令配置
- 在执行时，辅助文件会和代码文件一起上传至glot
### 使用方法
- 使用如下指令配置辅助文件，目前每个程序仅支持添加一个辅助文件：
  ```
  #pb set <名称> util <文件名>
  ```
- 如果您编写了其他好用的辅助文件，欢迎联系铁蛋添加
### 目前支持的辅助文件列表
- `htmlTools.py`
    + html表格辅助生成工具，可直接创建Table对象来配置表格属性，以及单元格的样式和内容，包括合并单元格、设置背景色等常用功能，并使用`to_string()`方法快速转换为html字符串
- `json.h`
    + cpp语言存储功能辅助，用于将JSON字符串解析为可供cpp程序使用的map对象，以及将map重新编码成JSON字符串用于json格式输出

---

## 二、输出格式
本部分关于如何将程序输出转换成不同的格式。在查看下方内容前，您需要先执行指令把在第一步中创建的代码项目的输出格式改为你需要的格式，具体指令为：

> #pb set <名称> format <输出格式> [默认宽度]

其中输出格式包括：
- [text](#1-text)（纯文本）
- [markdown](#2-markdown和html)（md/html转图片）
- [base64](#3-base64)（base64自定义格式输出）
- [image](#4-image)（链接或路径直接发图）
- [LaTeX](#5-latex)（LaTeX转图片）
- [json](#6-json输出使用帮助)（自定义输出格式、图片宽度）
- [ForwardMessage](#7-forwardmessage)（使用json生成包含多条文字/图片消息的转发消息）
- [Audio](#8-audio-tts)（使用json生成文字转语音消息）

具体使用方法详见下文说明

---

## (1) text
- 朴实无华的文本，输出最初的风格
### 使用帮助
- 这个格式没什么好写的，就是纯文本发送，**无需执行指令修改format，程序会默认采用本方法输出**
- 当文本消息过长时（大于550字符），会收入转发消息显示（防止刷屏），但转发消息的容量依然有上限，程序一次的输出最多只能为5000个中文字符（15000个英文字符），多余部分会被截断

---

## (2) markdown和html
- 将markdown和html格式的文档渲染成图片
### 使用帮助
- 程序输出需为markdown或html格式
- 鉴于服务器性能考虑，markdown转图片进程最大执行时间限制为60秒。过于复杂或大小过大的图片可能无法成功生成
### 图片调用
- 支持上传图片至服务器缓存方便调用，指令为：
    + `#pb upload <图片名称(需要包含拓展名)> <【图片】>`
    + markdown图片相关语法：`![](网页链接/本地图片)`
- 如果存在上传方面困难，可联系铁蛋寻求帮助
- 使用upload指令上传的图片可以直接通过“`image​://`”作为路径调用图片
- 同时支持调用LGTBot的图片素材，使用“`lgtbot​://`”作为路径会直接关联至LGTBot的`game`文件夹，如需查看相关图片具体路径，请访问[https://github.com/Slontia/lgtbot](https://github.com/Slontia/lgtbot)
### 样例
- python语言可以通过"import htmlTools"导入html表格辅助生成工具（此文件会在配置辅助文件为“htmlTools.py”后一起上传至glot），内部包含一些表格相关的辅助方法，具体使用方法和代码示例请查看：[https://pastebin.ubuntu.com/p/mfBGHbyS6T/](https://pastebin.ubuntu.com/p/mfBGHbyS6T/)（内含源代码链接）

---

## (3) base64
- 将根据base64的文件类型，自动将base64字符串转换成多种输出格式
- 目前支持转换 文本、图片、语音、视频
### 使用帮助
- 程序输出需为base64格式的字符串，需要包含类型，例如：
    + data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAABhkAAAPGC...(此处省略几万个字符)
    + data:video/mp4;base64,AAAAGGZ0eXBtcDQyAAAAAG1wNDJpc29tA...(此处省略几万个字符)
- 如果输出为图片，当图片像素很高时，base64字符串会相对较长，建议直接使用命令上传图片至缓存来调用。
- 也可以在markdown模式下，直接使用 `![](base64字符串)` 来直接转换base64图片
- 最后：base64终于变得非常有用了

---

## (4) image
- 从链接或本地路径直接获取图片，**可支持发送gif动图**
- 此输出格式下图片发送速度极快，但质量可能会被压缩，原始质量图片请选择markdown输出格式（速度和质量不能兼得）
### 使用帮助
- 程序输出**仅能包含有且仅有一个**`http`开头的URL或`file:///`开头的本地文件路径，例如：
    + `https://i.imgs.ovh/2025/04/20/jXUdp.jpeg`
    + `file:////home/lighthouse/lgtbot-mirai/lgtbot/images/logo_transparent_colorful.svg`
    + `image://boss2.png`

---

## (5) LaTeX
- 将LaTeX格式的文档使用在线API转换成图片，主要用于公式的图片生成
### 使用帮助
- 程序输出需为LaTeX格式的字符串，例如：
```latex
\begin{align}
    E_0 &= mc^2 \\
    E &= \frac{mc^2}{\sqrt{1-\frac{v^2}{c^2}}}
\end{align}
```
- **LaTeX不支持图片宽度`width`配置**
- 当前使用的在线转换API为QuickLaTeX（[https://quicklatex.com/](https://quicklatex.com/)）

---

## (6) json输出使用帮助

## json
- 自定义输出格式、图片宽度和内容，让程序输出可以在文字和图片间随时切换
### 使用帮助
- 使用此功能需要程序输出json格式结果，输出格式应与`JsonMessage`中的格式对应
- `JsonMessage`中包含下方几个参数：
    + `format`(String)——为输出的格式，格式不能使用json和ForwardMessage *（默认为text）*
    + `at`(Boolean)——为文本消息前是否@指令执行者，**仅在`format`为text和MessageChain时生效** *（默认为true）*
    + `width`(Int)——图片的默认宽度，当以text输出时，此项参数不生效 *（默认为600）*
    + `content`(String)——输出的内容，用于输出文字或生成图片，如果是markdown格式需要使用markdown语法 *（默认为“空消息”）*
    + `messageList`(List[`JsonSingleMessage`])——消息链中包含的所有消息，和`content`参数之间仅有一个有效。**MessageChain和`JsonSingleMessage`的使用帮助请在下一张图片中查看**
    + `error`(String)——用于抛出中断异常。当为非空时，bot会直接发送`error`中的消息，**并停止解析输出中的其他任何参数，存储数据也不会保存**
- 上方的全部参数均不是必填项，如没有填写则按照默认值生成
### 使用json输出的几个示例
- text格式输出（`format`和`width`均可省略）
```json
{
    "content": "这是一段文本"
}
```
- markdown格式输出（如果省略`width`则为默认值600）
```json
{
    "format": "markdown",
    "width": 300,
    "content": "### markdown标题\n- 文本内容"
}
```
- base64格式输出（如果宽度小于600，可适当调小`width`）
```json
{
    "format": "base64",
    "content": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAABhkAAA..."
}
```

## [消息链]MessageChain
- 使用json来构造消息链，实现在单条消息中包含多个图片或文字消息
### 使用帮助
- 使用此功能同样需要程序输出`JsonMessage`对象，但仅使用`format`、`at`和`messageList`参数，**传入`JsonMessage`中的其他参数均不生效**
    + `format`中必须填写“MessageChain”，注意大小写
    + `at`(Boolean)——为消息开头是否@指令执行者
    + `messageList`为由数个`JsonSingleMessage`对象组成的列表，用于保存消息链中包含的所有消息，**输出时会按照元素在列表中的顺序依次输出**。
- `JsonSingleMessage`中包含下方几个参数：
    + `format`(String)——为输出的格式，此处格式不能使用json、ForwardMessage、MessageChain和MultipleMessage *（默认为text）*
    + `width`(Int)——图片的默认宽度，当以text输出时，此项参数不生效 *（默认为600）*
    + `content`(String)——输出的内容，用于输出文字或生成图片 *（默认为“空消息”）*
- 上方的全部参数均不是必填项，如没有填写则按照默认值生成
### markdown图片时限
- 单次执行中，markdown转图片进程总时间为60秒。
- 如果包含多张图片，**每个图片的实际执行时间都会被累计**。如果在生成某一张图片时已经达到60秒总上限，**后续图片都将无法生成**
### 使用MessageChain输出的示例
- 下方展示了一个使用存储功能（详见总目录的第三点）和包含多条文字图片消息的json代码
```json
{
    "format": "MessageChain",
    "at": false,
    "messageList": [
        {
            "content": "这是第一段文本"
        },
        {
            "format": "markdown",
            "width": 300,
            "content": "### markdown图片1\n- 文本内容"
        },
        {
            "content": "这是第二段文本"
        },
        {
            "format": "markdown",
            "width": 300,
            "content": "### markdown图片2\n- 文本内容"
        },
        {
            "content": "这是第三段文本"
        },
        {
            "content": "这是第四段文本"
        }
    ],
    "storage": "新用户存储数据",
    "global": "新全局存储数据"
}
```
- 消息链会按照消息顺序依次输出，在每段后自动添加换行符，上方的代码输出后用户看到的结果为：
```text
        这是第一段文本
        【图片1】
        这是第二段文本
        【图片2】
        这是第三段文本
        这是第四段文本
```
- 同时`storage`和`global`也保存在了存储数据中

## [多条消息]MultipleMessage
- 使用json输出多条消息，bot会依次输出消息列表中的内容，单条消息间隔 2 秒（图片类消息耗时可能较大）
### 使用帮助
- 此功能和MessageChain相似，需要程序输出`JsonMessage`对象，但仅使用`format`、`at`和`messageList`参数，**传入`JsonMessage`中的其他参数均不生效**
    + `format`中必须填写“MultipleMessage”，注意大小写
    + `at`(Boolean)——为**每条text文本消息开头**是否@指令执行者
    + `messageList`为由数个`JsonSingleMessage`对象组成的列表，用于保存需要输出的所有消息，**bot会按照元素在列表中的顺序依次发送消息**。
- `JsonSingleMessage`中包含下方几个参数：
    + `format`(String)——为输出的格式，此处格式不能使用json、ForwardMessage、MessageChain和MultipleMessage *（默认为text）*
    + `width`(Int)——图片的默认宽度，当以text输出时，此项参数不生效 *（默认为600）*
    + `content`(String)——输出的内容，用于发送文字或图片 *（默认为“空消息”）*
- 上方的全部参数均不是必填项，如没有填写则按照默认值生成
### 输出限制和markdown时限
- **单次执行时最多输出 15 条消息，超过限制输出会被中断**
- 单次执行中，markdown转图片进程总时间为60秒。
- 如果包含多张图片，**每个图片的实际执行时间都会被累计**。如果在生成某一张图片时已经达到60秒总上限，**后续图片都将无法生成**
### 使用MultipleMessage输出的示例
- 下方展示了一个使用存储功能（详见总目录的第三点）和包含多条文字图片消息的json代码
```json
{
    "format": "MultipleMessage",
    "at": false,
    "messageList": [
        {
            "content": "这是第一段文本"
        },
        {
            "format": "markdown",
            "width": 300,
            "content": "### markdown图片1\n- 文本内容"
        },
        {
            "content": "这是第二段文本"
        },
        {
            "content": "这是第三段文本"
        },
        {
            "content": "这是第四段文本"
        }
    ],
    "storage": "新用户存储数据",
    "global": "新全局存储数据"
}
```
- 多条消息会按照消息顺序依次发送，上方的代码输出后用户看到的结果为，每行均为独立的消息输出：
```text
        这是第一段文本

        【图片1】

        这是第二段文本

        这是第三段文本

        这是第四段文本
```
- 同时`storage`和`global`也保存在了存储数据中

## active主动消息
- 仅支持json输出格式使用
- 在每次执行后可以向指定群聊发送一条文字消息（bot需要在目标群聊内才能发送）
- 关于私信主动消息：**用户必须先执行指令`#pb private`设置可用时间段后，在此时间段bot才能够向该用户发送主动消息**，单次发送限制最多10条，且不能包含相同的用户ID
- 注：群聊和私信主动消息可以同时使用
- **※使用此功能即表示您同意在合理范围内使用，请勿用于任何刷屏或骚扰，机器人后台可查询一切行为记录**
### 使用帮助
- 此功能需要程序输出`JsonMessage`对象，并添加额外参数`active`（`format`可为任意格式）
- **群聊主动消息：** `active`中需要包含两个参数：
    + `group`(Long)——为发送消息的目标群号
    + `content`(String)——为发送的消息内容，**消息前会有`[主动消息]`提示字样**
- **私信主动消息：** `active`中需要包含`private`参数：
    + `private`(List<SinglePrivateMessage>)——为发送私信的消息对象列表，其中每一项需要包含两个参数：
        + `userID`(Long)——为发送目标的账号ID
        + `content`(String)——为发送的消息内容，**消息会包含`[主动消息]`和执行人的账号ID**
### 使用示例
- 下方为使用群聊和私信主动消息的json输出示例：
- 包含群聊主动消息的text格式输出，向群聊`114514`发送消息
```json
{
    "content": "这是一段文本",
    "active": {
        "group": 114514,
        "content": "发送消息内容"
    }
}
```
- 包含私信主动消息的text格式输出，向用户`12345`和`11111`发送消息
```json
{
    "content": "这是一段文本",
    "active": {
        "private": [
            {
                "userID": 12345,
                "content": "私信主动消息1"
            },
            {
                "userID": 11111,
                "content": "私信主动消息2"
            }
        ]
    }
}
```
- 同时包含群聊和私信主动消息的markdown格式输出，向群聊`1919810`和用户`54321`发送消息
```json
{
    "format": "markdown",
    "width": 800,
    "content": "### markdown标题\n- 文本内容",
    "active": {
        "group": 1919810,
        "content": "发送消息内容",
        "private": [
            {
                "userID": 54321,
                "content": "私信主动消息"
            }
        ]
    }
}
```

---

## (7) ForwardMessage
- 使用json生成包含多条文字、图片消息的转发消息，高度自定义输出。
### 使用帮助
- 需要程序输出json格式结果，输出格式应与`JsonForwardMessage`中的格式对应
- `JsonForwardMessage`中包含下方几个参数：
    + `title`(String)——转发消息卡片的标题，点开后也会显示在页面最上方 *（默认为“运行结果”）*
    + `brief`(String)——转发消息显示在消息列表中的预览，仅在消息列表中可见 *（默认为“[输出内容]”）*
    + `preview`(List[String])——转发消息卡片的预览，可添加多条（但最多只能显示前3条），展示在标题下方。**注意此项参数为列表，使用时需要带[ ]** *（默认为“["无预览"]”）*
    + `summary`(String)——展示在转发消息卡片的底部 *（默认为“查看转发消息”）*
    + `name`(String)——转发消息内部bot显示的昵称，现在似乎如果有bot好友就依然显示好友名字了。此项感觉用处不大，可忽略 *（默认为“输出内容”）*
    + `messages`(List[`JsonMessage`])——转发消息内包含的所有消息，**注意此项为json对象组成的列表，内部项目为【json输出格式(详见上一张图片)】内提到的JsonMessage对象** *（默认为一个空的`JsonMessage`对象）*
- 上方的全部参数均不是必填项，如没有填写则按照默认值生成
- 在ForwardMessage的消息对象中，也可以使用MessageChain格式构造文字和图片组成的消息链，MessageChain的实现请查看上一张图片
### markdown图片时限
- 单次执行中，markdown转图片进程总时间为60秒。
- 如果包含多张图片（同时包括其中MessageChain复合消息中的一张或多张图片），**每个图片的实际执行时间都会被累计**。如果在生成某一张图片时已经达到60秒总上限，**后续图片都将无法生成**
### 样例
- 关于JsonForwardMessage的输出样例：[https://pastebin.ubuntu.com/p/gxykmRrsF8/](https://pastebin.ubuntu.com/p/gxykmRrsF8/)

---

## (8) Audio (TTS)
- 将自定义文本通过调用在线API转换为语音，可调音库、语速、音调、音量
### 使用帮助
- 需要程序输出json格式结果，输出格式应与`AudioMessage`中的格式对应
- `AudioMessage`中包含下方几个参数：
    + `format`(String)——**正常无需修改！** 仅提供`text`格式输出帮助或调试文本 *（默认为“Audio”）*
    + `person`(Int)——音库：设置转换语音的音色。*（默认为“0”）* <br>目前仅支持：
        + `0`——度小美-成熟女声（基础音库）
        + `106`——度博文-情感男声（精品音库）
        + `4100`——度小雯-成熟女声（臻品音库）
        + `4106`——度博文-情感男声（臻品音库）
    + `speed`(Int)——语速：支持范围 0-15 *（默认为“5”）*
    + `pitch`(Int)——音调：支持范围 0-15 *（默认为“5”）*
    + `volume`(Int)——音量：支持范围 0-15 *（默认为“15”）*
    + `content`(String)——要播放的文本内容 *（此项必须填写）*
- 参数仅有 `content`(转换内容) 为必填项目，其余参数如没有填写则按照默认值生成
### 使用示例
- 下方为一个使用度博文常规语速、音调的测试音频消息
```json
{
    "person": 4106,
    "speed": 5,
    "pitch": 5,
    "volume": 10,
    "content": "这是要转换语音的文本"
}
```
- 也可以使用如下方式在 Audio 格式下输出文本
```json
{
    "format": "text",
    "content": "这是普通文本输出（用于展示使用帮助、调试等）"
}
```

---

## 三、数据存储功能
目前已成功支持数据存储功能，具体指令为：

> #pb set <名称> storage <开启/关闭>

## 数据存储storage
- 使用存储功能来保存自定义存档数据，在下次运行时读取数据来延续程序运行。可适用于存档类游戏、排行榜等功能。
- **存储功能仅当输出格式为json、ForwardMessage、Audio时生效，当为其他格式时，仅输出结果而不影响存储数据**
### 使用帮助
- 存储功能参数包含`storage`和`global`，**变量类型均为String（但可以为空值null）**
- `storage`为每个用户独立的存储，绑定用户的`userID`，不同用户间数据互不影响。**用户A执行时则返回用户A的存档，而无法获取用户B的存档**
- `global`为全局存储，每个用户执行时都能获取此参数，修改后会影响全部用户。**用户A执行时修改了全局存储，用户B再执行时获取到的为修改后的值**
- **注意：ForwardMessage使用存储功能时，必须在`JsonForwardMessage`中添加存储参数，如果添加在`messages`内的`JsonMessage`对象中，存储的数据不能读取**
- 此外，此功能还可以获取到用户的ID和昵称，方便记录全局数据
    + `userID`(Long)——为用户的账号ID，是读取存储数据的唯一凭证
    + `nickname`(String)——为用户的昵称，如果在群聊执行且用户有群昵称则优先获取群昵称，**不同用户的群昵称可能存在相同**
    + `from`(String)——为用户执行代码的环境。在群聊执行为当前的群聊名称和群号（具体格式为"群名称(群号)"），在私信执行为"private"
- **注意：当关闭存储功能或删除项目时，存储的数据将被清空**
- 补充：「`#pb storage <名称> [userID]`」用来查询当前存储内容，方便程序调试
### 程序输入示例
- 开启存储功能后，程序在输入时会先读取本地存储，**并输入一行包含存储数据的json字符串，而用户的输入被移至第二行**，变更后的输入如下方所示：
```text
{"storage":"用户存储数据","global":"全局存储数据","userID":114514,"nickname":"用户昵称","from":"测试群(1919810)"}
[用户输入的第一行]
[用户输入的第二行]
......
```
- 程序需要对第一行json字符串进行解析，解析方法取决于编程使用的语言，请自行百度搜索（python的一个示例可在下方查看）
### 程序输出示例
- 如需更新存储数据，输出时应加上`storage`和`global`参数。如果输出时不添加存储参数，则不会影响当前存储数据
- **请务必注意：存储功能的逻辑为覆盖保存，如果程序在某次输出时将存储参数设置为空字符串，空字符串会覆盖原数据，原数据则会永久丢失！！！（如果不设置存储参数则不影响存储数据）**
- 下方为几个不同类型的输出示例：
- 带存储的text格式输出
```json
{
    "content": "这是一段文本",
    "storage": "新用户存储数据",
    "global": "新全局存储数据"
}
```
- 带存储的markdown格式输出
```json
{
    "format": "markdown",
    "width": 800,
    "content": "### markdown标题\n- 文本内容",
    "storage": "新用户存储数据",
    "global": "新全局存储数据"
}
```
### 下方可查看[存储功能+json格式输出]的代码示例
- 关于存储功能的python代码示例：[https://pastebin.ubuntu.com/p/8dBPp9KJkj/](https://pastebin.ubuntu.com/p/8dBPp9KJkj/)
- c++在使用存储功能时需要导入\"json.h\"（此文件会在配置辅助文件为“json.h”后一起上传至glot），内部包含json解析和编码功能，具体使用方法和代码示例请查看：[https://pastebin.ubuntu.com/p/qXMJcBdGFt/](https://pastebin.ubuntu.com/p/qXMJcBdGFt/)（内含源代码链接）

## 图片输入
- 用户在执行代码时可以附带图片，下方将介绍程序如何获取用户输入的图片
- **使用本功能需要开启存储功能，即使不使用数据存储也仍需要开启**
- 上文中提到，开启存储数据后程序会先输入一行包含存储数据的json字符串，图片信息将包含在此字符串中
- 当用户输入图片时，将按照**图片的输入顺序**生成包含全部输入图片信息的数组，其中包含图片的链接、base64编码字符串
### 获取图片信息
- 全部图片信息位于字段`images`数组中，单个图片元素包含以下信息：
  + `url`(String)——为图片的下载链接，可直接用于markdown渲染
  + `base64`(String?)——为图片的base64编码字符串。**默认为null，因为图片下载需要时间，仅当开启base64配置时才会提供此项，具体见下方指令**
  + `error`(String)——错误信息：未发生错误为空字符串。当图片下载失败或转换base64发生错误时，错误信息会保存在此字段
### 启用输入图片base64
- 仅当项目启用“图片base64”功能时，才会下载图片并转换base64。启用指令为：
  ```
  #pb set <名称> base64 <开启/关闭>
  ```
- 启用后程序会先下载用户输入的图片转换base64，转换完成时才开始执行代码，下载和转换会花费额外的时间
- 传入的图片不宜过大，**所有图片下载的总时限为 60 秒**。如果超时，后续的图片仅能提供url，不再转换base64
### 样例
- 使用python获取图片输入的代码示例：[https://pastebin.ubuntu.com/p/SYyF2zCFxT/](https://pastebin.ubuntu.com/p/SYyF2zCFxT/)

---

## 写在最后
- 感谢您阅读本文档！
- 看完此文档如果仍有关于文档内容的疑问或在使用时遇到任何困难都可以找铁蛋(2295824927)咨询，有时候会看不到群消息，建议私信发送您遇到的问题。
- 铁蛋现在正在国外留学ing，大部分时间可能无法及时回复消息，还望您的理解
