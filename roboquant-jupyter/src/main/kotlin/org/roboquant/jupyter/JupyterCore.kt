/*
 * Copyright 2020-2023 Neural Layer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.roboquant.jupyter

import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.resources
import org.roboquant.common.Logging
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Present exceptions a bit nicer in notebooks
 */
internal class RoboquantThrowableRenderer : ThrowableRenderer {

    override fun accepts(throwable: Throwable): Boolean {
        return true
    }

    @Suppress("ComplexCondition")
    private fun String?.escapeHtml(): String {
        if (this == null) return ""
        val str = this
        return buildString {
            for (c in str) {
                if (c.code > 127 || c == '"' || c == '\'' || c == '<' || c == '>' || c == '&') {
                    append("&#")
                    append(c.code)
                    append(';')
                } else {
                    append(c)
                }
            }
        }
    }

    override fun render(throwable: Throwable): Any {
        val output = StringWriter()
        PrintWriter(output).use { throwable.printStackTrace(it) }
        val result = """
           <details class="jp-RenderedText" data-mime-type="application/vnd.jupyter.stderr">
                <summary>${throwable.message.escapeHtml()}</summary>
                <pre style="background: transparent;">${output.toString().escapeHtml()}</pre>         
           </details>      
        """.trimIndent()
        return HTML(result, JupyterCore.isolation)
    }

}

/**
 * Should all HTML output be rendered in legacy mode (iFrames), default is false
 */
var legacyNotebookMode: Boolean
    get() = JupyterCore.isolation
    set(value) {
        JupyterCore.isolation = value
    }

/**
 * Integration with Kotlin based Jupyter notebook kernels. Some main features:
 *
 * 1) Support for charts using the Apache ECharts library
 * 2) Default imports
 * 3) Nicer exception handling
 */
internal class JupyterCore(
    private val notebook: Notebook?,
    private val options: MutableMap<String, String?>
) : JupyterIntegration() {

    init {
        logger.debug { options.toMap() }
        logger.debug { notebook }
    }

    companion object {
        private val logger = Logging.getLogger(JupyterCore::class)
        private val HTMLOutputs = CopyOnWriteArrayList<HTMLOutput>()
        internal fun addOutput(htmlOutput: HTMLOutput) = HTMLOutputs.add(htmlOutput)
        internal var isolation = false
    }

    override fun Builder.onLoaded() {
        val version = options["version"] ?: "1.2.0-SNAPSHOT"

        val d = mutableListOf("org.roboquant:roboquant-ta:$version")
        if (options["extra"].toBoolean()) d.add("org.roboquant:roboquant-extra:$version")
        if (options["crypto"].toBoolean()) d.add("org.roboquant:roboquant-crypto:$version")
        if (options["ibkr"].toBoolean()) d.add("org.roboquant:roboquant-ibkr:$version")
        @Suppress("SpreadOperator")
        dependencies(*d.toTypedArray())

        // Only applies to Datalore
        if (notebook.jupyterClientType == JupyterClientType.KOTLIN_NOTEBOOK) {
            isolation = true
        }

        import(
            "org.roboquant.*",
            "org.roboquant.loggers.*",
            "org.roboquant.common.*",
            "org.roboquant.metrics.*",
            "org.roboquant.strategies.*",
            "org.roboquant.orders.*",
            "org.roboquant.feeds.*",
            "org.roboquant.feeds.csv.*",
            "org.roboquant.brokers.sim.*",
            "org.roboquant.brokers.*",
            "org.roboquant.policies.*",
            "org.roboquant.jupyter.*",
            "java.time.Instant",
            "java.time.temporal.ChronoUnit",
            "org.roboquant.ta.*"
        )


        onLoaded {
            addThrowableRenderer(RoboquantThrowableRenderer())
            execute("%logLevel warn")
        }


        resources {
            js("echarts") {
                url(Chart.scriptUrl)
            }
        }

        /**
         * The resources that need to be loaded.

        resources {
            js("echarts") {
                classPath("js/echarts.min.js")
            }
        }
         */

        render<HTMLOutput> {
            if (isolation) HTML(it.asHTMLPage(), true) else HTML(it.asHTML(), false)
        }

        beforeCellExecution {
            HTMLOutputs.clear()
        }

        afterCellExecution { _, _ ->
            HTMLOutputs.forEach {
                this.display(it, null)
            }
            HTMLOutputs.clear()
        }

    }

}

/**
 * Render the output in a Notebook cell. When a [HTMLOutput] result is not the last statement in a Notebook cell, you
 * can use this to make sure it is still gets rendered.
 */
fun HTMLOutput.render() {
    JupyterCore.addOutput(this)
}
