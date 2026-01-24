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
 * @author jie65535@github
 */
object GlotAPI {
    private const val URL = "https://glot.io/"
    private const val URL_NEW = "https://glot.io/new/"
    private const val URL_API = URL + "api/"
    private const val URL_LIST_LANGUAGES = URL_API + "run"

    @Serializable
    data class Language(val name: String, val url: String)
    @Serializable
    data class CodeFile(val name: String, val content: String)

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
    fun listLanguages(): List<Language> {
        if (GlotCache.languages.isEmpty()) {
            GlotCache.languages = Json.decodeFromString(HttpUtil.get(URL_LIST_LANGUAGES)) ?: throw Exception("未获取到任何数据")
        }
        return GlotCache.languages
    }

    /**
     * 检查是否支持该语言在线编译
     * @param language 编程语言名字（忽略大小写）
     * @return 是否支持
     */
    fun checkSupport(language: String): Boolean = listLanguages().any { it.name.equals(language, true) }

    /**
     * 获取编程语言请求地址，若不支持将会抛出异常
     * @param language 编程语言名字（忽略大小写）
     * @return 返回语言请求地址
     * @exception Exception 不支持的语言
     */
    private fun getSupport(language: String): Language =
        listLanguages().find { it.name.equals(language, true) } ?: throw Exception("不支持的语言 $language")

    /**
     * 获取指定编程语言的模板文件（缓存）
     */
    fun getTemplateFile(language: String): CodeFile {
        val lang = getSupport(language)
        if (GlotCache.templateFiles.containsKey(lang.name))
            return GlotCache.templateFiles[lang.name]!!
        val document = HttpUtil.getDocument(URL_NEW + lang.name)
        val filename = HttpUtil.documentSelect(document, ".filename").firstOrNull()?.text() ?: throw Exception("无法获取文件名")
        val fileContent = HttpUtil.documentSelect(document, "#editor-1").firstOrNull()?.wholeText() ?: throw Exception("无法获取模板文件内容")
        val templateFile = CodeFile(filename, fileContent)
        GlotCache.templateFiles[lang.name] = templateFile
        return templateFile
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
    private fun runCode(language: Language, requestData: RunCodeRequest): RunResult {
        val response =
            if (language.name in DockerConfig.supportedLanguages) {
                // docker 请求
                logger.debug("请求使用 glot docker-run 运行代码")
                val dockerImage = when (language.name) {
                    "c", "cpp"-> "glot/clang:latest"
                    else-> "glot/${language.name}:latest"
                }
                HttpUtil.post(
                    DockerConfig.requestUrl,
                    Json.encodeToString(DockerRunRequest(dockerImage, requestData)),
                    mapOf("X-Access-Token" to DockerConfig.token)
                )
            } else {
                // Glot API 请求
                HttpUtil.post(
                    language.url + "/latest",
                    Json.encodeToString(requestData),
                    mapOf("Authorization" to PastebinConfig.API_TOKEN)
                )
            }

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

    /**
     * # 编译指令
     * 在编译时根据语言选择合适的编译指令，满足使用需求
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
     * 更简单的运行代码重载
     * @param language 编程语言
     * @param code 程序代码
     * @param stdin 可选的输入缓冲区数据
     * @return 返回运行结果 若执行了死循环或其它阻塞代码，
     * 导致程序无法在限定时间内返回，将会报告超时异常
     */
    fun runCode(language: String, code: String, stdin: String? = null, file: String? = null): RunResult =
        runCode(getSupport(language), RunCodeRequest(language.lowercase(), stdin, useCommand(language), getFiles(language, code, file)))
}