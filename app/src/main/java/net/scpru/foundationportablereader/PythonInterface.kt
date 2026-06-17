package net.scpru.foundationportablereader

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlin.system.measureTimeMillis

data class PageResponse(
    val statusCode: Int,
    val reason: String,
    val headers: Map<String, String>,
    val cookies: List<String>,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageResponse

        if (statusCode != other.statusCode) return false
        if (reason != other.reason) return false
        if (headers != other.headers) return false
        if (cookies != other.cookies) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + reason.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + cookies.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

class PythonInterface {
    private lateinit var py: Python
    lateinit var pythonInterface: PyObject
        private set

    fun setup(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        py = Python.getInstance()
        pythonInterface = py.getModule("java_interface")
            .callAttr("JavaInterface")
    }

    fun initWithHome(path: String) {
        pythonInterface.callAttr("check_and_setup", path)
    }

    fun dispose() {
        pythonInterface.callAttr("dispose")
    }

    fun getPage(path: String, method: String, requestHeaders: Map<String, String>?): PageResponse {
        lateinit var response: List<PyObject>
        val pythonCallDuration = measureTimeMillis {
            response = pythonInterface.callAttr("get_page", path, method, requestHeaders)
                .asList()
        }
        Log.i("PythonInterface[timing]", "Render page for '$path' took $pythonCallDuration ms")

        val statusCode = response[0].toInt()
        val reason = response[1].toString()

        val responseHeadersList = response[2].asList()
        val body: ByteArray = response[3]
            .toJava(ByteArray::class.java)

        val cookies: MutableList<String> = mutableListOf()

        val responseHeaders = responseHeadersList.associate { pyTuple ->
            val tupleItems = pyTuple.asList()
            val headerName = tupleItems[0].toString().lowercase()
            val headerValue = tupleItems[1].toString()

            if (headerName.equals("set-cookie", ignoreCase = true)) {
                cookies.add(headerValue)
            }
            headerName to headerValue
        }

        return PageResponse(statusCode, reason, responseHeaders, cookies, body)
    }

    fun importPages(pages: List<String>, importProgressCallback: (String?, Int, Int, Int, Int) -> (Boolean)): Pair<List<String>, List<String>> {
        lateinit var successful: List<String>
        lateinit var failed: List<String>

        val safeCallback: (String?, Any?, Any?, Any?, Any?) -> Boolean = { current, fetched, imported, failed, total ->
            importProgressCallback(
                current?.toString(),
                (fetched as? Number)?.toInt() ?: 0,
                (imported as? Number)?.toInt() ?: 0,
                (failed as? Number)?.toInt() ?: 0,
                (total as? Number)?.toInt() ?: 0
            )
        }

        try {
            val response = pythonInterface.callAttr(
                "import_pages",
                pages.toTypedArray(),
                safeCallback
            ).asList()

            successful = response[0].asList().map { it.toString() }
            failed = response[1].asList().map { it.toString() }
        } catch (_: Exception) {
            emptyList<PyObject>() to pages.map { PyObject.fromJava(it) }
        }

        return successful to failed
    }

    fun isReady(): Boolean {
        return pythonInterface.callAttr("is_ready").toJava(Boolean::class.java)
    }
}