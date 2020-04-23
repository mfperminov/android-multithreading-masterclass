package com.techyourchance.multithreading.exercises.exercise10

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.techyourchance.multithreading.DefaultConfiguration
import com.techyourchance.multithreading.R
import com.techyourchance.multithreading.common.BaseFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Exercise10Fragment : BaseFragment() {

    private lateinit var edtArgument: EditText
    private lateinit var edtTimeout: EditText
    private lateinit var btnStartWork: Button
    private lateinit var txtResult: TextView
    private var job: Job? = null

    private lateinit var computeFactorialUseCase: ComputeFactorialUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        computeFactorialUseCase = ComputeFactorialUseCase()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_exercise_10, container, false)

        view.apply {
            edtArgument = findViewById(R.id.edt_argument)
            edtTimeout = findViewById(R.id.edt_timeout)
            btnStartWork = findViewById(R.id.btn_compute)
            txtResult = findViewById(R.id.txt_result)
        }

        btnStartWork.setOnClickListener { _ ->
            if (edtArgument.text.toString().isEmpty()) {
                return@setOnClickListener
            }

            txtResult.text = ""
            btnStartWork.isEnabled = false


            val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(btnStartWork.windowToken, 0)

            val argument = Integer.valueOf(edtArgument.text.toString())

            job = CoroutineScope(Dispatchers.Main).launch {
                val result = computeFactorialUseCase.computeFactorial(argument, getTimeout())
                txtResult.text = result.toString()
                btnStartWork.isEnabled = true
            }
        }

        return view
    }

    override fun onStop() {
        super.onStop()
        job?.cancel()
    }

    override fun getScreenTitle(): String {
        return "Exercise 10"
    }

    private fun getTimeout() : Int {
        var timeout: Int
        if (edtTimeout.text.toString().isEmpty()) {
            timeout = MAX_TIMEOUT_MS
        } else {
            timeout = Integer.valueOf(edtTimeout.text.toString())
            if (timeout > MAX_TIMEOUT_MS) {
                timeout = MAX_TIMEOUT_MS
            }
        }
        return timeout
    }
    
    companion object {
        fun newInstance(): Fragment {
            return Exercise10Fragment()
        }
        private const val MAX_TIMEOUT_MS = DefaultConfiguration.DEFAULT_FACTORIAL_TIMEOUT_MS
    }
}
