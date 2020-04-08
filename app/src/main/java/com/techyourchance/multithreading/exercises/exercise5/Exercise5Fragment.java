package com.techyourchance.multithreading.exercises.exercise5;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.techyourchance.multithreading.DefaultConfiguration;
import com.techyourchance.multithreading.R;
import com.techyourchance.multithreading.common.BaseFragment;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class Exercise5Fragment extends BaseFragment {

    private static final int NUM_OF_MESSAGES = DefaultConfiguration.DEFAULT_NUM_OF_MESSAGES;
    private static final int BLOCKING_QUEUE_CAPACITY =
        DefaultConfiguration.DEFAULT_BLOCKING_QUEUE_SIZE;
    private final Object LOCK = new Object();
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final MyBlockingQueue mBlockingQueue = new MyBlockingQueue(BLOCKING_QUEUE_CAPACITY);
    private Button mBtnStart;
    private ProgressBar mProgressBar;
    private TextView mTxtReceivedMessagesCount;
    private TextView mTxtExecutionTime;
    private int mNumOfFinishedConsumers;
    private int mNumOfReceivedMessages;
    private long mStartTimestamp;

    public static Fragment newInstance() {
        return new Exercise5Fragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exercise_5, container, false);

        mBtnStart = view.findViewById(R.id.btn_start);
        mProgressBar = view.findViewById(R.id.progress);
        mTxtReceivedMessagesCount = view.findViewById(R.id.txt_received_messages_count);
        mTxtExecutionTime = view.findViewById(R.id.txt_execution_time);

        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBtnStart.setEnabled(false);
                mTxtReceivedMessagesCount.setText("");
                mTxtExecutionTime.setText("");
                mProgressBar.setVisibility(View.VISIBLE);

                mNumOfReceivedMessages = 0;
                mNumOfFinishedConsumers = 0;

                startCommunication();
            }
        });

        return view;
    }

    @Override
    protected String getScreenTitle() {
        return "Exercise 5";
    }

    private void startCommunication() {

        mStartTimestamp = System.currentTimeMillis();

        // watcher-reporter thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    while (mNumOfFinishedConsumers < NUM_OF_MESSAGES) {
                        try {
                            LOCK.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                showResults();
            }
        }).start();

        // producers init thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < NUM_OF_MESSAGES; i++) {
                    startNewProducer(i);
                }
            }
        }).start();

        // consumers init thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < NUM_OF_MESSAGES; i++) {
                    startNewConsumer();
                }
            }
        }).start();
    }

    private void startNewProducer(final int index) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(DefaultConfiguration.DEFAULT_PRODUCER_DELAY_MS);
                } catch (InterruptedException e) {
                    return;
                }
                mBlockingQueue.put(index);
            }
        }).start();
    }

    private void startNewConsumer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int message = mBlockingQueue.take();
                synchronized (LOCK) {
                    if (message != -1) {
                        mNumOfReceivedMessages++;
                    }
                    mNumOfFinishedConsumers++;
                    LOCK.notifyAll();
                }
            }
        }).start();
    }

    private void showResults() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mProgressBar.setVisibility(View.INVISIBLE);
                mBtnStart.setEnabled(true);
                synchronized (LOCK) {
                    mTxtReceivedMessagesCount.setText(
                        "Received messages: " + mNumOfReceivedMessages);
                }
                long executionTimeMs = System.currentTimeMillis() - mStartTimestamp;
                mTxtExecutionTime.setText("Execution time: " + executionTimeMs + "ms");
            }
        });
    }

    /**
     * Simplified implementation of blocking queue.
     */
    private static class MyBlockingQueue {

        private final int mCapacity;
        private final Queue<Integer> mQueue = new LinkedList<>();
        private final Object INSERT_LOCK = new Object();
        private final Object RETRIEVE_LOCK = new Object();

        private AtomicInteger mCurrentSize = new AtomicInteger(0);

        private MyBlockingQueue(int capacity) {
            mCapacity = capacity;
        }

        /**
         * Inserts the specified element into this queue, waiting if necessary
         * for space to become available.
         *
         * @param number the element to add
         */
        public void put(int number) {
            synchronized (INSERT_LOCK) {
                while (mCurrentSize.get() >= mCapacity) {
                    try {
                        INSERT_LOCK.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            synchronized (RETRIEVE_LOCK) {
                mQueue.offer(number);
                mCurrentSize.getAndIncrement();
                RETRIEVE_LOCK.notifyAll();
            }
        }

        /**
         * Retrieves and removes the head of this queue, waiting if necessary
         * until an element becomes available.
         *
         * @return the head of this queue
         */
        public int take() {
            synchronized (RETRIEVE_LOCK) {
                while (mCurrentSize.get() <= 0) {
                    try {
                        RETRIEVE_LOCK.wait();
                    } catch (InterruptedException e) {
                        return -1;
                    }
                }
            }

            synchronized (INSERT_LOCK) {
                mCurrentSize.getAndDecrement();
                Integer message = mQueue.poll();
                if (message != null) {
                    return message;
                }
                INSERT_LOCK.notifyAll();
            }
            return -1;
        }
    }
}
