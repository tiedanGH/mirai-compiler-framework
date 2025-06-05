# 项目指令相关帮助

## `run`命令原型
- `run <language> <code>`
- `run <language> <源代码URL> [stdin]`
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