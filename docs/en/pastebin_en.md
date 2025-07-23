# pb Command Help Documentation
- This document provides instructions for using the `#pb` commands, helping you quickly master the commands, use multiple output formats, and utilize the storage feature to enable multi-stage program execution.

## Table of Contents
- [1. Basic Functions and Command Usage](#1-basic-functions-and-usage)
- [2. Output Formats: Output Images or Multiple Text and Image Messages](#2-output-formats)
- [3. Using the Storage Feature to Link Multiple Program Runs](#3-using-the-storage-feature-to-link-multiple-program-runs)

---

## 1. Basic Functions and Usage
Before reading the content below, you need to first understand the basic functionalities of the `#pb` and `#run` commands. If you have already mastered these basic functions and can use them proficiently, you can jump directly to [Section 2](#2-output-formats).

## Basic Functions and Command Usage
### Basic Functions
- This command is built on the Glot online compiler API and executes code remotely by calling the Glot API.
    + You can view detailed project information with the command `#glot help`.
- This plugin supports the vast majority of programming languages.
    + Use the command `#glot list` to view all supported languages.
- The command `#run <name> [stdin]` is used to run code entries saved in the pb list. Note that each execution has a **15-second time limit** and the maximum output size cannot exceed **1,000,000KB**.
### Command Usage
- The command `#pb help` allows you to view help for all pb functions.
- The command `#pb list` allows you to view the complete list of pb data entries.
- The command `#pb info <name>` allows you to view detailed information and a run example for the specified entry.
### Adding New Code Links
- Before using the add command, you need to learn how to upload code to a pastebin website. The bot currently supports the following pastebin websites:
    + [https://pastebin.ubuntu.com/](https://pastebin.ubuntu.com/) (login required, supports caching)
    + [https://glot.io/snippets/](https://glot.io/snippets/) (updates require no changes to the URL, can debug directly)
    + [https://pastebin.com/](https://pastebin.com/) (use `raw` links; updates require no changes to the URL)
    + [https://gist.github.com/](https://gist.github.com/) (GitHub login required, supports editing and caching)
    + [https://www.toptal.com/developers/hastebin/](https://www.toptal.com/developers/hastebin/) (supports caching)
    + [https://bytebin.lucko.me/](https://bytebin.lucko.me/) (supports caching)
    + [https://pastes.dev/](https://pastes.dev/) (supports caching)
    + [https://p.ip.fi/](https://p.ip.fi/) (supports caching)
- Some sites require registering an account before use, which has been annotated above.
- pastebin.com allows anonymous users to create links (though some features are limited, so registration is still recommended). You can click `edit` to modify content without creating a new link; when using, append `/raw` to obtain the raw URL, for example: `https://pastebin.com/raw/114514`.
- GitHub Gist supports content editing, but after modifications you must click the `raw` button to obtain the new link and replace the old one.
- After uploading your code, you will receive a link (or you can copy the link from your browser’s address bar). Use the command:
  ```
  #pb add <name> <author> <language> <source code URL> [example input (stdin)]
  ```
  to fill in the `source code URL` and add a new pb entry.
- If an “invalid link” error appears, it may be because the URL does not start with “https://” or does not end with “/”.
### Modifying Parameters
- Use the command:
  ```
  #pb set <name> <parameter name> <value>
  ```
  to modify entry parameters.
    + For example: `#pb set test name test2` will change the entry named “test” to “test2”.
- **Link Hiding:** When `hide` is enabled, the source code link will be hidden when viewing entry info (useful for programs with encryption).
- **Group Only:** When `groupOnly` is enabled, the entry can only be executed in group chats.

## Util File Help
- Util files provide commonly used functions for programs. When in use, configure them with the set command.
- During execution, util files will be uploaded together with the code file to Glot.
### Usage
- Configure a util file with the following command. Currently, each program supports adding only one helper file:
  ```
  #pb set <name> util <filename>
  ```
- If you have developed other useful util files, feel free to contact Tiedan to have them added.
### Currently Supported Util Files
- `htmlTools.py`
    + An HTML table generation tool; you can create `Table` objects, configure table properties, style and content cells (including cell merging and background color), and convert to HTML strings via `to_string()`.
- `json.h`
    + A C++ helper for storage functionality; used to parse JSON strings into C++ `std::map` objects, and re-encode `std::map` into JSON strings for JSON-format output.

---

## 2. Output Formats
This section explains how to convert program output into different formats. Before reading the details below, you need to execute the command to set the output format for the code entry you created in Section 1:

> #pb set <name> format <output format> [default width]

Supported output formats include:
- [text](#1-text) (plain text)
- [markdown](#2-markdown--html) (Markdown/HTML to image)
- [base64](#3-base64) (Custom Base64 Format Output)
- [image](#4-image) (send image via link or path)
- [LaTeX](#5-latex) (LaTeX to image)
- [json](#6-json-output) (custom format and image width)
- [ForwardMessage](#7-forwardmessage) (generate a forwarded message containing multiple text/image messages using JSON)
- [Audio](#8-audio-tts)（Generate a text-to-speech message using JSON）

Detailed usage is provided below.

---

### (1) text
- Plain, unadorned text in the original style.
#### Usage
- This format requires no special setup; plain text is the default output.
- When the text message is too long (over 550 characters), it will be wrapped into a forwarded message to prevent spam. Forwarded messages also have limits: one execution can output up to 5000 Chinese characters (15000 English characters). Excess content will be truncated.

---

### (2) markdown & html
- Renders Markdown or HTML documents as images.
#### Usage
- Output must be in Markdown or HTML format.
- Due to server performance, the markdown-to-image process has a 60-second limit. Extremely complex or large images may fail to generate.
#### Image Invocation
- You can upload images to the server cache for easy use with:
    + `#pb upload <image name with extension> <image/URL>`
    + Use standard markdown image syntax: `![](web URL or local path)`
- Contact Tiedan if you have any difficulties in uploading images.
- Uploaded images can be referenced with `image​://` as the path.
- You can also reference LGTBot’s image assets via `lgtbot​://`, which maps to LGTBot’s `game` folder. For paths, see [https://github.com/Slontia/lgtbot](https://github.com/Slontia/lgtbot).
#### Example
- In Python, you can `import htmlTools` to use the HTML table helper (configured via `htmlTools.py`). See usage examples at: [https://pastebin.ubuntu.com/p/mfBGHbyS6T/](https://pastebin.ubuntu.com/p/mfBGHbyS6T/) (includes source link).

---

## (3) Base64
- Automatically converts Base64 strings into multiple output formats based on the file type.
- Currently, supports conversion of text, images, audio, and video.

### Usage Guide
- The program requires to be output in Base64 format, including the file type prefix, for example:
    + `data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAABhkAAAPGC...`
    + `data:video/mp4;base64,AAAAGGZ0eXBtcDQyAAAAAG1wNDJpc29tA...`
- If the output is an image with high resolution, the Base64 string can be very long. In such cases, it's recommended to upload the image to cache via a command instead.
- In Markdown mode, you can also directly convert Base64 images using: `![](base64_string)`

---

### (4) image
- Directly sends an image from a URL or local path; supports GIF animations.
- Image sending is very fast but may be compressed. For original quality, use markdown output.
#### Usage
- Output must contain exactly one URL starting with `http` or a local file path starting with `file:///`, for example:
    + `https://i.imgs.ovh/2025/04/20/jXUdp.jpeg`
    + `file:///home/lighthouse/lgtbot-mirai/lgtbot/images/logo.svg`
    + `image://boss2.png`

---

### (5) LaTeX
- Converts LaTeX-formatted documents to images via an online API, primarily for formula generation.
#### Usage
- Output must be a LaTeX string, for example:
```latex
\begin{align}
    E_0 &= mc^2 \\
    E &= \frac{mc^2}{\sqrt{1-\frac{v^2}{c^2}}}
\end{align}
```
- **LaTeX output does not support the `width` parameter.**
- The current API used is QuickLaTeX ([https://quicklatex.com/](https://quicklatex.com/)).

---

### (6) json Output

## json
- Customizes output format, image width, and content, allowing switching between text and image.
#### Usage
- Program output must be in JSON format, matching the `JsonMessage` schema.
- Fields in `JsonMessage` include:
    + `format` (String): the output format; cannot use `json` or `ForwardMessage` here (default: `text`).
    + `at` (Boolean): whether to @ the command issuer; effective only for `text` and `MessageChain` (default: `true`).
    + `width` (Int): default image width; ignored in text output (default: `600`).
    + `content` (String): text or content for image generation; use Markdown syntax if needed (default: empty).
    + `messageList` (List[`JsonSingleMessage`]): used only in `MessageChain` or `MultipleMessage`; cannot coexist with `content`.
    + `error` (String): used to throw exceptions; if non-empty, the bot sends the `error` message and stops parsing other fields or saving data.
- All fields are optional and will use defaults if omitted.
#### JSON Output Examples
- Text format (omit `format` and `width`):
```json
{
    "content": "This is a text segment."
}
```
- Markdown format (default width = 600 if omitted):
```json
{
    "format": "markdown",
    "width": 300,
    "content": "### markdown title\n- some text"
}
```
- Base64 format:
```json
{
    "format": "base64",
    "content": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAABhkAAA..."
}
```

## MessageChain
- Constructs a message chain in a single message containing multiple text or image segments.
#### Usage
- Requires outputting a `JsonMessage` with `format`, `at`, and `messageList` fields; other fields are ignored.
    + `format` must be `MessageChain` (case-sensitive).
    + `at` (Boolean): whether to @ the command issuer.
    + `messageList`: a list of `JsonSingleMessage` objects in the order to be sent.
- Fields in `JsonSingleMessage` include:
    + `format` (String): one of `text`, `markdown`, `base64`, `image` (default: `text`).
    + `width` (Int): image width; ignored for text.
    + `content` (String): text or markdown content.
- All parameters above are not mandatory. If not filled in, they will be generated according to the default values
### Markdown Image Rendering Time Limit
- In a single execution, the total time for the markdown to image conversion process is 60 seconds.
- If multiple images are included, the actual execution time of each image will be accumulated. If the total limit of 60 seconds has been reached when generating a certain image, subsequent images will not be generated**
### Example of using MessageChain output
- The following shows a JSON code that utilizes storage functionality (as detailed in the third point of the table of contents) and contains multiple text and image messages
```json
{
    "format": "MessageChain",
    "at": false,
    "messageList": [
        {
            "content": "First text"
        },
        {
            "format": "markdown",
            "width": 300,
            "content": "### Image 1\n- Details"
        },
        {
            "content": "Second text"
        },
        {
            "format": "markdown",
            "width": 300,
            "content": "### Image 2\n- Details"
        },
        {
            "content": "Third text"
        },
        {
            "content": "Fourth text"
        }
    ],
    "storage": "userData",
    "global": "globalData"
}
```
The user will see:
```text
        First text
        [Image 1]
        Second text
        [Image 2]
        Third text
        Fourth text
```
- And both `storage` and `global` are saved.

## MultipleMessage
- Sends multiple independent messages in sequence with a 2-second interval (images may take longer).
### Usage Help
- This feature is similar to `MessageChain`. The program must output a `JsonMessage` object, but only the `format`, `at`, and `messageList` parameters are used. **All other parameters in `JsonMessage` will be ignored**.
  + The `format` field must be set to `"MultipleMessage"` — case-sensitive.
  + `at` (Boolean) — Specifies **whether to @ the command issuer at the start of each text message**.
  + `messageList` is a list of `JsonSingleMessage` objects, used to store all messages to be output. **The bot will send the messages one by one in the order they appear in the list.**
- The `JsonSingleMessage` object includes the following parameters:
  + `format` (String) — Output format. The format **cannot** be `json`, `ForwardMessage`, `MessageChain`, or `MultipleMessage`. *(Default: `text`)*
  + `width` (Int) — Default image width. Not effective when outputting text. *(Default: 600)*
  + `content` (String) — The content to output, used for sending text or images. *(Default: empty message)*
- All of the above parameters are optional. If omitted, the default values will be used.
### Output Limits and Markdown Time Limit
- **A maximum of 15 messages can be output per execution. If this limit is exceeded, the output will be interrupted.**
- During each execution, the total time for converting markdown to images is **limited to 60 seconds**.
- If multiple images are included, **each image’s rendering time will be accumulated**. If the time limit is reached during any image generation, **subsequent images will fail to render**.
### Example of Using `MultipleMessage` for Output
- Below is an example JSON configuration that uses the storage feature (see Section 3 in the main table of contents) and outputs multiple text and image messages.
```json
{
    "format": "MultipleMessage",
    "at": false,
    "messageList": [
        {
            "content": "First text"
        },
        {
            "format": "markdown",
            "width": 300,
            "content": "### image 1\n- 文本内容"
        },
        {
            "content": "Second text"
        },
        {
            "content": "Third text"
        },
        {
            "content": "Fourth text"
        }
    ],
    "storage": "userData",
    "global": "globalData"
}
```
- MultipleMessage will be sent in the order of the messages, and the result seen by the user after the code above is that each line is an independent message output:
```text
        First text

        [Image 1]

        Second text

        Third text

        Fourth text
```
- And both `storage` and `global` are saved.

## active Proactive Messages
- Supported only with JSON output.
- After each execution, you can send a proactive message to a specified group chat (bot must be in the group).
- For private proactive messages, the user must first execute `#pb private` to set an allowable time period. During this period, the bot can send up to 10 messages to different user IDs.
- Group and private proactive messages can be used simultaneously.
- **Use responsibly; logs are retained to prevent abuse.**
### Usage Instructions
- This feature requires the program to output a `JsonMessage` object and include an additional parameter `active` (the `format` can be any supported format).
- **Group proactive messages:** The `active` object must contain two parameters:
    + `group` (Long) — The target group ID to which the message will be sent.
    + `content` (String) — The content of the message to be sent. **The message will be prefixed as a notice.**
- **Private proactive messages:** The `active` object must include a `private` parameter:
    + `private` (List<SinglePrivateMessage>) — A list of private message objects. Each item must contain two parameters:
        + `userID` (Long) — The user ID of the message recipient.
        + `content` (String) — The content of the message. **The message will include a notice and the sender’s user ID.**
### Examples
- Below are JSON output examples that use both group and private proactive messaging:
- Text output with group proactive message, sent to group `114514`
```json
{
    "content": "This is a text message",
    "active": {
        "group": 114514,
        "content": "Message content"
    }
}
```
- Text output with private proactive messages sent to users `12345` and `11111`
```json
{
    "content": "This is a text message",
    "active": {
        "private": [
            {
                "userID": 12345,
                "content": "Private active message 1"
            },
            {
                "userID": 11111,
                "content": "Private active message 2"
            }
        ]
    }
}
```
- Markdown output with both group and private proactive messages, sent to group `1919810` and user `54321`
```json
{
    "format": "markdown",
    "width": 800,
    "content": "### Markdown Title\n- Text content",
    "active": {
        "group": 1919810,
        "content": "Message content",
        "private": [
            {
                "userID": 54321,
                "content": "Private active message"
            }
        ]
    }
}
```

---

## (7) ForwardMessage
- Use JSON to generate a forwarded message containing multiple text and image segments, allowing for highly customizable output.
### Usage Instructions
- The program must output data in JSON format, and the structure should match that of a `JsonForwardMessage`.
- A `JsonForwardMessage` includes the following parameters:
    + `title` (String) — The title of the forwarded message card. It will also appear at the top when expanded. *(Default: "Execution Result")*
    + `brief` (String) — A short preview shown in the message list. Visible only in the message list. *(Default: "[Output Content]")*
    + `preview` (List[String]) — Preview lines displayed below the title on the message card. You can include multiple lines (but only the first 3 will be shown). **Note: This must be a list, so use square brackets `[ ]`**. *(Default: `["No Preview"]`)*
    + `summary` (String) — Text shown at the bottom of the forwarded message card. *(Default: "View forwarded message")*
    + `name` (String) — The nickname displayed for the bot inside the forwarded message. If the bot is already a friend, this may still show the friend’s name. This option is not very useful and can generally be ignored. *(Default: "Output Content")*
    + `messages` (List[`JsonMessage`]) — The full list of message objects contained in the forwarded message. **Note: This must be a list of JSON objects following the same `JsonMessage` format described earlier.** *(Default: a single empty `JsonMessage` object)*
- All of the above parameters are optional. If omitted, default values will be used.
- Inside the `messages` field, you can also use the `MessageChain` format to combine multiple text and image items into a single message entry. See the previous section for details on `MessageChain` implementation.
### Markdown Image Rendering Time Limit
- The total time for rendering markdown to images is **60 seconds per execution**.
- If the forwarded message contains multiple images — including those nested within a `MessageChain` — **each image’s rendering time is accumulated**. Once the 60-second limit is reached during any image processing, **all subsequent images will fail to render**.
### Example
- An example output using `JsonForwardMessage`: [https://pastebin.ubuntu.com/p/gxykmRrsF8/](https://pastebin.ubuntu.com/p/gxykmRrsF8/)

---

## (8) Audio (TTS)
- Converts custom text into speech using an online API, with adjustable voice type, speed, pitch, and volume.
### Usage Instructions
- The program must output a result in JSON format, which should match the structure of `AudioMessage`.
- `AudioMessage` includes the following parameters:
    + `format` (String) — **Usually does not need to be changed!** Only use `text` format for displaying help or debugging output. *(Default: "Audio")*
    + `person` (Int) — Voice type: determines the voice used for conversion. *(Default: 0)* <br>Currently supported values:
        + `0` — Du Xiaomei - Mature Female (Basic Voice)
        + `106` — Du Bowen - Emotional Male (Premium Voice)
        + `4100` — Du Xiaowen - Mature Female (Ultra Voice)
        + `4106` — Du Bowen - Emotional Male (Ultra Voice)
    + `speed` (Int) — Speaking speed: range 0–15 *(Default: 5)*
    + `pitch` (Int) — Pitch: range 0–15 *(Default: 5)*
    + `volume` (Int) — Volume: range 0–15 *(Default: 15)*
    + `content` (String) — The text content to be spoken *(This field is required)*
- Only the `content` field is required. Other parameters will use default values if omitted.
### Examples
- Below is a test audio message using Du Bowen with default speed and pitch:
```json
{
    "person": 4106,
    "speed": 5,
    "pitch": 5,
    "volume": 10,
    "content": "This is the text to be converted into speech"
}
```
- You can also output plain text in `text` format like this:
```json
{
    "format": "text",
    "content": "This is plain text output (used for help display, debugging, etc.)"
}
```

---

## 3. Using the Storage Feature to Link Multiple Program Runs
You can enable data storage with:

> #pb set <name> storage on/off

## Data Storage (`storage`)
- The storage feature allows saving custom save data that can be retrieved in the next run to continue program execution. It is suitable for use cases such as save-based games, leaderboards, etc.
- **The storage feature only works when the output format is `json` or `ForwardMessage`. For other formats, output is displayed but data is not stored.**
### Usage Instructions
- The storage feature uses two parameters: `storage` and `global`. **Both are of type `String` (but may also be `null`).**
- `storage` refers to user-specific storage, bound to the user’s `userID`. **Each user has separate data. When user A runs the program, they only get their own data and cannot access user B's.**
- `global` is shared across all users. **If user A modifies global data, user B will see the updated value when they run the program.**
- **Note:** When using `ForwardMessage` with storage, the `storage` and `global` parameters **must** be included in the outer `JsonForwardMessage` object. If placed only inside the nested `JsonMessage`, the data will not be saved.
- Additionally, this feature can retrieve the user’s ID and nickname to help track global data:
    + `userID` (Long) — The user’s account ID, used as the key to fetch storage data.
    + `nickname` (String) — The user’s nickname. If executed in a group and the user has a group nickname, that is preferred. **Note: Different users may have the same nickname.**
    + `from` (String) — The environment where the command is run. In group chats, it's the group name and ID in the format: `"GroupName(GroupID)"`; in private chats, it will be `"private"`.
- **Warning:** When the storage feature is disabled or an entry is deleted, **all associated data will be cleared**.
- Additional command: `#pb storage <name> [userID]` — View current storage contents, useful for debugging.
### Program Input Example
- When the storage feature is enabled, the program will read local storage first, **and prepend a line of JSON data before the user’s actual input, which begins from the second line**.
```text
{"storage":"user storage data","global":"global storage data","userID":114514,"nickname":"user nickname","from":"Test Group(1919810)"}
[user input line 1]
[user input line 2]
...
```
- The program must parse the first JSON line. The parsing method depends on the language used. (For a Python example, see below.)
### Program Output Example
- To update storage data, the program’s output should include the `storage` and/or `global` parameters. If omitted, storage data remains unchanged.
- **Important:** Storage updates are **overwrite operations**. If your program outputs an empty string for the `storage` parameter, that empty string will **replace** the old data, which will then be **permanently lost**! (If the parameter is not included, the original data is preserved.)
- Below are examples of different output formats that include storage updates:
- Text format with storage:
```json
{
    "content": "This is a text message",
    "storage": "new user storage data",
    "global": "new global storage data"
}
```
- Markdown format with storage:
```json
{
    "format": "markdown",
    "width": 800,
    "content": "### Markdown Title\n- Text content",
    "storage": "new user storage data",
    "global": "new global storage data"
}
```
### Example Code Using [Storage + JSON Output]
- Python example using the storage feature: [https://pastebin.ubuntu.com/p/8dBPp9KJkj/](https://pastebin.ubuntu.com/p/8dBPp9KJkj/)
- In C++, using the storage feature requires importing `json.h`. (this helper file will be uploaded to Glot when configured). It includes functions for JSON parsing and encoding. See usage and code examples here: [https://pastebin.ubuntu.com/p/qXMJcBdGFt/](https://pastebin.ubuntu.com/p/qXMJcBdGFt/)

---

## Final Note
- Thank you for reading this documentation!
- If you still have questions after reading or encounter difficulties during usage, feel free to contact Tiedan (QQ: 2295824927). Since group messages may be missed, it is recommended to send a private message describing your issue.
- Tiedan is currently studying abroad and may not be able to respond promptly. Thank you for your understanding.
