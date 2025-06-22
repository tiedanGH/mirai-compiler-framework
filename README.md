<div align="center">

# Mirai Compiler Framework

_基于 Glot 接口的 Mirai Console 在线编译器框架_

[![release](https://img.shields.io/github/v/release/tiedanGH/mirai-compiler-framework)](https://github.com/tiedanGH/mirai-compiler-framework/releases)
[![Build Snapshot](https://github.com/tiedanGH/mirai-compiler-framework/actions/workflows/build.yml/badge.svg)](https://github.com/tiedanGH/mirai-compiler-framework/actions/workflows/build.yml)

[//]: # ([![stars]&#40;https://img.shields.io/github/stars/tiedanGH/mirai-compiler-framework?style=flat&color=orange&#41;]&#40;https://github.com/tiedanGH/mirai-compiler-framework&#41;)
[//]: # ([![downloads]&#40;https://shields.io/github/downloads/tiedanGH/mirai-compiler-framework/total&#41;]&#40;https://github.com/tiedanGH/mirai-compiler-framework/releases/latest&#41;)

[简体中文](README.md) | [English](docs/README_en.md)

</div>

---

## 特色功能
- **全方位多语言支持**
    + Glot 接口提供了 40 种常见编程语言，应对各种编程需求
- **项目一键保存与执行**
    + 通过指令将项目URL保存至本地，下次只需输入项目名称即可极速启动
- **支持使用多种格式输出**
    + Markdown/HTML转图片、base64自定义格式（图片、语音、视频）、文本转语音、链接下载图片、LaTeX转图片，还能输出消息链、多条消息或转发消息。满足生成各种效果的需求
- **智能数据存储与调用**
    + 自定义数据持久化保存，后续运行可直接读取，轻松延续程序逻辑。同时可获取昵称、QQ号、群聊等信息
- **群聊和私信主动消息**
    + 程序可主动向指定群聊或个人发送消息，实现实时提醒与即时互动
- **本地代码高速缓存**
    + 首次执行自动缓存代码，后续运行无需重复下载，加载速度大幅提升

## 安装方法
1. 本插件基于 [Mirai Console](https://github.com/mamoe/mirai) 运行，您可以通过阅读 [Mirai用户手册](https://docs.mirai.mamoe.net/UserManual.html) 来了解如何安装、启动机器人。
2. `MiraiConsole` 成功启动后，将本项目 [releases](https://github.com/tiedanGH/mirai-compiler-framework/releases) 中的`.jar`文件放入`.\plugins\`目录下即可加载插件。
3. 插件加载成功后，将 [data文件夹](data) 下的所有文件放入 `data\com.tiedan.mirai-compiler-framework\` 中，然后重新启动 `MiraiConsole`
4. `config\com.tiedan.mirai-compiler-framework\` 中填写 Glot 的 [Api Token](https://glot.io/account/token)，`mail.properties` 中填写SMTP邮箱信息（如果需要，用于将存储数据发送邮件查看）
5. 本框架提供了超过200个已经实现好的项目，位于 [data\PastebinData.yml](data/PastebinData.yml)，放入数据文件夹来加载已有项目

## 帮助文档
- [框架基础指令帮助](docs/commands.md)
    + [run直接运行代码](docs/commands.md#run直接运行代码)
    + [Glot指令](docs/commands.md#Glot指令)
    + [Pastebin指令](docs/commands.md#Pastebin指令)
    + [Run指令](docs/commands.md#Run指令)
- [pb指令和高级功能帮助文档](docs/pastebin.md)
    + [1. 指令基本功能和使用帮助](docs/pastebin.md#一指令基本功能和使用帮助)
    + [2. 输出格式：输出图片或同时输出多条文字和图片](docs/pastebin.md#二输出格式)
    + [3. 使用存储功能关联程序多次运行](docs/pastebin.md#三数据存储功能)
- [支持上传代码的网站](docs/commands.md#支持上传代码的网站)

---

## 基于第三方开源代码
本项目部分代码基于 **mirai-console-jcc-plugin**（原始地址：[https://github.com/jie65535/mirai-console-jcc-plugin](https://github.com/jie65535/mirai-console-jcc-plugin)）  
该项目遵循 **GNU Affero General Public License v3.0（AGPL-3.0）** 发布。

根据 AGPL-3.0 协议要求，本项目保留原始项目的版权声明，并在此对其贡献表示感谢。  
如需了解该许可证的详细内容，请参阅本项目中的 [LICENSE](LICENSE) 文件。

## 反馈
如在安装或使用插件过程中遇到任何问题，欢迎通过以下方式反馈：

- 加入交流群反馈：QQ群 [1059834024](https://qun.qq.com/universal-share/share?ac=1&authKey=VjAYH7vlGCeiHxqIk36ZjC%2BXV%2BlbKDesGcQudvYTEkpa5rbqlVMZvKdvVbc25Bbh&busi_data=eyJncm91cENvZGUiOiIxMDU5ODM0MDI0IiwidG9rZW4iOiJhY1R0R2d3UzA0OE9tYmNSQ2hUeCtTcEVzdUsyOTIzQkpxeHBGN2N4eEluR2Q5ckdkYm1haUNwbFRjSGVMZUJwIiwidWluIjoiMjI5NTgyNDkyNyJ9&data=e4kR51XjrBU5G05XS909DmQ1jOpUp_zo7zjVWnIC8pLfI8fKIt7Gni7XeVnX-IcV79QINpuZQtl2_ngo-7t9AQ&svctype=4&tempid=h5_group_info)、[541402580](https://qun.qq.com/universal-share/share?ac=1&authKey=swt4AA6VEU48jridDNJHTqZMmU%2BHEA%2FhtOzlVi7qm7L1bqXVkIDTqchnfxuduFX4&busi_data=eyJncm91cENvZGUiOiI1NDE0MDI1ODAiLCJ0b2tlbiI6IjVpVmMzMmRrcFVrK2EzNmllNjRBMVpSZGE1R0l3MnZ2RTBxdXRpVDluQkRUNm1IV0Y1TVFQY2UrNnk1MTkxSFYiLCJ1aW4iOiIyMjk1ODI0OTI3In0%3D&data=uUdh-8OxIvt8rOO8E51c1HVkTqh896ogmIc8ZThzQfrO3NBajKMJE3tMmVOCInC6xKkZrv6_wfQ6hmySnPWJMw&svctype=4&tempid=h5_group_info)
- 直接联系作者 QQ：铁蛋（[2295824927](https://qm.qq.com/q/hAIXBftS12)）
- 在 [Issues](https://github.com/tiedanGH/mirai-compiler-framework/issues) 页面提交问题
- 如果你具备开发能力，欢迎通过 [Pull Request](https://github.com/tiedanGH/mirai-compiler-framework/pulls) 提交修改建议

## 致谢
感谢所有为本框架提供代码或灵感的开发者！  
截至 2025 年 6 月 6 日，本框架已整合 **217** 个独立的项目。

特别感谢：
- 原项目 [jie65535/**mirai-console-jcc-plugin**](https://github.com/jie65535/mirai-console-jcc-plugin/)
- 提供粘贴代码服务的平台，包括 Ubuntu Pastebin、pastebin.com、Gist 等共 [7 个网站](docs/commands.md#支持上传代码的网站)
