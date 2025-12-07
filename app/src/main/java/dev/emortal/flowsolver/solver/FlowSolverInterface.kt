package dev.emortal.flowsolver.solver

import android.content.Context
import android.net.Uri
import java.util.concurrent.CompletableFuture

interface FlowSolverInterface {

    fun detect(context: Context, uri: Uri): Boolean
    fun doSolve(): CompletableFuture<Grid?>

    fun getColors(): Set<Int>

    fun getGrid(): Grid?
    fun getGridPixelSize(): Int
    fun getFlows(): List<Flow>
    fun cancel()

}