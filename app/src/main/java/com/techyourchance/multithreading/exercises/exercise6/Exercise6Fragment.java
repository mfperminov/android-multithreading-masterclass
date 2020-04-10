package com.techyourchance.multithreading.exercises.exercise6;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.techyourchance.multithreading.R;
import com.techyourchance.multithreading.common.BaseFragment;

public class Exercise6Fragment extends BaseFragment implements ComputeFactorialUseCase.Listener {

    public static Fragment newInstance() {
        return new Exercise6Fragment();
    }

    private Button mBtnStartWork;
    private TextView mTxtResult;

    private ComputeFactorialUseCase computeFactorialUseCase;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        computeFactorialUseCase = new ComputeFactorialUseCase();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exercise_6, container, false);

        EditText mEdtArgument = view.findViewById(R.id.edt_argument);
        EditText mEdtTimeout = view.findViewById(R.id.edt_timeout);
        mBtnStartWork = view.findViewById(R.id.btn_compute);
        mTxtResult = view.findViewById(R.id.txt_result);

        mBtnStartWork.setOnClickListener(v -> {
            if (mEdtArgument.getText().toString().isEmpty()) {
                return;
            }

            mTxtResult.setText("");
            mBtnStartWork.setEnabled(false);


            InputMethodManager imm =
                    (InputMethodManager) requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mBtnStartWork.getWindowToken(), 0);

            int argument = Integer.parseInt(mEdtArgument.getText().toString());

            computeFactorialUseCase.computeFactorial(argument, mEdtTimeout.getText().toString());
        });

        return view;
    }

    @Override public void onStart() {
        super.onStart();
        computeFactorialUseCase.registerListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        computeFactorialUseCase.unregisterListener(this);
    }

    @Override
    protected String getScreenTitle() {
        return "Exercise 6";
    }

    @Override public void onFactorialComputed(String result) {
        mTxtResult.setText(result);
        mBtnStartWork.setEnabled(true);
    }
}
