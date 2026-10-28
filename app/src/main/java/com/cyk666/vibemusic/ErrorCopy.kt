package com.cyk666.vibemusic

/**
 * 失败提示的统一文案（CODE-REVIEW-2026-10-28 CODE-2）。
 *
 * 原来「下载失败」有 4 处孪生字面量（两处 `"下载失败：保存失败"` + 两处
 * `"下载失败：${friendlyNetworkMessage(e)}"`），且半角冒号 `": "` 与全角 `"："` 混用。
 * 现在：**所有**带原因的失败提示走 [diagnosableError]（全角冒号、附原始报错），
 * 只有"没有异常对象"的固定文案留在常量里。
 */
const val DOWNLOAD_FAILED_PREFIX = "下载失败"

/** 下载返回 false（内容非法 / 写盘失败）—— 没有异常对象可附，用固定文案。 */
const val DOWNLOAD_SAVE_FAILED_MESSAGE = "下载失败：保存失败"
