package com.ipuuuuu.doctorwang.data.util

/**
 * 下载报告文件的扩展名推导：
 * 优先按响应 MIME 子类型（图片报告也能正确打开），回退到标题里的扩展名，最后 pdf。
 */
fun resolveDownloadExtension(mimeSubtype: String?, title: String?): String = when (mimeSubtype?.lowercase()) {
    "jpeg", "jpg" -> "jpg"
    "png" -> "png"
    "pdf" -> "pdf"
    else -> title?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
        ?: "pdf"
}
