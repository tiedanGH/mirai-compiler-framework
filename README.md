<div align="center">

# Mirai Compiler Framework

_基于 Glot 接口的 Mirai Console 在线编译器框架_

</div>

---

## 特色功能
- 全方位多语言支持
    + Glot 接口提供了 47 种常见编程语言，应对各种编程需求
- 项目一键保存与执行
    + 通过指令将项目URL保存至本地，下次只需输入项目名称即可极速启动
- 支持使用多种格式输出
    + markdown/HTML转图片、base64转图片、链接下载图片、LaTeX转图片，还能输出消息链、多条消息或转发消息。满足生成各种效果的需求
- 智能数据存储与调用
    + 自定义数据持久化保存，后续运行可直接读取，轻松延续程序逻辑。同时可获取昵称、QQ号、群聊等信息
- 群聊和私信主动消息
    + 程序可主动向指定群聊或个人发送消息，实现实时提醒与即时互动
- 本地代码高速缓存
    + 首次执行自动缓存代码，后续运行无需重复下载，加载速度大幅提升

## 安装方法
1. 本插件基于 [Mirai Console](https://github.com/mamoe/mirai-console) 运行，您可以通过阅读 [Mirai用户手册](https://docs.mirai.mamoe.net/UserManual.html) 来了解如何安装、启动机器人。
2. `MiraiConsole` 成功启动后，将本项目 [releases](https://github.com/tiedanGH/mirai-compiler-framework/releases) 中的`.jar`文件放入`.\plugins\`目录下即可加载插件。
3. 插件加载成功后，将 [data文件夹](data) 下的所有文件放入 `data\com.tiedan.mirai-compiler-framework\` 中，然后重新启动 `MiraiConsole`
4. 本框架提供了超过200个已经实现好的项目，位于 [data\PastebinData.yml](data/PastebinData.yml)，放入数据文件夹来加载已有项目

## 帮助文档
- [框架基础指令帮助](doc/commands.md)
    + [run直接运行代码](doc/commands.md#run直接运行代码)
    + [Glot指令](doc/commands.md#Glot指令)
    + [Pastebin指令](doc/commands.md#Pastebin指令)
    + [Run指令](doc/commands.md#Run指令)
- [pb指令和高级功能帮助文档](doc/pastebin.md)
    + [1. 指令基本功能和使用帮助](doc/pastebin.md#一基本功能和指令使用)
    + [2. 输出格式：输出图片或同时输出多条文字和图片](doc/pastebin.md#二输出格式)
    + [3. 使用存储功能关联程序多次运行](doc/pastebin.md#三数据存储功能)
- [支持上传代码的网站](doc/commands.md#支持上传代码的网站)

---

## 反馈
- 如安装或使用插件过程中遇到任何问题，欢迎添加QQ群 1059834024 讨论和测试，您也可以直接添加QQ联系我：铁蛋（[2295824927](https://qm.qq.com/q/hAIXBftS12)）
- 或者您可以提交一个[issue](https://github.com/tiedanGH/mirai-compiler-framework/issues)。如果您有能力且愿意帮助我修复这些问题，欢迎提交[Pull request](https://github.com/tiedanGH/mirai-compiler-framework/pulls)

## 致谢
- 感谢对本框架提供项目代码的所有开发者。截止至 2025/06/06，本框架内已有 **218** 个独立的项目
- 感谢原项目 [jie65535/**mirai-console-jcc-plugin**](https://github.com/jie65535/mirai-console-jcc-plugin/) 提供的源代码和灵感
- 感谢 Ubuntu Pastebin、pastebin.com、Gist 等共[7个网站/平台](doc/commands.md#支持上传代码的网站)提供的粘贴代码服务。
