# 框架基础指令帮助

## 主要内容
- [run直接运行代码](#run直接运行代码)
- [Glot指令](#Glot指令)
- [Pastebin指令](#Pastebin指令)
- [Run指令及快捷前缀](#run指令及快捷前缀)
- [Bucket跨项目存储库操作指令](#Bucket跨项目存储库操作指令)
- [Image本地图片操作指令](#Image本地图片操作指令)
- [支持上传代码的网站](#支持上传代码的网站)

---

## run直接运行代码
- `run <language> <code>`
- `run <language> <源代码URL> [stdin]`
- `引用消息: run <language> [stdin]`
### 参数说明
| 参数         | 说明    | 示例                                          | 备注                           |
|------------|-------|---------------------------------------------|------------------------------|
| `language` | 编程语言  | `python`                                    | 用`/glot list`列出所有支持的语言       |
| `code`     | 代码    | `print("Hello world")`                      | 要运行的代码，支持换行                  |
| `源代码Url`   | 源代码地址 | `https://pastebin.ubuntu.com/p/KhBB7ZjVbD/` | 需要在 [支持的网站](#支持上传代码的网站) 上传代码 | 
| `stdin`    | 标准输入  | `1 2 3 4 5`                                 | 可选：用于`scanf`之类               |

### 使用示例
#### 直接使用：
`run python print("Hello world")`

#### 从 [源代码URL](https://pastebin.ubuntu.com/) 运行代码：
`run c https://pastebin.ubuntu.com/p/KhBB7ZjVbD/`

#### 从 引用 执行代码：
> 引用: print("Hello world")

`run python`

### 支持带输入运行程序
#### 例1
`run c https://pastebin.ubuntu.com/p/S2PyvRqJNf/ 1 2 3 4 5`

#### 例2
> 引用: https://pastebin.ubuntu.com/p/S2PyvRqJNf/

`run c 1 2 3 4 5`

---

## Glot指令
查看框架信息和帮助

| 指令                    | 功能说明        |
|-----------------------|-------------|
| `/glot help`          | 查看框架信息和帮助   |
| `/glot list`          | 列出所有支持的编程语言 |
| `/glot template [语言]` | 获取指定语言的模板   |

---

## Pastebin指令
查看和添加pastebin代码、个人信息、查看统计、高级功能配置

### 📋 Pastebin 查看运行帮助
| 指令                 | 功能说明        |
|--------------------|-------------|
| `/pb support`      | 支持粘贴代码的网站   |
| `/pb profile [ID]` | 查看个人信息      |
| `/pb private`      | 允许私信主动消息    |
| `/pb stats [名称]`   | 查看统计信息      |
| `/pb list [查询模式]`  | 查看项目列表      |
| `/pb info <名称>`    | 查看项目信息&运行示例 |
| `/pb thread`       | 查询运行和等待中的进程 |

### ✏️ Pastebin 更新数据帮助
| 指令                                              | 功能说明           |
|-------------------------------------------------|----------------|
| `/pb add <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]` | 添加 Pastebin 项目 |
| `/pb set <名称> <参数名> <内容>`                       | 修改项目属性         |
| `/pb delete <名称>`                               | 永久删除项目         |

### ⚙️ Pastebin 高级功能帮助
| 指令                                    | 功能说明                  |
|---------------------------------------|-----------------------|
| `/pb set <名称> format <输出格式> [宽度/存储]`  | 修改输出格式                |
| `/pb storage <名称> [查询ID/mail] [邮件地址]` | 查询存储数据                |
| `/pb export <名称>`                     | 将项目代码缓存导出为临时链接（过期时使用） |

> 👉使用图片输出、数据存储等高级功能帮助请查看 [pb指令和高级功能帮助文档](pastebin.md)

---

## Run指令及快捷前缀
运行 Pastebin 中的代码

| 指令                  | 功能说明         |
|---------------------|--------------|
| `/run <名称> [stdin]` | 运行保存的代码项目    |
| `##<名称> [stdin]`    | 使用快捷前缀运行代码项目 |

*快捷前缀可在 [PastebinConfig](../src/main/kotlin/site/tiedan/config/PastebinConfig.kt) 中进行配置

---

## Bucket跨项目存储库操作指令
查看和创建跨项目存储库、备份和回滚数据

### 🗄 跨项目存储库操作指令
| 指令                                            | 功能说明    |
|-----------------------------------------------|---------|
| `/bk list [文字/备份]`                            | 查看存储库列表 |
| `/bk info <ID/名称>`                            | 查看存储库信息 |
| `/bk storage <ID/名称> [密码] [备份ID/mail] [邮件地址]` | 查询存储库数据 |
| `/bk create <名称> <密码>`                        | 创建新存储库  |
| `/bk set <ID/名称> <参数名> <内容>`                  | 修改存储库属性 |

### 🔗 关联存储库与项目
| 指令                            | 功能说明      |
|-------------------------------|-----------|
| `/bk add <项目名称> <ID/名称> [密码]` | 将存储库添加至项目 |
| `/bk rm <项目名称> <ID/名称>`       | 将存储库从项目移除 |

### ⚠️ 危险区
| 指令                                 | 功能说明    |
|------------------------------------|---------|
| `/bk backup <ID/名称> <编号> [密码]`     | 备份存储库数据 |
| `/bk backup <ID/名称> del <编号> [密码]` | 删除指定备份  |
| `/bk rollback <ID/名称> <编号> [密码]`   | 从备份回滚数据 |
| `/bk delete <ID/名称>`               | 永久删除存储库 |

---

## Image本地图片操作指令
预览和管理本地图片

### 🖼️ 本地图片操作指令
| 指令                              | 功能说明     |
|---------------------------------|----------|
| `/img list [查询模式]`              | 查看图片列表   |
| `/img info <名称>`                | 查看图片信息   |
| `/img upload <图片名称> <【图片/URL】>` | 上传图片至服务器 |

### ✏️ 更新图片帮助
| 指令                         | 功能说明   |
|----------------------------|--------|
| `/img set <名称> <参数名> <内容>` | 修改图片属性 |
| `/img delete <名称>`         | 删除图片   |

---

# 支持上传代码的网站
- [https://pastebin.ubuntu.com/](https://pastebin.ubuntu.com/) （需要登录，支持缓存）
- [https://glot.io/snippets/](https://glot.io/snippets/) （更新无需修改链接，可直接调试）
- [https://pastebin.com/](https://pastebin.com/) （需要`raw`，更新无需修改链接）
- [https://gist.github.com/](https://gist.github.com/) （需登录GitHub，支持修改+缓存）
- [https://www.toptal.com/developers/hastebin/](https://www.toptal.com/developers/hastebin/)（支持缓存）
- [https://bytebin.lucko.me/](https://bytebin.lucko.me/)（支持缓存）
- [https://pastes.dev/](https://pastes.dev/)（支持缓存）
- [https://p.ip.fi/](https://p.ip.fi/) （支持缓存）
