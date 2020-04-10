package com.techyourchance.multithreading.exercises.exercise6;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.WorkerThread;
import com.techyourchance.multithreading.DefaultConfiguration;
import com.techyourchance.multithreading.common.BaseObservable;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ComputeFactorialUseCase extends BaseObservable<ComputeFactorialUseCase.Listener> {

    private static int MAX_TIMEOUT_MS = DefaultConfiguration.DEFAULT_FACTORIAL_TIMEOUT_MS;
    private final Object LOCK = new Object();
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private int mNumberOfThreads; // safe
    private ComputationRange[] mThreadsComputationRanges; // safe
    private AtomicReferenceArray<BigInteger> mThreadsComputationResults; // safe
    private int mNumOfFinishedThreads = 0; // safe
    private long mComputationTimeoutTime; // safe
    private volatile boolean mAbortComputation; // safe

    void computeFactorial(final int factorialArgument, final String timeoutUserInput) {
        final int timeout = getTimeout(timeoutUserInput);
        new Thread(() -> {
            initComputationParams(factorialArgument, timeout);
            startComputation();
            waitForThreadsResultsOrTimeoutOrAbort();
            processComputationResults();
        }).start();
    }

    private int getTimeout(String userInput) {
        int timeout;
        if (userInput.isEmpty()) {
            timeout = MAX_TIMEOUT_MS;
        } else {
            timeout = Integer.parseInt(userInput);
            if (timeout > MAX_TIMEOUT_MS) {
                timeout = MAX_TIMEOUT_MS;
            }
        }
        return timeout;
    }

    private void initComputationParams(int factorialArgument, int timeout) {
        mNumberOfThreads = factorialArgument < 20
            ? 1 : Runtime.getRuntime().availableProcessors();

        synchronized (LOCK) {
            mNumOfFinishedThreads = 0;
        }

        mAbortComputation = false;

        mThreadsComputationResults = new AtomicReferenceArray<>(mNumberOfThreads);

        mThreadsComputationRanges = new ComputationRange[mNumberOfThreads];

        initThreadsComputationRanges(factorialArgument);

        mComputationTimeoutTime = System.currentTimeMillis() + timeout;
    }

    private void initThreadsComputationRanges(int factorialArgument) {
        int computationRangeSize = factorialArgument / mNumberOfThreads;

        long nextComputationRangeEnd = factorialArgument;
        for (int i = mNumberOfThreads - 1; i >= 0; i--) {
            mThreadsComputationRanges[i] = new ComputationRange(
                nextComputationRangeEnd - computationRangeSize + 1,
                nextComputationRangeEnd
            );
            nextComputationRangeEnd = mThreadsComputationRanges[i].start - 1;
        }

        // add potentially "remaining" values to first thread's range
        mThreadsComputationRanges[0].start = 1;
    }

    @WorkerThread
    private void startComputation() {
        for (int i = 0; i < mNumberOfThreads; i++) {

            final int threadIndex = i;

            new Thread(() -> {
                long rangeStart = mThreadsComputationRanges[threadIndex].start;
                long rangeEnd = mThreadsComputationRanges[threadIndex].end;
                BigInteger product = new BigInteger("1");
                for (long num = rangeStart; num <= rangeEnd; num++) {
                    if (isTimedOut()) {
                        break;
                    }
                    product = product.multiply(new BigInteger(String.valueOf(num)));
                }
                mThreadsComputationResults.set(threadIndex, product);

                synchronized (LOCK) {
                    mNumOfFinishedThreads++;
                    LOCK.notifyAll();
                }
            }).start();
        }
    }

    @WorkerThread
    private void waitForThreadsResultsOrTimeoutOrAbort() {
        synchronized (LOCK) {
            while (mNumOfFinishedThreads != mNumberOfThreads
                && !mAbortComputation
                && !isTimedOut()) {
                try {
                    LOCK.wait(getRemainingMillisToTimeout());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private long getRemainingMillisToTimeout() {
        return mComputationTimeoutTime - System.currentTimeMillis();
    }

    @WorkerThread
    private void processComputationResults() {
        String resultString;

        if (mAbortComputation) {
            resultString = "Computation aborted";
        } else {
            resultString = computeFinalResult().toString();
        }

        // need to check for timeout after computation of the final result
        if (isTimedOut()) {
            resultString = "Computation timed out";
        }

        final String finalResultString = resultString;

        mUiHandler.post(() -> {
            for (Listener listener : getListeners()
            ) {
                listener.onFactorialComputed(finalResultString);
            }
        });
    }

    @WorkerThread
    private BigInteger computeFinalResult() {
        BigInteger result = new BigInteger("1");
        for (int i = 0; i < mNumberOfThreads; i++) {
            if (isTimedOut()) {
                break;
            }
            result = result.multiply(mThreadsComputationResults.get(i));
        }
        return result;
    }

    private boolean isTimedOut() {
        return System.currentTimeMillis() >= mComputationTimeoutTime;
    }

    @Override protected void onLastListenerUnregistered() {
        super.onLastListenerUnregistered();
        mAbortComputation = true;
    }

    interface Listener {
        void onFactorialComputed(String result);
    }
}
