package site.tiedan.core

/**
 * # Glot 语言运行配置表
 *
 * glot.io 新版 `/api/mux` 接口要求请求中**必须**显式提供 `runInstructions`
 *
 * 数据来源：[https://github.com/glotcode/glot-languages] 仓库的 `src/language` 目录
 *
 * @author tiedanGH
 */
object GlotLanguages {

    /** 主文件名占位符，如 `main.py` */
    private const val FILE = "{file}"
    /** 主文件名去除扩展名并首字母大写，如 `Main`，用于 java、kotlin */
    private const val STEM = "{stem}"
    /** 主文件名替换扩展名为 js，如 `main.js`，用于 typescript */
    private const val JS = "{js}"

    /**
     * @param image 运行所用的 docker 镜像
     * @param build 构建命令，多数语言为空
     * @param run 运行命令
     */
    data class Config(
        val image: String,
        val build: List<String> = emptyList(),
        val run: String,
    )

    data class RunInstructions(
        val buildCommands: List<String>,
        val runCommand: String,
    )

    /**
     * 全部支持的语言配置
     */
    val configs: Map<String, Config> = mapOf(
        "assembly" to Config("glot/assembly:latest", listOf("nasm -f elf64 -o a.o $FILE", "ld -o a.out a.o"), "./a.out"),
        "ats" to Config("glot/ats:latest", listOf("patscc -o a.out $FILE"), "./a.out"),
        "bash" to Config("glot/bash:latest", run = "bash $FILE"),
        "c" to Config("glot/clang:latest", listOf("clang -O2 -o a.out -lm $FILE"), "./a.out"),
        "clisp" to Config("glot/clisp:latest", run = "sbcl --noinform --non-interactive --load $FILE"),
        "clojure" to Config("glot/clojure:latest", run = "clj -M $FILE"),
        "cobol" to Config("glot/cobol:latest", listOf("cobc -x -o a.out $FILE"), "./a.out"),
        "coffeescript" to Config("glot/coffeescript:latest", run = "coffee $FILE"),
        "cpp" to Config("glot/clang:latest", listOf("clang++ -std=c++17 -O2 -o a.out $FILE"), "./a.out"),
        "crystal" to Config("glot/crystal:latest", run = "crystal run $FILE"),
        "csharp" to Config("glot/csharp:latest", listOf("mcs -out:a.exe $FILE"), "mono a.exe"),
        "d" to Config("glot/dlang:latest", listOf("dmd -ofa.out $FILE"), "./a.out"),
        "dart" to Config("glot/dart:latest", run = "dart $FILE"),
        "elixir" to Config("glot/elixir:latest", run = "elixirc $FILE"),
        "elm" to Config("glot/elm:latest", listOf("elm make --output a.js $FILE"), "elm-runner a.js"),
        "erlang" to Config("glot/erlang:latest", run = "escript $FILE"),
        "fsharp" to Config("glot/fsharp:latest", listOf("fsharpc --out:a.exe $FILE"), "mono a.exe"),
        "go" to Config("glot/golang:latest", listOf("go build -o a.out $FILE"), "./a.out"),
        "groovy" to Config("glot/groovy:latest", run = "groovy $FILE"),
        "guile" to Config("glot/guile:latest", run = "guile --no-debug --fresh-auto-compile --no-auto-compile -s $FILE"),
        "hare" to Config("glot/hare:latest", listOf("hare build -o a.out $FILE"), "./a.out"),
        "haskell" to Config("glot/haskell:latest", run = "runghc $FILE"),
        "idris" to Config("glot/idris:latest", listOf("idris2 -o a.out --output-dir . $FILE"), "./a.out"),
        "java" to Config("glot/java:latest", listOf("javac $FILE"), "java $STEM"),
        "javascript" to Config("glot/javascript:latest", run = "node $FILE"),
        "julia" to Config("glot/julia:latest", run = "julia $FILE"),
        "kotlin" to Config("glot/kotlin:latest", listOf("kotlinc $FILE"), "kotlin ${STEM}Kt"),
        "lua" to Config("glot/lua:latest", run = "lua $FILE"),
        "luau" to Config("glot/luau:latest", run = "luau $FILE"),
        "mercury" to Config("glot/mercury:latest", listOf("mmc -o a.out $FILE"), "./a.out"),
        "nim" to Config("glot/nim:latest", run = "nim --hints:off --verbosity:0 compile --run $FILE"),
        "nix" to Config("glot/nix:latest", run = "nix-instantiate --eval $FILE"),
        "ocaml" to Config("glot/ocaml:latest", listOf("ocamlc -o a.out $FILE"), "./a.out"),
        "pascal" to Config("glot/pascal:latest", listOf("fpc -oa.out $FILE"), "./a.out"),
        "perl" to Config("glot/perl:latest", run = "perl $FILE"),
        "php" to Config("glot/php:latest", run = "php $FILE"),
        "python" to Config("glot/python:latest", run = "python $FILE"),
        "raku" to Config("glot/raku:latest", run = "raku $FILE"),
        "ruby" to Config("glot/ruby:latest", run = "ruby $FILE"),
        "rust" to Config("glot/rust:latest", listOf("rustc -o a.out $FILE"), "./a.out"),
        "sac" to Config("glot/sac:latest", listOf("sac2c -t seq -o a.out $FILE"), "./a.out"),
        "scala" to Config("glot/scala:latest", listOf("scalac $FILE"), "scala Main"),
        "swift" to Config("glot/swift:latest", run = "swift $FILE"),
        "typescript" to Config("glot/typescript:latest", listOf("tsc $FILE"), "node $JS"),
        "zig" to Config("glot/zig:latest", run = "zig run $FILE"),
    )

    /**
     * 全部支持的语言名称
     */
    val names: List<String> = configs.keys.sorted()

    fun getConfig(language: String): Config? = configs[language.lowercase()]

    fun isSupported(language: String): Boolean = getConfig(language) != null

    /**
     * 获取运行所需的 docker 镜像（镜像名与语言名并非总是一致）
     */
    fun getImage(language: String): String =
        getConfig(language)?.image ?: "glot/${language.lowercase()}:latest"

    /**
     * 构造 `/api/mux` 所需的 runInstructions
     * @param language 编程语言
     * @param mainFile 主文件名，如 `main.py`
     */
    fun getRunInstructions(language: String, mainFile: String): RunInstructions {
        val config = getConfig(language) ?: throw Exception("不支持的语言 $language")
        return RunInstructions(
            buildCommands = config.build.map { it.fill(mainFile) },
            runCommand = config.run.fill(mainFile),
        )
    }

    private fun String.fill(mainFile: String): String {
        val stem = mainFile.substringBeforeLast('.')
        return replace(FILE, mainFile)
            .replace(STEM, stem.replaceFirstChar { it.uppercaseChar() })
            .replace(JS, "$stem.js")
    }
}
