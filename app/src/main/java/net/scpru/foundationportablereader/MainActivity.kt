package net.scpru.foundationportablereader

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.net.toUri
import androidx.core.content.edit
import net.scpru.foundationportablereader.fragments.BundlesFragment
import net.scpru.foundationportablereader.fragments.ReaderFragment
import net.scpru.foundationportablereader.fragments.SettingsFragment

class MainActivity : AppCompatActivity() {
    val pythonInterface: PythonInterface = PythonInterface()
    private lateinit var bottomNavigationView: BottomNavigationView

    private val readerFragment: ReaderFragment by lazy { ReaderFragment() }
    private val bundlesFragment: BundlesFragment by lazy { BundlesFragment() }
    private val settingsFragment: SettingsFragment by lazy { SettingsFragment() }
    private var activeFragment: Fragment = readerFragment

    private val uriPathHelper = URIPathHelper()

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    setupStorage()
                } else {
                    Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val requestLegacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                setupStorage()
            } else {
                Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    private val openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val sharedPref = getPreferences(MODE_PRIVATE) ?: return@let
            sharedPref.edit {
                putString("scp_wiki_home", uri.toString())
            }
            setupStorage()
        } ?: run {
            Toast.makeText(this, "You must select a working directory.", Toast.LENGTH_LONG).show()
            finish()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        pythonInterface.setup(this)
        setupStorage()
    }

    private fun setupStorage(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                storagePermissionLauncher.launch(intent)
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestLegacyPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        val sharedPref = getPreferences(MODE_PRIVATE)
        val dirPath = sharedPref.getString("scp_wiki_home", null)

        if (force || dirPath == null) {
            openDirectoryLauncher.launch(null)
        } else {
            onReady(dirPath)
        }
    }

    fun rebindStorage() {
        supportFragmentManager.beginTransaction().apply {
            remove(readerFragment)
            remove(bundlesFragment)
            remove(settingsFragment)
        }.commit()
        pythonInterface.dispose()
        pythonInterface.setup(this)
        openDirectoryLauncher.launch(null)
    }

    private fun onReady(workdir: String) {
        val treeUri = workdir.toUri()
        val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri)
        val filePath = uriPathHelper.getPath(this, docFile!!.uri)!!

        pythonInterface.initWithHome(filePath)
        setupNavigation()
        setupFragments()
    }

    private fun setupNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_reader -> switchFragment(readerFragment)
                R.id.nav_bundles -> switchFragment(bundlesFragment)
                R.id.nav_settings -> switchFragment(settingsFragment)
                else -> false
            }
        }

        bottomNavigationView.selectedItemId = R.id.nav_reader

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeFragment is ReaderFragment && (activeFragment as ReaderFragment).canGoBack()) {
                    (activeFragment as ReaderFragment).goBack()
                } else if (bottomNavigationView.selectedItemId != R.id.nav_reader) {
                    bottomNavigationView.selectedItemId = R.id.nav_reader
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, readerFragment, "ReaderFragment")
            add(R.id.fragment_container, bundlesFragment, "BundlesFragment").hide(bundlesFragment)
            add(R.id.fragment_container, settingsFragment, "SettingsFragment").hide(settingsFragment)
        }.commit()
        activeFragment = readerFragment
    }

    private fun switchFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction().hide(activeFragment).show(fragment).commit()
        activeFragment = fragment
        return true
    }
}