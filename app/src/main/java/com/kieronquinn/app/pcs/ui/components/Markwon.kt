package com.kieronquinn.app.pcs.ui.components

import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.kieronquinn.app.pcs.ui.theme.googleSansFlex
import com.kieronquinn.app.pcs.ui.theme.googleSansMono
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import org.commonmark.node.Heading

@Composable
fun Markwon(modifier: Modifier = Modifier, @RawRes rawFile: Int? = null, text: String? = null) {
    val titleColour = MaterialTheme.colorScheme.onBackground.toArgb()
    CreateMarkdownView(modifier, titleColour, rawFile, text)
}

@Composable
private fun CreateMarkdownView(
    modifier: Modifier,
    titleColour: Int,
    rawFile: Int? = null,
    text: String? = null
) {
    AndroidView(modifier = modifier, factory = { context ->
        val resources = context.resources
        val markdown = when {
            rawFile != null -> {
                resources.openRawResource(rawFile).use {
                    it.reader().use { reader ->
                        reader.readText()
                    }
                }
            }
            text != null -> text
            else -> ""
        }
        val assets = context.assets
        val googleSansFlex = assets.googleSansFlex()
        val googleSansMono = assets.googleSansMono()
        val markwon = Markwon.builder(context)
            .usePlugin(TablePlugin.create(TableTheme.Builder().build()))
            .usePlugin(object: AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingTypeface(googleSansFlex)
                    builder.headingBreakHeight(0)
                    builder.codeTypeface(googleSansMono)
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    val origin = builder.requireFactory(Heading::class.java)
                    builder.setFactory(Heading::class.java) { configuration, props ->
                        arrayOf(
                            origin.getSpans(configuration, props),
                            ForegroundColorSpan(titleColour)
                        )
                    }
                }
        }).build()
        TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTypeface(googleSansFlex)
            this.text = markwon.toMarkdown(markdown)
        }
    })
}