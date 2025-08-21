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
- Only supported in `json` output format.
- After each execution, custom messages can be sent to specified group chats or private messages. A maximum of 10 messages can be sent per execution, and they cannot contain duplicate group IDs or user IDs.
- Regarding private active messages: **Users must first run the command `#pb private` to set an available time period. The bot can only send active messages to the user during that period.**
- Note: Group and private active messages can be used simultaneously.
- **※ By using this feature, you agree to use it within reasonable limits. Do not use it for spam or harassment. All behavior records can be checked in the bot’s backend.**
### Usage Help
- This feature requires the program to output a `JsonMessage` object, with an additional `active` parameter (`format` can be any format).
- **[Active Message Structure]** `active` is an **array** of multiple active messages, with the following parameters:
    + `groupID` (Long) — The target group ID to send the message.
    + `userID` (Long) — The target user ID to send the message.
    + `message` (`JsonMessage` object) — The message object to be sent. Supports multiple formats; see the detailed description in the JSON output section above.
- Either `groupID` or `userID` must be filled (if both are filled, only `groupID` is valid). **Messages will be prefixed with `[Active Message]`.**
### Examples
- Below are JSON output examples for using group and private active messages:
- Text output with group proactive message, sent to group `114514`
```json
{
    "content": "This is a text message",
    "active": [
        {
            "groupID": 114514,
            "message": {
                "content": "Message content"
            }
        }
    ]
}
```
- Text format output with private active messages, sending an image and text message to users `12345` and `11111`:
```json
{
    "content": "This is a text message",
    "active": [
        {
            "userID": 12345,
            "message": {
                "format": "markdown",
                "content": "Private active message (markdown)"
            }
        },
        {
            "userID": 11111,
            "message": {
                "content": "Private active message"
            }
        }
    ]
}
```
- Markdown format output with both group and private active messages, sending an image and text message to group `1919810` and user `54321`:
```json
{
    "format": "markdown",
    "width": 800,
    "content": "### Markdown Title\n- Text content",
    "active": [
        {
            "groupID": 1919810,
            "message": {
                "format": "markdown",
                "content": "Group active message (markdown)"
            }
        },
        {
            "userID": 54321,
            "message": {
                "content": "Private active message"
            }
        }
    ]
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
- **The storage feature only works when the output format is `json`, `ForwardMessage`, `Audio`. For other formats, output is displayed but data is not stored.**
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

## Bucket Cross-Project Storage
- Buckets can be shared across multiple PB projects (even if the projects come from different authors), and a single project can be linked to multiple Buckets.
- During input, the program can access data from all linked Buckets, and after execution, update the corresponding Buckets. This can be used for features like global currency, user data, etc.
- **Buckets can only be linked if the project output format is `json`, `ForwardMessage`, or `Audio`, and storage is enabled.**
### Command Help
- Use `#bk help` to view all commands for cross-project Buckets.
- Steps to create and link:
    1. Use the `create` command to create a new Bucket.
    2. Use the `add` command to link your project to the Bucket.
    3. To unlink, use the `remove` command.
- Use 「`#bk storage <ID/Name> [Password]`」 to query Bucket contents, useful for program debugging.
### Usage Help
- The parameter for Bucket functionality is `bucket`, which is an **array** of `BucketData` (but can be `null`).
- Each `BucketData` in the array contains:
    + `id` (Long) — The Bucket ID. **Generated by the system when created, immutable, used for globally unique identification.**
    + `name` (String) — The Bucket name. **The name can be changed; do not use it for linking Buckets.**
    + `content` (String) — The current data content of the Bucket. **All projects linked to this Bucket have permission to modify its content.**
### Program Input Example
- When storage is enabled and Buckets are linked, the program will input a line containing storage data in JSON format, as shown below:
```text
{"storage":"","global":"","bucket":[{"id":1,"name":"Bucket1Name","content":"bucket1Content"}],......}
[First line of user input]
[Second line of user input]
......
```
### Program Output Example
- To update Bucket data, add bucket with correctly configured parameters in the output. If no storage parameter is added, the Bucket will remain unaffected.
- **If no Bucket ID is specified or the Bucket is not linked, Bucket data cannot be saved.**
- **Important: Storage works with overwrite logic. Each save will overwrite existing data. If the Bucket is shared across multiple projects or users, be cautious to avoid disrupting other projects.**
- Below are several output examples:
- Example 1: Text format output linked to Bucket ID 1
```json
{
    "content": "This is a piece of text",
    "bucket": [
        {
            "id": 1,
            "content": "Content saved in Bucket1"
        }
    ]
}
```
- Example 2: Markdown format output linked to Bucket IDs 2 and 4
```json
{
    "format": "markdown",
    "width": 600,
    "content": "### Markdown Title\n- Text content",
    "bucket": [
        {
            "id": 2,
            "content": "Content saved in Bucket2"
        },
        {
            "id": 4,
            "content": "Content saved in Bucket2"
        }
    ]
}
```

---

## Image Input
- Users can attach images when executing code. This section explains how to retrieve user-provided images in your program.
- **This feature requires storage to be enabled, even if you're not using data storage.**
- As mentioned earlier, when storage is enabled, the program will first receive a line containing a JSON string with stored data. Image information will be included in this string.
- When users input images, an array of all image information is generated **in the order the images were input**, including image URLs and base64-encoded strings.
### Retrieving Image Information
- All image data is located in the `images` array. Each image entry contains the following fields:
  + `url` (String) — A download link for the image, directly usable for markdown rendering.
  + `base64` (String?) — A base64-encoded string of the image. **Defaults to null**, as image downloading takes time. This field is only provided when base64 conversion is explicitly enabled (see instruction below).
  + `error` (String) — Error message: an empty string means no error. If downloading or base64 conversion fails, the error message will be stored here.
### Enabling Base64 for Input Images
- The image to base64 function is only populated when the "image base64" feature is enabled. Use the following command to enable it:
  ```
  #pb set <name> base64 on/off
  ```
- When enabled, the program will download and convert the input images to base64 before executing the code. This takes extra time for downloading and conversion.
- Input images should not be too large. **The total download timeout for all images is 60 seconds**. If this limit is exceeded, subsequent images will only provide the URL, without base64 conversion.
### Example
- Python code example to retrieve input image data: [https://pastebin.ubuntu.com/p/SYyF2zCFxT/](https://pastebin.ubuntu.com/p/SYyF2zCFxT/)

---

## Final Note
- Thank you for reading this documentation!
- If you still have questions after reading or encounter difficulties during usage, feel free to contact Tiedan (QQ: 2295824927). Since group messages may be missed, it is recommended to send a private message describing your issue.
- Tiedan is currently studying abroad and may not be able to respond promptly. Thank you for your understanding.
