package net.scpru.foundationportablereader.fragments

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.scpru.foundationportablereader.MainActivity
import net.scpru.foundationportablereader.PythonInterface
import net.scpru.foundationportablereader.R
import java.io.ByteArrayInputStream

class ReaderFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var cookieManager: CookieManager
    private lateinit var pythonInterface: PythonInterface

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reader, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pythonInterface = (requireActivity() as MainActivity).pythonInterface
        setupWebView(view)
    }

    override fun onPause() {
        super.onPause()
        cookieManager.flush()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(view: View) {
        WebView.setWebContentsDebuggingEnabled(true)

        webView = view.findViewById(R.id.webView)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout) // Инициализация

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        webView.viewTreeObserver.addOnScrollChangedListener {
            swipeRefreshLayout.isEnabled = webView.scrollY == 0
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                return handleRequest(request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
            }
        }

        if (pythonInterface.isReady()) {
            bootstrapWebView()
        }
    }

    fun bootstrapWebView() {
        webView.loadUrl("http://localhost:8000/")
    }

    private fun handleRequest(request: WebResourceRequest?): WebResourceResponse?
            = runBlocking(Dispatchers.IO) {
        val url = request?.url
        val domain = url?.host

        if (domain !in listOf("localhost", "scpfoundation.net", "files.scpfoundation.net")) {
            Log.i("RequestHandler[external]", "${request?.method} $url")
            return@runBlocking null
        }

        val path = url?.path ?: ""
        val urlString = url.toString()

        val requestHeaders = request?.requestHeaders?.toMutableMap() ?: mutableMapOf()

        val cookies = cookieManager.getCookie(urlString)
        if (cookies != null) {
            requestHeaders["cookie"] = cookies
        }

        requestHeaders["if-None-Match"] = ""

        Log.i("RequestHandler[request]", "${request?.method} $path")

        val response = pythonInterface.getPage(path, request?.method ?: "GET", requestHeaders)

        requireActivity().runOnUiThread {
            response.cookies.forEach { cookie ->
                cookieManager.setCookie(urlString, cookie)
            }
        }

        if (response.statusCode in 300..399) {
            if (response.headers.containsKey("location")){
                val location = response.headers["location"]!!
                val redirectUri = if (location.startsWith("/")) {
                    url!!.buildUpon().path(location).query(null).fragment(null).build()
                } else {
                    location.toUri()
                }
                val redirectRequest = object : WebResourceRequest {
                    override fun getUrl(): Uri = redirectUri
                    override fun isRedirect(): Boolean = true
                    override fun getMethod(): String = "GET"
                    override fun getRequestHeaders(): MutableMap<String, String> =
                        request?.requestHeaders ?: mutableMapOf()
                    override fun hasGesture(): Boolean = request?.hasGesture() ?: false
                    override fun isForMainFrame(): Boolean = request?.isForMainFrame ?: false
                }
                Log.i("RequestHandler[redirect]", "${response.statusCode} $response.reason ${request?.method} $path -> $redirectUri")
                return@runBlocking handleRequest(redirectRequest)
            }
            return@runBlocking null
        }

        val mimeType = response.headers["content-type"]
            ?.split(";")?.get(0)
            ?: "text/plain"

        Log.i("RequestHandler[response]", "${response.statusCode} ${response.reason} [$mimeType] ${request?.method} $path")

        val inputStream = ByteArrayInputStream(response.body)

        return@runBlocking WebResourceResponse(
            mimeType,
            "utf-8",
            response.statusCode,
            response.reason,
            response.headers,
            inputStream
        )
    }

    fun canGoBack(): Boolean {
        return if (::webView.isInitialized) webView.canGoBack() else false
    }

    fun goBack() {
        webView.goBack()
    }
}