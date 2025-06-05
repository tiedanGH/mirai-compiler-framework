# Mirai Compiler Framework

> 基于Glot接口的mirai-console在线编译器框架

## 插件使用方法
本插件基于 [Mirai-Console](https://github.com/mamoe/mirai-console) 运行，您可以通过阅读 [Mirai用户手册](https://docs.mirai.mamoe.net/UserManual.html) 来了解如何安装、启动机器人。

`MiraiConsole`成功启动后，将本项目 [releases](https://github.com/tiedanGH/mirai-compiler-framework/releases) 中的`.jar`文件放入`.\plugins\`目录下即可加载插件。

插件加载成功后，将 [data文件夹](data) 中的所有文件放入 ``

## `run`命令原型
 - `run <language> <code>`
 - `run <language> <源代码Url> [stdin]`
 - `引用消息: run <language> [stdin]`
### 参数说明
| 参数         | 说明    | 示例                                          | 备注                           |
|------------|-------|---------------------------------------------|------------------------------|
| `language` | 编程语言  | `python`                                    | 用`/jcc list`列出所有支持的语言        |
| `code`     | 代码    | `print("Hello world")`                      | 要运行的代码，支持换行                  |
| `源代码Url`   | 源代码地址 | `https://pastebin.ubuntu.com/p/KhBB7ZjVbD/` | 需要在 [支持的网站](#支持上传代码的网站) 上传代码 | 
| `stdin`    | 标准输入  | `1 2 3 4 5`                                 | 可选 用于`scanf`之类               |

## 使用示例
### 直接使用
`run python print("Hello world")`

### 从 [pastebinUrl](https://pastebin.ubuntu.com/) 运行代码：
`run c https://pastebin.ubuntu.com/p/KhBB7ZjVbD/`

### 从 引用 执行代码：
> 引用: print("Hello world")

`run python`

### 支持运行程序带输入：
#### 例1
`run c https://pastebin.ubuntu.com/p/S2PyvRqJNf/ 1 2 3 4 5`

#### 例2
> 引用: https://pastebin.ubuntu.com/p/S2PyvRqJNf/

`run c 1 2 3 4 5`

## 其他指令
 - /jcc help    # 帮助
 - /jcc list    # 列出所有支持的编程语言
 - /jcc template <language>    # 获取指定语言的模板

## 支持上传代码的网站
> 👉[查看pb帮助文档和高级功能请点击这里](doc/README_pb.md)
---

## 反馈
如使用或安装插件过程中遇到非本插件功能问题，您首先应该在[Mirai论坛](https://mirai.mamoe.net/)中搜索解决方案，若未解决，可以在[本项目的主题贴](https://mirai.mamoe.net/topic/463/jcc-%E5%9F%BA%E4%BA%8Emirai-console%E7%9A%84%E5%9C%A8%E7%BA%BF%E7%BC%96%E8%AF%91%E6%8F%92%E4%BB%B6)中回帖提问。

如果是插件本身的问题或漏洞，您可以向我提交一个[issue](https://github.com/jie65535/mirai-console-jcc-plugin/issues)。若您有能力且愿意帮助我修复这些问题，请提交[Pull request](https://github.com/jie65535/mirai-console-jcc-plugin/pulls)。


## 致谢
感谢原项目 [jie65535/**mirai-console-jcc-plugin**](https://github.com/jie65535/mirai-console-jcc-plugin/) 提供的框架代码和灵感

感谢对本框架提供项目代码的所有开发者，截止至 2025/06/05，本框架内已有 **218** 个项目

