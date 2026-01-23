# Basic Framework Command Help

## Table of Contents
- [Directly Run Code](#directly-run-code)
- [Glot Commands](#glot-commands)
- [Pastebin Commands](#pastebin-commands)
- [Run Commands & Quick Prefix](#run-commands--quick-prefix)
- [Cross-Project Bucket Commands](#cross-project-bucket-commands)
- [Local Image Commands](#local-image-commands)
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

| Command                     | Description                              |
|-----------------------------|------------------------------------------|
| `/glot help`                | View framework info and help             |
| `/glot list`                | List all supported programming languages |
| `/glot template [language]` | Get the template for a specific language |

---

## Pastebin Commands
View and add pastebin code, profile info, statistics, and configure advanced features.

### 📋 View Pastebin Help
| Command                | Description                             |
|------------------------|-----------------------------------------|
| `/pb support`          | List websites currently supported by PB |
| `/pb profile [QQ]`     | View personal profile info              |
| `/pb private`          | Enable proactive private messaging      |
| `/pb stats [name]`     | View statistics                         |
| `/pb list [QueryMode]` | View full list                          |
| `/pb info <name>`      | View info & run sample                  |
| `/pb thread`           | Query running and pending processes     | 

### ✏️ Update Pastebin Data
| Command                                                        | Description                |
|----------------------------------------------------------------|----------------------------|
| `/pb add <name> <author> <language> <source code URL> [stdin]` | Add Pastebin project       |
| `/pb set <name> <param> <value>`                               | Modify project attributes  |
| `/pb delete <name>`                                            | Permanently delete project |

### ⚙️ Pastebin Advanced Features
| Command                                                 | Description                                          |
|---------------------------------------------------------|------------------------------------------------------|
| `/pb set <name> format <output format> [width/storage]` | Modify output format                                 |
| `/pb storage <name> [query ID]`                         | Query stored data                                    |
| `/pb export <name>`                                     | Export code cache to a temp link (when link expires) |

> 👉 For help with image output, data storage, and other advanced features, please see [pb Commands & Advanced Features Help](pastebin_en.md)

---

## Run Commands & Quick Prefix
Run code saved in Pastebin

| Command               | Description                    |
|-----------------------|--------------------------------|
| `/run <name> [stdin]` | Run project by name            |
| `##<name> [stdin]`    | Run project using Quick Prefix |

*The Quick Prefix can be configured in [PastebinConfig](../../src/main/kotlin/site/tiedan/config/PastebinConfig.kt)

---

## Cross-Project Bucket Commands
Manage and operate cross-project Buckets.

### 🗄 Bucket Management Commands
| Command                                       | Description              |
|-----------------------------------------------|--------------------------|
| `/bk list [text/backup]`                      | View Bucket list         |
| `/bk info <ID/name>`                          | View Bucket info         |
| `/bk storage <ID/name> [password] [backupID]` | Query Bucket stored data |
| `/bk create <name> <password>`                | Create new Bucket        |
| `/bk set <ID/name> <param> <value>`           | Modify Bucket attributes |

### 🔗 Project-Bucket Linking Commands
| Command                                  | Description                |
|------------------------------------------|----------------------------|
| `/bk add <project> <ID/name> [password]` | Add Bucket to project      |
| `/bk rm <project> <ID/name>`             | Remove Bucket from project |

### ⚠️ Danger Zone
| Command                                          | Description                |
|--------------------------------------------------|----------------------------|
| `/bk backup <ID/name> <backupID> [password]`     | Backup Bucket storage data |
| `/bk backup <ID/name> del <backupID> [password]` | Delete backup data         |
| `/bk rollback <ID/name> <backupID> [password]`   | Rollback to backup data    |
| `/bk delete <ID/name>`                           | Permanently delete Bucket  |

---

## Local Image Commands
Manage and operate local images.

### 🖼️ Image Management Commands
| Command                            | Description            |
|------------------------------------|------------------------|
| `/img list [QueryMode]`            | View image list        |
| `/img info <name>`                 | View image info        |
| `/img upload <name> <【image/URL】>` | Upload image to server |

### ✏️ Update Image Data
| Command                           | Description             |
|-----------------------------------|-------------------------|
| `/img set <name> <param> <value>` | Modify image attributes |
| `/img delete <name>`              | Delete image            |

---

# Supported Code Upload Sites
- [https://pastebin.ubuntu.com/](https://pastebin.ubuntu.com/) (Login required, supports caching)
- [https://glot.io/snippets/](https://glot.io/snippets/) （No link change on update, supports direct debugging)
- [https://pastebin.com/](https://pastebin.com/) (Needs `raw`, no link change on update)
- [https://gist.github.com/](https://gist.github.com/) (GitHub login required, supports editing and caching)
- [https://www.toptal.com/developers/hastebin/](https://www.toptal.com/developers/hastebin/) (Supports caching)
- [https://bytebin.lucko.me/](https://bytebin.lucko.me/) (Supports caching)
- [https://pastes.dev/](https://pastes.dev/) (Supports caching)
- [https://p.ip.fi/](https://p.ip.fi/) (Supports caching)
