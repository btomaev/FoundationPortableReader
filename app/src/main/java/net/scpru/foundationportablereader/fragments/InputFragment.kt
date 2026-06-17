package net.scpru.foundationportablereader.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.scpru.foundationportablereader.MainActivity
import net.scpru.foundationportablereader.PythonInterface
import net.scpru.foundationportablereader.R

class InputFragment : Fragment() {
    private var currentImportingArticle: String = ""
    private lateinit var pythonInterface: PythonInterface
    private lateinit var editText: EditText
    private lateinit var importButton: Button
    private lateinit var progressContainer: ConstraintLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var cancelButton: Button

    @Volatile
    private var isStopRequested = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pythonInterface = (requireActivity() as MainActivity).pythonInterface

        editText = view.findViewById(R.id.edit_text)
        importButton = view.findViewById(R.id.import_button)
        progressContainer = view.findViewById(R.id.progress_container)
        progressBar = view.findViewById(R.id.progress_bar)
        progressText = view.findViewById(R.id.progress_text)
        cancelButton = view.findViewById(R.id.cancel_button)

        setupFragment()
    }

    private fun setupFragment() {
        cancelButton.setOnClickListener {
            isStopRequested = true
            editText.isEnabled = true
            importButton.isEnabled = true
            progressContainer.visibility = View.GONE
        }

        importButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isBlank()) {
                val message = getString(R.string.empty_input_error)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isStopRequested = false
            importButton.isEnabled = false
            editText.isEnabled = false
            progressContainer.visibility = View.VISIBLE
            progressBar.progress = 0

            lifecycleScope.launch {
                val pages = text.trim().split("\n").filter { it.isNotBlank() }
                pythonInterface.importPages(pages, ::importProgressCallback)
            }
        }
    }

    fun importProgressCallback(current: String?, fetched: Int, imported: Int, failed: Int, total: Int): Boolean {
        if (isStopRequested)
            return true

        currentImportingArticle = current ?: currentImportingArticle

        lifecycleScope.launch(Dispatchers.Main) {
            val progress = ((imported + failed) * 100) / total
            progressBar.progress = progress
            progressText.text =
                getString(
                    R.string.articles_import_progress_text,
                    currentImportingArticle.take(15),
                    fetched,
                    imported,
                    failed,
                    total
                )
        }

        return false
    }
}
