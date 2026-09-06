package site.tiedan.utils

import java.io.File
import kotlin.math.log10
import kotlin.math.pow

/**
 * # 文件大小工具
 *
 * @author tiedanGH
 */
object FileSizeUtil {

    /**
     * 递归统计目录占用的字节数，目录不存在时返回 0
     */
    fun folderSize(folder: File?): Long {
        if (folder == null || !folder.exists()) return 0L
        if (folder.isFile) return folder.length()
        val files = folder.listFiles() ?: return 0L
        return files.sumOf { folderSize(it) }
    }

    /**
     * 将字节数格式化为带单位的可读形式
     */
    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
        return String.format("%.2f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }
}
