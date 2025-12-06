package dev.emortal.flowsolver.solver

import android.content.Context
import android.net.Uri
import java.util.concurrent.CompletableFuture

interface FlowSolverInterface {

    fun detect(context: Context, uri: Uri): Boolean
    fun doSolve(): CompletableFuture<Array<ByteArray>?>

    fun getGridSizeX(): Int
    fun getGridSizeY(): Int

    fun getColors(): Set<Int>

    fun getGrid(): Array<ByteArray>
    fun getVisualisableGrid(): Array<ByteArray>
    fun getFlows(): List<Flow>
    fun cancel()

}