package site.tiedan.core

import site.tiedan.MiraiCompilerFramework.logger
import site.tiedan.config.PastebinConfig
import site.tiedan.data.GlotCache
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import site.tiedan.MiraiCompilerFramework
import site.tiedan.MiraiCompilerFramework.ERROR_MSG_MAX_LENGTH
import site.tiedan.MiraiCompilerFramework.trimToMaxLength
import site.tiedan.config.DockerConfig
import site.tiedan.utils.HttpUtil
import java.io.File

/**
 * # glot.io api 封装
 * [https://glot.io/] 是一个开源的在线运行代码的网站
 * 它提供了免费API供外界使用，API文档见 [https://github.com/glotcode/glot/blob/master/api_docs]
 * 本类是对该API文档的封装
 * 通过 [listLanguages] 获取支持在线运行的编程语言列表
 * 通过 [getSupport] 判断指定编程语言是否支持
 * 通过 [getTemplateFile] 来获取指定编程语言的模板文件（runCode需要）
 * 以上接口均有缓存，仅首次获取不同数据时会发起请求。因此，首次运行可能较慢。
 * 通过 [runCode] 运行代码
 * 若觉得原版 [runCode] 使用复杂，还可以使用另一个更简单的重载 [runCode]
 * @suppress 注意，若传入不支持的语言，或者格式不正确，将无法正确识别
 *
 * ## 接口版本说明
 * glot.io 已下线旧版公开 API，[URL_LIST_LANGUAGES] 与 `/api/run/<语言>/latest` 均返回 404，目前仅保留官网自身使用的 [URL_MUX] 接口。
 *
 * @author jie65535@github
 */
object GlotAPI {
    private const val URL = "https://glot.io/"
    private const val URL_NEW = "https://glot.io/new/"
    /** 新版官网内部接口地址 */
    private const val URL_MUX = URL + "api/mux"

    /** 旧版接口地址前缀，**已废弃**：glot.io 已下线该接口 */
    private const val URL_API = URL + "api/"
    /** 旧版语言列表接口，**已废弃**：改用 [GlotLanguages] */
    private const val URL_LIST_LANGUAGES = URL_API + "run"

    private val json = Json { ignoreUnknownKeys = true }
    private val muxJson = Json { encodeDefaults = true }

    @Serializable
    data class Language(val name: String, val url: String)
    @Serializable
    data class CodeFile(val name: String, val content: String)

    /**
     * 旧版运行请求体
     * 本地部署 docker-run 使用此格式
     */
    @Serializable
    data class RunCodeRequest(
        val language: String,
        val stdin: String? = null,
        val command: String? = null,
        val files: List<CodeFile>
    )
    @Serializable
    data class DockerRunRequest(
        val image: String,
        val payload: RunCodeRequest
    )
    @Serializable
    data class RunResult(
        val stdout: String = "",
        val stderr: String = "",
        val error: String = "",
        val message: String = "",
        val duration: Long? = null
    )

    /** 新版 MUX 接口数据结构 */
    @Serializable
    data class RunInstructions(
        val buildCommands: List<String>,
        val runCommand: String,
    )
    @Serializable
    data class MuxRequest(
        val action: String = "run",
        val data: MuxData,
    )
    @Serializable
    data class MuxData(
        val image: String,
        val payload: MuxPayload,
    )
    @Serializable
    data class MuxPayload(
        val runInstructions: RunInstructions,
        val files: List<CodeFile>,
        val stdin: String? = null,
    )
    @Serializable
    data class MuxResponse(
        val data: RunResult? = null,
        val error: MuxError? = null,
    )
    @Serializable
    data class MuxError(
        val code: String = "",
        val message: String = "",
        val requestId: String = "",
    )

    /**
     * 列出所有支持在线运行的语言
     *
     * 直接读取 [GlotLanguages] 中的配置，旧版实现见 [listLanguagesFromApi]
     */
    fun listLanguages(): List<Language> =
        GlotLanguages.names.map { Language(it, "$URL_NEW$it") }

    /**
     * 检查是否支持该语言在线编译
     * @param language 编程语言名字（忽略大小写）
     * @return 是否支持
     */
    fun checkSupport(language: String): Boolean = GlotLanguages.isSupported(language)

    /**
     * 获取编程语言请求地址，若不支持将会抛出异常
     * @param language 编程语言名字（忽略大小写）
     * @return 返回语言请求地址
     * @exception Exception 不支持的语言
     */
    private fun getSupport(language: String): Language =
        listLanguages().find { it.name.equals(language, true) } ?: throw Exception("不支持的语言 $language")

    /**
     * 新版 glot.io 页面内嵌的编辑器数据，[SSR_SELECTOR] 中为该结构的 JSON
     */
    @Serializable
    data class SsrData(val editor: SsrEditor)
    @Serializable
    data class SsrEditor(
        val title: String = "",
        val language: String = "",
        val files: List<CodeFile> = emptyList(),
        val stdin: String? = null,
    )

    /** 新版页面内嵌数据所在元素 */
    private const val SSR_SELECTOR = "#glot-ssr-data"

    /**
     * 获取 glot.io 页面中的文件列表
     *
     * @param url glot.io 页面地址，如 `https://glot.io/new/python`、`https://glot.io/snippets/<id>`
     * @return 页面中的全部文件，首个元素为主文件
     */
    fun getEditorFiles(url: String): List<CodeFile> {
        val document = HttpUtil.getDocument(url)
        val ssrData = HttpUtil.documentSelect(document, SSR_SELECTOR).firstOrNull()?.data()
            ?: throw Exception("无法获取 $url 的页面数据")
        return json.decodeFromString<SsrData>(ssrData).editor.files
            .ifEmpty { throw Exception("未获取到 $url 中的任何文件") }
    }

    /**
     * 获取指定编程语言的模板文件（缓存）
     */
    fun getTemplateFile(language: String): CodeFile {
        val lang = getSupport(language)
        if (GlotCache.templateFiles.containsKey(lang.name))
            return GlotCache.templateFiles[lang.name]!!
        val templateFile = getEditorFiles(URL_NEW + lang.name).first()
        GlotCache.templateFiles[lang.name] = templateFile
        return templateFile
    }

    /**
     * # 使用 glot.io 新版接口运行代码
     *
     * ## 请求示例
     * ```json
     * {
     *   "action": "run",
     *   "data": {
     *     "image": "glot/python:latest",
     *     "payload": {
     *       "runInstructions": { "buildCommands": [], "runCommand": "python main.py" },
     *       "files": [ { "name": "main.py", "content": "print(42)" } ],
     *       "stdin": null
     *     }
     *   }
     * }
     * ```
     * ## 成功响应
     * ```json
     * { "data": { "duration": 17305894, "stdout": "42", "stderr": "", "error": "" } }
     * ```
     * ## 失败响应（HTTP 400）
     * ```json
     * { "error": { "code": "decode_error", "message": "...", "requestId": "..." } }
     * ```
     * @param language 要运行的编程语言
     * @param files 上传的全部文件，首个元素为主文件
     * @param stdin 可选的输入缓冲区数据
     * @return 返回运行结果 若执行了死循环或其它阻塞代码，
     * 导致程序无法在限定时间内返回，将会报告超时异常
     */
    private fun runOnGlot(language: String, files: List<CodeFile>, stdin: String?): RunResult {
        val instructions = GlotLanguages.getRunInstructions(language, files.first().name)
        val request = MuxRequest(
            data = MuxData(
                image = GlotLanguages.getImage(language),
                payload = MuxPayload(
                    runInstructions = RunInstructions(instructions.buildCommands, instructions.runCommand),
                    files = files,
                    stdin = stdin,
                )
            )
        )
        val body = muxJson.encodeToString(request)
        val bodyString = HttpUtil.post(URL_MUX, body, muxHeaders(language)).readBody()
        logger.debug("Glot 响应：$bodyString")

        val response = json.decodeFromString<MuxResponse>(bodyString)
        // 接口层失败：转换为 message 交由上层统一提示
        return response.data
            ?: RunResult(message = response.error?.let { "[${it.code}] ${it.message}" } ?: "未获取到任何数据")
    }

    /**
     * 使用本地部署 docker-run 运行代码
     */
    private fun runOnDocker(language: String, files: List<CodeFile>, stdin: String?): RunResult {
        logger.debug("请求使用 glot docker-run 运行代码")
        val request = DockerRunRequest(
            image = GlotLanguages.getImage(language),
            payload = RunCodeRequest(language, stdin, useCommand(language), files)
        )
        val bodyString = HttpUtil.post(
            DockerConfig.requestUrl,
            json.encodeToString(request),
            mapOf("X-Access-Token" to DockerConfig.token)
        ).readBody()
        return json.decodeFromString<RunResult>(bodyString)
    }

    /**
     * 读取响应体，非成功状态抛出异常
     */
    private fun okhttp3.Response.readBody(): String {
        return use { res ->
            val bodyString = res.body.string()
            if (!res.isSuccessful && res.code != 400) {
                throw HttpUtil.HttpException(
                    code = res.code,
                    message = res.message,
                    url = res.request.url.toString(),
                    body = trimToMaxLength(bodyString, ERROR_MSG_MAX_LENGTH).first.replace("\n", "").ifEmpty { "无返回内容" }
                )
            }
            bodyString
        }
    }

    /**
     * [URL_MUX] 为官网自身接口，附带浏览器请求头以贴近正常访问
     */
    private fun muxHeaders(language: String): Map<String, String> = mapOf(
        "Origin" to "https://glot.io",
        "Referer" to "$URL_NEW$language",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36",
    )

    /**
     * # 编译指令
     * 在编译时根据语言选择合适的编译指令，满足使用需求
     * 仅用于 docker-run；glot.io 新版接口的构建与运行命令改由 [GlotLanguages] 提供
     */
    private fun useCommand(language: String): String? {
        val command = when (language) {
            "c"-> "clang -O2 main.c && ./a.out"
            "cpp"-> "clang++ -std=c++17 -O2 main.cpp && ./a.out"
            else-> null
        }
        if (command?.isNotEmpty() == true) logger.info("Run Command: $command")
        return command
    }

    private fun getFiles(language: String, code: String, file: String?): List<CodeFile> {
        val mainFile = CodeFile(getTemplateFile(language).name, code)
        return if (file != null) {
            @OptIn(ConsoleExperimentalApi::class)
            val utilFile = File("${MiraiCompilerFramework.utilsFolder}$file").readText()
            logger.info("Upload Extra File: $file")
            listOf(mainFile, CodeFile(file, utilFile))
        } else {
            listOf(mainFile)
        }
    }

    /**
     * # 运行代码
     * @param language 编程语言
     * @param code 程序代码
     * @param stdin 可选的输入缓冲区数据
     * @param file 可选的辅助文件名
     * @return 返回运行结果 若执行了死循环或其它阻塞代码，导致程序无法在限定时间内返回，将会报告超时异常
     */
    fun runCode(language: String, code: String, stdin: String? = null, file: String? = null): RunResult {
        val lang = getSupport(language).name
        val files = getFiles(lang, code, file)
        return if (lang in DockerConfig.supportedLanguages) {
            runOnDocker(lang, files, stdin)
        } else {
            runOnGlot(lang, files, stdin)
        }
    }


    /* ==================================================================== */
    /* ======================= 以下为旧版接口（已废弃） ====================== */
    /* ==================================================================== */

    /**
     * 列出所有支持在线运行的语言（缓存）
     * @return 返回支持的语言列表 示例：
     * ```json
     * [
     *   {
     *     "name": "assembly",
     *     "url": "https://glot.io/api/run/assembly"
     *   },
     *   {
     *     "name": "c",
     *     "url": "https://glot.io/api/run/c"
     *   }
     * ]
     * ```
     */
    @Suppress("unused")
    @Deprecated(
        message = "glot.io 已下线该接口，现返回 404。语言列表改由 GlotLanguages 提供",
        replaceWith = ReplaceWith("listLanguages()"),
        level = DeprecationLevel.WARNING,
    )
    fun listLanguagesFromApi(): List<Language> {
        if (GlotCache.languages.isEmpty()) {
            GlotCache.languages = Json.decodeFromString(HttpUtil.get(URL_LIST_LANGUAGES)) ?: throw Exception("未获取到任何数据")
        }
        return GlotCache.languages
    }

    /**
     * # 运行代码
     *
     * ## 简单示例：
     * 请求
     * ```json
     * {
     *   "files": [
     *     {
     *       "name": "main.py",
     *       "content": "print(42)"
     *     }
     *   ]
     * }
     * ```
     * 响应
     * ```json
     * {
     *   "stdout": "42\n",
     *   "stderr": "",
     *   "error": ""
     * }
     * ```
     *
     * ## 读输入流示例：
     * 请求
     * ```json
     * {
     *   "stdin": "42",
     *   "files": [
     *     {
     *       "name": "main.py",
     *       "content": "print(input('Number from stdin: '))"
     *     }
     *   ]
     * }
     * ```
     * 响应
     * ```json
     * {
     *   "stdout": "Number from stdin: 42\n",
     *   "stderr": "",
     *   "error": ""
     * }
     * ```
     *
     * ## 自定义运行命令示例：
     * 请求
     * ```json
     * {
     *   "command": "bash main.sh 42",
     *   "files": [
     *     {
     *       "name": "main.sh",
     *       "content": "echo Number from arg: $1"
     *     }
     *   ]
     * }
     * ```
     * 响应
     * ```json
     * {
     *   "stdout": "Number from arg: 42\n",
     *   "stderr": "",
     *   "error": ""
     * }
     * ```
     * @param language 要运行的编程语言
     * @param requestData 运行代码的请求数据
     * @return 返回运行结果 若执行了死循环或其它阻塞代码，
     * 导致程序无法在限定时间内返回，将会报告超时异常
     */
    @Suppress("unused")
    @Deprecated(
        message = "glot.io 已下线 /api/run/<语言>/latest 接口，且新版接口不再接受该请求体结构",
        level = DeprecationLevel.WARNING,
    )
    private fun runCode(language: Language, requestData: RunCodeRequest): RunResult {
        // docker-run 请求
        if (language.name in DockerConfig.supportedLanguages) {
            return runOnDocker(language.name, requestData.files, requestData.stdin)
        }
        // Glot API 请求
        val response = HttpUtil.post(
            language.url + "/latest",
            Json.encodeToString(requestData),
            mapOf("Authorization" to PastebinConfig.API_TOKEN)
        )

        var bodyString = ""
        response.use { res ->
            bodyString = res.body.string()
            if (!res.isSuccessful && res.code != 400) {
                // 400 会在返回内容中给出具体错误信息，交给上层处理
                throw HttpUtil.HttpException(
                    code = res.code,
                    message = res.message,
                    url = res.request.url.toString(),
                    body = trimToMaxLength(bodyString, ERROR_MSG_MAX_LENGTH).first.replace("\n", "").ifEmpty { "无返回内容" }
                )
            }
        }
        return Json.decodeFromString(bodyString) ?: throw Exception("未获取到任何数据")
    }
}
