# 框架基础指令帮助

## 主要内容
- [run直接运行代码](#run直接运行代码)
- [Glot指令](#Glot指令)
- [Pastebin指令](#Pastebin指令)
- [Run指令](#Run指令)
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
| `stdin`    | 标准输入  | `1 2 3 4 5`                                 | 可选 用于`scanf`之类               |

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

```text
- /glot help    查看框架信息和帮助
- /glot list    列出所有支持的编程语言
- /glot template [语言]    获取指定语言的模板
```

---

## Pastebin指令
查看和添加pastebin代码、个人信息、查看统计、高级功能配置

```text
📋 pastebin查看运行帮助：
/pb support　目前pb支持的网站
/pb profile [QQ]　查看个人信息
/pb private　允许私信主动消息
/pb stats [名称]　查看统计
/pb list [页码/作者]　查看完整列表
/pb info <名称>　查看信息&运行示例

✏️ pastebin更新数据帮助：
/pb add <名称> <作者> <语言> <源代码URL> [示例输入(stdin)]　添加pastebin数据
/pb set <名称> <参数名> <内容>　修改程序属性
/pb delete <名称>　删除一条数据

⚙️ pastebin高级功能帮助：
/pb set <名称> format <输出格式> [宽度/存储]　修改输出格式
/pb upload <图片名称(需要包含拓展名)> <【图片/URL】>　上传图片至缓存
/pb storage <名称> [查询ID]　查询存储数据
```

> 👉使用图片输出、数据存储等高级功能帮助请查看 [pb指令和高级功能帮助文档](pastebin.md)

---

## Run指令
运行pastebin中的代码

```text
/run <名称> [stdin]    运行保存的pastebin代码
```

# 支持上传代码的网站
- [https://pastebin.ubuntu.com/](https://pastebin.ubuntu.com/) （需要登录，支持缓存）
- [https://pastebin.com/](https://pastebin.com/) （需要`raw`，更新无需修改链接）
- [https://gist.github.com/](https://gist.github.com/) （需登录GitHub，支持修改+缓存）
- [https://www.toptal.com/developers/hastebin/](https://www.toptal.com/developers/hastebin/)（支持缓存）
- [https://bytebin.lucko.me/](https://bytebin.lucko.me/)（支持缓存）
- [https://pastes.dev/](https://pastes.dev/)（支持缓存）
- [https://p.ip.fi/](https://p.ip.fi/) （支持缓存）
