package com.techyourchance.multithreading.exercises.exercise10

import androidx.annotation.WorkerThread
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ComputeFactorialUseCase {

    private val reentrantLock = ReentrantLock()
    private val lockCondition = reentrantLock.newCondition()

    private var numberOfThreads: Int = 0
    private var threadsComputationRanges: Array<ComputationRange?> = arrayOf()
    @Volatile private var threadsComputationResults: Array<BigInteger?> = arrayOf()
    private var numOfFinishedThreads = 0

    private var computationTimeoutTime: Long = 0

    private var abortComputation: Boolean = false

    suspend fun computeFactorial(
        argument: Int,
        timeout: Int
    ): Result {

        initComputationParams(argument, timeout)
        startComputation()
        waitForThreadsResultsOrTimeoutOrAbort()
        return processComputationResults()

    }

    private fun initComputationParams(
        factorialArgument: Int,
        timeout: Int
    ) {
        numberOfThreads = if (factorialArgument < 20)
            1
        else
            Runtime.getRuntime().availableProcessors()

        synchronized(reentrantLock) {
            numOfFinishedThreads = 0
            abortComputation = false
        }

        threadsComputationResults = arrayOfNulls(numberOfThreads)

        threadsComputationRanges = arrayOfNulls(numberOfThreads)

        initThreadsComputationRanges(factorialArgument)

        computationTimeoutTime = System.currentTimeMillis() + timeout
    }

    private fun initThreadsComputationRanges(factorialArgument: Int) {
        val computationRangeSize = factorialArgument / numberOfThreads

        var nextComputationRangeEnd = factorialArgument.toLong()
        for (i in numberOfThreads - 1 downTo 0) {
            threadsComputationRanges[i] = ComputationRange(
                nextComputationRangeEnd - computationRangeSize + 1,
                nextComputationRangeEnd
            )
            nextComputationRangeEnd = threadsComputationRanges[i]!!.start - 1
        }

        // add potentially "remaining" values to first thread's range
        threadsComputationRanges[0] = ComputationRange(1, threadsComputationRanges[0]!!.end)
    }

    @WorkerThread
    private fun startComputation() {
        for (i in 0 until numberOfThreads) {

            Thread {
                val rangeStart = threadsComputationRanges[i]!!.start
                val rangeEnd = threadsComputationRanges[i]!!.end
                var product = BigInteger("1")
                for (num in rangeStart..rangeEnd) {
                    if (isTimedOut()) {
                        break
                    }
                    product = product.multiply(BigInteger(num.toString()))
                }
                threadsComputationResults[i] = product

                reentrantLock.withLock {
                    numOfFinishedThreads++
                    lockCondition.signalAll()
                }

            }.start()
        }
    }

    @WorkerThread
    private fun waitForThreadsResultsOrTimeoutOrAbort() {
        reentrantLock.withLock {
            while (numOfFinishedThreads != numberOfThreads && !abortComputation && !isTimedOut()) {
                try {
                    lockCondition.await(remainingMillisToTimeout(), TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    return
                }

            }
        }
    }

    @WorkerThread
    private fun processComputationResults(): Result {
        if (abortComputation) {
            return Result.Aborted
        }

        val result = computeFinalResult()

        // need to check for timeout after computation of the final result
        if (isTimedOut()) {
            return Result.Timeout
        }

        return Result.Factorial(result)
    }

    @WorkerThread
    private fun computeFinalResult(): BigInteger {
        var result = BigInteger("1")
        for (i in 0 until numberOfThreads) {
            if (isTimedOut()) {
                break
            }
            result = result.multiply(threadsComputationResults[i])
        }
        return result
    }

    private fun remainingMillisToTimeout(): Long {
        return computationTimeoutTime - System.currentTimeMillis()
    }

    private fun isTimedOut(): Boolean {
        return System.currentTimeMillis() >= computationTimeoutTime
    }

    private data class ComputationRange(
        val start: Long,
        val end: Long
    )

    sealed class Result {
        object Timeout : Result() {
            override fun toString(): String {
                return "Computation timed out"
            }
        }

        object Aborted : Result() {
            override fun toString(): String {
                return "Computation was aborted"
            }
        }

        class Factorial(private val bigInteger: BigInteger) : Result() {
            override fun toString(): String {
                return bigInteger.toString()
            }
        }
    }
}
