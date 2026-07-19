package com.nebula.browser.ai.markdown

import android.content.Context
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

object MarkdownRenderer {
    @Volatile private var INST: Markwon? = null
    fun get(context: Context): Markwon = INST ?: synchronized(this) {
        INST ?: Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(SyntaxHighlightPlugin.create(
                io.noties.prism4j.Prism4j(),
                Prism4jThemeDarkula.create()
            ))
            .build()
            .also { INST = it }
    }
}
