package utils

import JCompilerCollection.save
import data.ExtraData

object Statistics {
    private var name: String = "unknown"
    fun setName(name: String) { this.name = name }

    fun countRun() {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("run") ?: 0
        ExtraData.statistics[name]?.set("run", current + 1)
        ExtraData.save()
    }

    fun countMarkdown() {
        createIfNotExist(name)
        val current = ExtraData.statistics[name]?.get("markdown") ?: 0
        ExtraData.statistics[name]?.set("markdown", current + 1)
        ExtraData.save()
    }

    fun getAllStatistics(): String {
        var totalRun = 0L
        var totalMarkdown = 0L

        for (entry in ExtraData.statistics.values) {
            totalRun += entry["run"] ?: 0L
            totalMarkdown += entry["markdown"] ?: 0L
        }

        return "·总执行次数：$totalRun\n·调用markdown次数：$totalMarkdown\n"
    }

    fun getStatistic(name: String): String {
        val stat = ExtraData.statistics[name]
        val run = stat?.get("run") ?: "0"
        val markdown = stat?.get("markdown")

        return buildString {
            appendLine("·总执行次数：$run")
            if (markdown != null) {
                append("·调用markdown次数：$markdown")
            }
        }
    }

    private fun createIfNotExist(name: String) {
        if (!ExtraData.statistics.containsKey(name)) {
            ExtraData.statistics[name] = mutableMapOf()
        }
    }

}