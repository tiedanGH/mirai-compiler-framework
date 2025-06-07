# Basic Framework Command Help

## Table of Contents
- [Directly Run Code](#directly-run-code)
- [Glot Commands](#glot-commands)
- [Pastebin Commands](#pastebin-commands)
- [Run Commands](#run-commands)
- [Supported Code Upload Sites](#supported-code-upload-sites)

---

## Directly Run Code
- `run <language> <code>`
- `run <language> <source code URL> [stdin]`
- `Quoted Message: run <language> [stdin]`
### Parameter Description
| Parameter         | Description            | Example                                     | Notes                                                        |
|-------------------|------------------------|---------------------------------------------|--------------------------------------------------------------|
| `language`        | Programming language   | `python`                                    | Use `/glot list` to list all supported languages             |
| `code`            | Code to run            | `print("Hello world")`                      | Supports multiline code                                      |
| `source code URL` | URL of the source code | `https://pastebin.ubuntu.com/p/KhBB7ZjVbD/` | Uploaded on a [supported site](#supported-code-upload-sites) |
| `stdin`           | Standard input         | `1 2 3 4 5`                                 | Optional; for functions like `scanf`                         |

### Usage Examples
#### Direct usage:
`run python print("Hello world")`

#### Run from [source code URL](https://pastebin.ubuntu.com/):
`run c https://pastebin.ubuntu.com/p/KhBB7ZjVbD/`

#### Run from quoted message:
> Quoted: print("Hello world")

`run python`

### Run programs with input
#### Example 1:
`run c https://pastebin.ubuntu.com/p/S2PyvRqJNf/ 1 2 3 4 5`

#### Example 2:
> Quoted: https://pastebin.ubuntu.com/p/S2PyvRqJNf/

`run c 1 2 3 4 5`

---

## Glot Commands
Check framework information and help.

```text
/glot help                 View framework info and help
/glot list                 List all supported programming languages
/glot template [language] Get the template for a specific language
```

---

## Pastebin Commands
View and add pastebin code, profile info, statistics, and configure advanced features.

```text
📋 View Pastebin Help:
/pb support                List websites currently supported by pb
/pb profile [QQ]           View personal profile info
/pb private                Enable proactive private messaging
/pb stats [name]           View statistics
/pb list [page/author]     View full list
/pb info <name>            View info & run sample

✏️ Update Pastebin Data:
/pb add <name> <author> <language> <source code URL> [stdin]     Add pastebin entry
/pb set <name> <param> <value>                                   Modify program attributes
/pb delete <name>                                                Delete an entry

⚙️ Advanced Features:
/pb set <name> format <output format> [width/storage]            Modify output format
/pb upload <image_name.(ext)> <image/URL>                        Upload image to cache
/pb storage <name> [query ID]                                    Query stored data
```

> 👉 For help with image output, data storage, and other advanced features, please see [pb Commands & Advanced Features Help](pastebin_en.md)

---

## Run Commands
Run code saved in pastebin

```text
/run <name> [stdin]    Run pastebin code by name
```

# Supported Code Upload Sites
- [https://pastebin.ubuntu.com/](https://pastebin.ubuntu.com/) (Login required, supports caching)
- [https://pastebin.com/](https://pastebin.com/) (Needs `raw`, no link change on update)
- [https://gist.github.com/](https://gist.github.com/) (GitHub login required, supports editing and caching)
- [https://www.toptal.com/developers/hastebin/](https://www.toptal.com/developers/hastebin/) (Supports caching)
- [https://bytebin.lucko.me/](https://bytebin.lucko.me/) (Supports caching)
- [https://pastes.dev/](https://pastes.dev/) (Supports caching)
- [https://p.ip.fi/](https://p.ip.fi/) (Supports caching)
