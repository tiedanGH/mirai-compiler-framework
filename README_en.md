<div align="center">

# Mirai Compiler Framework

_Mirai Console online compiler framework based on Glot API_

[简体中文](README.md) | [English](README_en.md)

</div>

---

## Features
- **Comprehensive Multi-language Support**
    + The Glot API provides 40 common programming languages to meet various coding needs.
- **Convenient Projects Saving and Execution**
    + Save project URLs locally via commands; next time, simply enter the project name for instant running.
- **Support for Multiple Output Formats**
    + Convert Markdown/HTML to images, base64 to images, download images via links, convert LaTeX to images, and output message chains, multiple messages, or forwarded messages. This satisfies a variety of output requirements.
- **Intelligent Data Storage and Retrieval**
    + Customize data persistence so that subsequent runs can directly read stored data, allowing program logic to continue seamlessly. Also, able to retrieve information such as nickname, QQ number, and group chat details.
- **Proactive Group and Private Messages**
    + The program can proactively send messages to specified group chats or individuals, enabling real-time alerts and instant interaction.
- **Local Code High-speed Caching**
    + Automatically cache code on the first execution; subsequent runs do not require re-downloading, greatly improving load speed.

## Installation Instructions
1. This plugin runs on [Mirai Console](https://github.com/mamoe/mirai). You can learn how to install and start the bot by reading the [Mirai User Manual](https://docs.mirai.mamoe.net/UserManual.html).
2. After `MiraiConsole` has started successfully, place the `.jar` file from this project’s [releases](https://github.com/tiedanGH/mirai-compiler-framework/releases) into the `.\plugins\` directory to load the plugin.
3. Once the plugin has loaded successfully, copy all files from the [data](data) into `data\com.tiedan.mirai-compiler-framework\`, then restart `MiraiConsole`.
4. In `config\com.tiedan.mirai-compiler-framework\`, fill in the Glot [API Token](https://glot.io/account/token). In `mail.properties`, enter the SMTP email information (if needed, used to send stored data via email for review).
5. This framework provides over 200 pre-implemented projects located in [data\PastebinData.yml](data/PastebinData.yml). Place this file into the data folder to load existing projects.

## Documentation
- [Basic Framework Command Help](doc/commands_en.md)
    + [Directly Run Code](doc/commands_en.md#directly-run-code)
    + [Glot Commands](doc/commands_en.md#glot-commands)
    + [Pastebin Commands](doc/commands_en.md#pastebin-commands)
    + [Run Commands](doc/commands_en.md#run-commands)
- [Pastebin Commands and Advanced Features Documentation](doc/pastebin_en.md)
    + [1. Basic Command Functionality and Usage](doc/pastebin_en.md#1-basic-functions-and-usage)
    + [2. Output Formats: Output Images or Simultaneously Output Multiple Texts and Images](doc/pastebin_en.md#2-output-formats)
    + [3. Using Storage Functionality to Link Multiple Program Runs](doc/pastebin_en.md#3-using-the-storage-feature-to-link-multiple-program-runs)
- [Supported Code Upload Websites](doc/commands_en.md#supported-code-upload-sites)

---

## Feedback
- If you encounter any issues during installation or usage of the plugin, feel free to join QQ group 1059834024 for discussion and testing. You can also add me directly on QQ: Tiedan ([2295824927](https://qm.qq.com/q/hAIXBftS12)).
- You can also report any issues on the [issue](https://github.com/tiedanGH/mirai-compiler-framework/issues) page. If you have the skills and are willing to contribute, you're welcome to submit your improvements via a [Pull Request](https://github.com/tiedanGH/mirai-compiler-framework/pulls).

## Thanks
- Thanks to all developers who have contributed project code to this framework. As of June 6, 2025, this framework contains **217** independent projects.
- Thanks to the original project [jie65535/**mirai-console-jcc-plugin**](https://github.com/jie65535/mirai-console-jcc-plugin/) for providing source code and inspiration.
- Thanks to Ubuntu Pastebin, pastebin.com, Gist, and a total of [7 websites/platforms](doc/commands_en.md#supported-code-upload-sites) for offering code paste services.
