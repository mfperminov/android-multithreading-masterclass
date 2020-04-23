package com.techyourchance.multithreading.exercises.exercise10

import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.BigInteger

class ComputeFactorialUseCase {

    suspend fun computeFactorial(
        argument: Int,
        timeout: Int
    ): Result {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(timeMillis = timeout.toLong()) {
                    val ranges = getComputationRanges(argument)
                    val partialResults = getPartialResults(ranges)
                    processComputationResults(partialResults)
                }
            } catch (e: TimeoutCancellationException) {
                Result.Timeout
            }
        }
    }

    private fun getComputationRanges(argument: Int): List<ComputationRange> {
        val numberOfThreads = if (argument < 20) {
            1
        } else {
            Runtime.getRuntime()
                .availableProcessors()
        }
        val computationRangeSize = argument / numberOfThreads

        var nextComputationRangeEnd = argument.toLong()
        val threadsComputationRanges = ArrayList<ComputationRange>(numberOfThreads).apply {
            for (i in 0 until numberOfThreads) {
                this.add(ComputationRange(0, 0))
            }
        }
        for (i in numberOfThreads - 1 downTo 0) {
            threadsComputationRanges[i] = ComputationRange(
                nextComputationRangeEnd - computationRangeSize + 1,
                nextComputationRangeEnd
            )
            nextComputationRangeEnd = threadsComputationRanges[i].start - 1
        }

        // add potentially "remaining" values to first thread's range
        threadsComputationRanges[0] = ComputationRange(1, threadsComputationRanges[0].end)
        return threadsComputationRanges
    }

    @WorkerThread
    private suspend fun getPartialResults(ranges: List<ComputationRange>): List<BigInteger> {
        return ranges.map { range ->
            CoroutineScope(Dispatchers.IO).computeProductForRangeAsync(
                range
            )
        }
            .map { deferred -> deferred.await() }
    }

    private fun CoroutineScope.computeProductForRangeAsync(range: ComputationRange): Deferred<BigInteger> {
        return async(Dispatchers.IO) {
            var product = BigInteger("1")
            for (num in range.start..range.end) {
                product = product.multiply(BigInteger(num.toString()))
            }
            product
        }
    }

    @WorkerThread
    private fun processComputationResults(partialResults: List<BigInteger>): Result {
        val result = computeFinalResult(partialResults)
        return Result.Factorial(result)
    }

    @WorkerThread
    private fun computeFinalResult(partialResults: List<BigInteger>): BigInteger {
        var result = BigInteger("1")
        for (i in partialResults.indices) {
            result = result.multiply(partialResults[i])
        }
        return result
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

        class Factorial(private val bigInteger: BigInteger) : Result() {
            override fun toString(): String {
                return bigInteger.toString()
            }
        }
    }
}
