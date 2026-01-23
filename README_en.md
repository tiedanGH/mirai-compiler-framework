<div align="center">

# Mirai Compiler Framework

_Mirai Console online compiler framework based on Glot API_

[![release](https://img.shields.io/github/v/release/tiedanGH/mirai-compiler-framework)](https://github.com/tiedanGH/mirai-compiler-framework/releases)
[![stars](https://img.shields.io/github/stars/tiedanGH/mirai-compiler-framework?style=flat&color=orange)](https://github.com/tiedanGH/mirai-compiler-framework)
[![downloads](https://shields.io/github/downloads/tiedanGH/mirai-compiler-framework/total)](https://github.com/tiedanGH/mirai-compiler-framework/releases/latest)
[![Build Snapshot](https://github.com/tiedanGH/mirai-compiler-framework/actions/workflows/build.yml/badge.svg)](https://github.com/tiedanGH/mirai-compiler-framework/actions/workflows/build.yml)

[简体中文](README.md) | [English](README_en.md)

</div>

---

## Features
- **Comprehensive Multi-language Support**
    + The Glot API provides 40 common programming languages to meet various coding needs.
- **Convenient Projects Saving and Execution**
    + Save project URLs locally via commands; next time, simply enter the project name for instant running.
- **Support for Multiple Output Formats**
    + Convert Markdown/HTML to images, support custom Base64 formats (images, audio, video), text-to-speech, download images from links, and convert LaTeX to images. Also supports outputting message chains, multiple messages, or forwarded messages — meeting the needs for generating various effects.
- **Intelligent Data Storage and Retrieval**
    + Customize data persistence so that subsequent runs can directly read stored data, allowing program logic to continue seamlessly. Also, able to retrieve information such as nickname, QQ number, and group chat details.
- **Cross-Project Bucket Storage**
    + Supports creating independent Buckets, which can be flexibly mounted to different projects via commands, enabling data sharing and reuse across multiple projects
    + Built-in backup and rollback features ensure the security and traceability of cross-project data
- **Proactive Group and Private Messages**
    + The program can proactively send messages to specified group chats or individuals, enabling real-time alerts and instant interaction.
- **Local Code High-speed Caching**
    + Automatically cache code on the first execution; subsequent runs do not require re-downloading, greatly improving load speed.

## Installation Instructions
1. This plugin runs on [Mirai Console](https://github.com/mamoe/mirai). You can learn how to install and start the bot by reading the [Mirai User Manual](https://docs.mirai.mamoe.net/UserManual.html).
2. After `MiraiConsole` has started successfully, place the `.jar` file from this project’s [releases](https://github.com/tiedanGH/mirai-compiler-framework/releases) into the `./plugins/` directory to load the plugin.
3. Once the plugin has loaded successfully, copy all files from the [data](data) into `data/site.tiedan.mirai-compiler-framework/`, then restart `MiraiConsole`.
4. In `config/site.tiedan.mirai-compiler-framework/`, fill in the Glot [API Token](https://glot.io/account/token). In `mail.properties`, enter the SMTP email information (if needed, used to send stored data via email for review).
5. This framework provides over 200 pre-implemented projects located in [data\PastebinData.yml](data/PastebinData.yml). Place this file into the data folder to load existing projects.

## Documentation
- [Basic Framework Command Help](docs/en/commands_en.md)
    + [Directly Run Code](docs/en/commands_en.md#directly-run-code)
    + [Glot Commands](docs/en/commands_en.md#glot-commands)
    + [Pastebin Commands](docs/en/commands_en.md#pastebin-commands)
    + [Run Commands & Quick Prefix](docs/en/commands_en.md#run-commands--quick-prefix)
    + [Cross-Project Bucket Commands](docs/en/commands_en.md#cross-project-bucket-commands)
    + [Local Image Commands](docs/en/commands_en.md#local-image-commands)
- [Supported Code Upload Websites](docs/en/commands_en.md#supported-code-upload-sites)
- [Pastebin Commands and Advanced Features Documentation](docs/en/pastebin_en.md)
    + [1. Basic Command Functionality and Usage](docs/en/pastebin_en.md#1-basic-functions-and-usage)
    + [2. Output Formats: Output Images or Simultaneously Output Multiple Texts and Images](docs/en/pastebin_en.md#2-output-formats)
    + [3. Using Storage Functionality to Link Multiple Program Runs](docs/en/pastebin_en.md#3-using-the-storage-feature-to-link-multiple-program-runs)
- [Deploy Glot locally using docker-run](docs/en/docker-run_en.md)
    + [Install and Start docker-run](docs/en/docker-run_en.md#install-and-start-docker-run)
    + [Fill in the DockerConfig](docs/en/docker-run_en.md#fill-in-the-dockerconfig)

---

## Based on Third-Party Open Source Code
Parts of this project are based on **mirai-console-jcc-plugin** (link: [https://github.com/jie65535/mirai-console-jcc-plugin](https://github.com/jie65535/mirai-console-jcc-plugin))  
This project is released under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

In compliance with the AGPL-3.0 license, this project retains the original copyright notice and expresses gratitude for the contribution.  
For more information about this license, please refer to the [LICENSE](LICENSE) file in this project.

## Feedback
If you encounter any issues during the installation or usage of the plugin, feel free to provide feedback through the following channels:

- Join the user groups on QQ: [1059834024](https://qun.qq.com/universal-share/share?ac=1&authKey=VjAYH7vlGCeiHxqIk36ZjC%2BXV%2BlbKDesGcQudvYTEkpa5rbqlVMZvKdvVbc25Bbh&busi_data=eyJncm91cENvZGUiOiIxMDU5ODM0MDI0IiwidG9rZW4iOiJhY1R0R2d3UzA0OE9tYmNSQ2hUeCtTcEVzdUsyOTIzQkpxeHBGN2N4eEluR2Q5ckdkYm1haUNwbFRjSGVMZUJwIiwidWluIjoiMjI5NTgyNDkyNyJ9&data=e4kR51XjrBU5G05XS909DmQ1jOpUp_zo7zjVWnIC8pLfI8fKIt7Gni7XeVnX-IcV79QINpuZQtl2_ngo-7t9AQ&svctype=4&tempid=h5_group_info), [541402580](https://qun.qq.com/universal-share/share?ac=1&authKey=swt4AA6VEU48jridDNJHTqZMmU%2BHEA%2FhtOzlVi7qm7L1bqXVkIDTqchnfxuduFX4&busi_data=eyJncm91cENvZGUiOiI1NDE0MDI1ODAiLCJ0b2tlbiI6IjVpVmMzMmRrcFVrK2EzNmllNjRBMVpSZGE1R0l3MnZ2RTBxdXRpVDluQkRUNm1IV0Y1TVFQY2UrNnk1MTkxSFYiLCJ1aW4iOiIyMjk1ODI0OTI3In0%3D&data=uUdh-8OxIvt8rOO8E51c1HVkTqh896ogmIc8ZThzQfrO3NBajKMJE3tMmVOCInC6xKkZrv6_wfQ6hmySnPWJMw&svctype=4&tempid=h5_group_info)
- Contact the author directly on QQ: Tiedan ([2295824927](https://qm.qq.com/q/hAIXBftS12))
- Submit an issue on the [Issues](https://github.com/tiedanGH/mirai-compiler-framework/issues) page
- If you have development skills, feel free to contribute via [Pull Request](https://github.com/tiedanGH/mirai-compiler-framework/pulls)

## Acknowledgments
Thanks to all developers who have contributed code or inspiration to this framework!  
As of June 6, 2025, this framework has integrated **217** independent projects.

Special thanks to:
- The original project [jie65535/**mirai-console-jcc-plugin**](https://github.com/jie65535/mirai-console-jcc-plugin/)
- The online compiler API provided by Glot and its [docker-run](https://github.com/glotcode/docker-run) deployment plan
- Platforms providing code paste services, including Ubuntu Pastebin, pastebin.com, Gist, and a total of [7 supported sites](docs/commands.md#支持上传代码的网站)
