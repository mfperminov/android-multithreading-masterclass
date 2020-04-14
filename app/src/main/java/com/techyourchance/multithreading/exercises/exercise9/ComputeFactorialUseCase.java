package com.techyourchance.multithreading.exercises.exercise9;

import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ComputeFactorialUseCase {

    private final Object LOCK = new Object();
    private int mNumberOfThreads;
    private ComputationRange[] mThreadsComputationRanges;
    private AtomicReferenceArray<BigInteger> mThreadsComputationResults;
    private int mNumOfFinishedThreads = 0;

    private long mComputationTimeoutTime;

    private boolean mAbortComputation;

    public Observable<FactorialResult> computeFactorial(final int argument, final int timeout) {
        initComputationParams(argument, timeout);
        return Flowable.range(0, mNumberOfThreads)
            .flatMap(threadIndex -> Flowable
                .fromCallable(() -> {
                    long rangeStart = mThreadsComputationRanges[threadIndex].start;
                    long rangeEnd = mThreadsComputationRanges[threadIndex].end;
                    BigInteger product = new BigInteger("1");
                    for (long num = rangeStart; num <= rangeEnd; num++) {
                        if (isTimedOut()) {
                            throw new TimeoutException();
                        }
                        product = product.multiply(new BigInteger(String.valueOf(num)));
                    }
                    return product;
                }))
                .subscribeOn(Schedulers.io())
                .parallel(mNumberOfThreads)
                .runOn(Schedulers.io())
                .sequential()
                .scan(BigInteger::multiply)
                .takeLast(1)
                .map(bigInteger -> new FactorialResult(bigInteger, null)
                )
                .toObservable();
    }

    private void initComputationParams(int factorialArgument, int timeout) {
        mNumberOfThreads = factorialArgument < 20
            ? 1 : Runtime.getRuntime().availableProcessors();

        synchronized (LOCK) {
            mNumOfFinishedThreads = 0;
            mAbortComputation = false;
        }

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

    @WorkerThread
    private FactorialResult processComputationResults() {
        if (mAbortComputation) {
            return new FactorialResult(null, "Computation aborted");
        }

        BigInteger result = computeFinalResult();

        // need to check for timeout after computation of the final result
        if (isTimedOut()) {
            return new FactorialResult(null, "Computation timed out");
        }

        return new FactorialResult(result, null);
    }

    @WorkerThread
    private BigInteger computeFinalResult() {
        BigInteger result = new BigInteger("1");
        for (int i = 0; i < mNumberOfThreads; i++) {
            if (isTimedOut()) {
                break;
            }
            try {
                result = result.multiply(mThreadsComputationResults.get(i));
            } catch (Exception e) {
                Log.e("Rx", e.getMessage());
            }
        }
        return result;
    }

    private long getRemainingMillisToTimeout() {
        return mComputationTimeoutTime - System.currentTimeMillis();
    }

    private boolean isTimedOut() {
        return System.currentTimeMillis() >= mComputationTimeoutTime;
    }

    static class FactorialResult {
        @Nullable final BigInteger factorial;
        @Nullable final String errorMessage;

        FactorialResult(@Nullable BigInteger factorial, @Nullable String errorMessage) {
            this.factorial = factorial;
            this.errorMessage = errorMessage;
        }
    }

    private static class ComputationRange {
        private long start;
        private long end;

        public ComputationRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class TimeoutException extends Exception {
        @Override public String getMessage() {
            return "Computation timed out";
        }
    }
}
