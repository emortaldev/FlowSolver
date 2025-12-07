package dev.emortal.flowsolver.solver

import Pos
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.get
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import kotlin.properties.Delegates

private val GRID_START_COLORS = arrayOf(
    Color.hsv(120f, 0.422f, 0.427f).toArgb(),
    Color.hsv(0f, 0.504f, 0.498f).toArgb(),
    Color.hsv(224.5f, 0.457f, 0.498f).toArgb(),
)

class FlowSolverArray(val slow: Boolean = false) : FlowSolverInterface {

    private val uniqueColors = mutableSetOf(Color.Black.toArgb())

    private lateinit var grid: Grid

    private var gridPxSize by Delegates.notNull<Int>()

    private val flows = mutableListOf<Flow>()

    private var cancel = false

    override fun detect(context: Context, uri: Uri): Boolean {
        val source: ImageDecoder.Source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { a, _, _ ->
            a.isMutableRequired = true
        }

        // Detect grid start
        var gridStartX = -1
        var gridStartY = -1
        var startColor = -1

        val halfWidth = bitmap.width / 2
        top@ for (y in 0 until bitmap.height) {
            if (GRID_START_COLORS.contains(bitmap[halfWidth, y])) {
                startColor = bitmap[halfWidth,y]
                for (x in 0 until halfWidth) {
                    if (bitmap[x, y] == startColor) {
                        gridStartX = x
                        gridStartY = y
                        break@top
                    }
                }
                break@top
            }
        }

        if (gridStartX == -1) {
            Log.i("FlowSolver", "Failed to read image")
            return false
        }

        // Detect grid size
        var nextGridPixel = 0
        for (x in gridStartX + 10..1000) {
            if (bitmap[x, gridStartY + 10] == startColor) {
                nextGridPixel = x
                break
            }
        }

        gridPxSize = nextGridPixel - gridStartX
        val gridSizeX = (bitmap.width - (gridStartX * 2)) / gridPxSize

        // assume grid is square
        Log.i("FlowSolver", "Set grid value")
        grid = Grid(gridSizeX, gridSizeX)

        // Detect flows
        for (x in 0 until gridSizeX) {
            val pixelY = x * gridPxSize + gridStartY + (gridPxSize / 2)
            for (y in 0 until gridSizeX) {
                val pixelX = y * gridPxSize + gridStartX + (gridPxSize / 2)

                val rgb = bitmap[pixelX, pixelY]
                val color = Color(rgb)
                if (color.red + color.green + color.blue < 0.47) {
                    // probably background
                    continue
                }

                if (!uniqueColors.contains(rgb)) {
                    uniqueColors.add(rgb)
                    flows.add(Flow((uniqueColors.indexOf(rgb) + 1).toByte(), Pos(x, y)))
                }

                grid[x, y] = (uniqueColors.indexOf(rgb) + 1).toByte()

                flows[uniqueColors.indexOf(rgb) - 1].endNode = Pos(x, y)


            }
        }

        Log.i("FlowSolver", "Solving flow!")

        val list = flows.sortedBy {
            var weight = 0

            if (grid.onEdge(it.startNode.x, it.startNode.y)) weight += 1
            if (grid.borderingFlow(it.startNode.x, it.startNode.y)) weight += 1
            if (grid.onEdge(it.endNode.x, it.endNode.y)) weight += 1
            if (grid.borderingFlow(it.endNode.x, it.endNode.y)) weight += 1
            return@sortedBy weight
        }.reversed().toMutableList()
        Log.i("FlowSolver", list.toString())
        flows.clear()
        flows.addAll(list)

        return flows.isNotEmpty()
    }

    override fun doSolve(): CompletableFuture<Grid?> {
        val future = CompletableFuture.supplyAsync({
            val result = solve()

            if (result == null) {
                Log.e("FlowSolver", "Solving failed!")
                return@supplyAsync null
            }

            Log.i("FlowSolver", "Solving success!")

            return@supplyAsync grid
        }, ForkJoinPool.commonPool())

        return future
    }

    fun solve(): List<Flow>? {
        if (cancel) return emptyList()

        if (slow) Thread.sleep(50)

        for (flow in flows) {
            if (flow.completed) {
                continue
            }

            val endOfFlow = flow.lastNode
            // sort neighbours by closest to the end node
            // prefer going along edge
            val nei = grid.emptyNeighbours(endOfFlow.x, endOfFlow.y).sortedBy {
                var weight = 0
                weight += flow.endNode.distance(it.x, it.y)

                if (!grid.onEdge(endOfFlow.x + it.x, endOfFlow.y + it.y)) {
                    weight += 500
                }
                if (!grid.borderingFlow(endOfFlow.x + it.x, endOfFlow.y + it.y)) {
                    weight += 500
                }

                return@sortedBy weight
            }

            if (endOfFlow.distance(flow.endNode) == 1) {
                flow.completed = true
                if (flows.all { it.completed }) {
                    // TODO: Check if grid is actually done ie no empty

                    return flows
                }
                continue
            }


            for (dir in nei) {
                val newPos = endOfFlow.add(dir.x, dir.y)
                flow.lastNode = newPos
                grid[newPos.x, newPos.y] = flow.flowNum

                if (flows.any { grid.isSurrounded(it.lastNode.x, it.lastNode.y, it.flowNum) }) {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x, newPos.y] = 0
                    continue
                }
                if (grid.surroundedSquare(flow.flowNum)) {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x, newPos.y] = 0
                    continue
                }
                if (
                    flows.any {
                        if (it.flowNum == flow.flowNum) return@any false
                        if (it.completed) return@any false
                        !grid.hasPath(
                            it.lastNode.x,
                            it.lastNode.y,
                            it.endNode.x,
                            it.endNode.y,
                            mutableSetOf()
                        )
                    }
                ) {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x, newPos.y] = 0
                    continue
                }

                val attempt = solve()
                if (attempt != null) {
                    return attempt
                } else {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x, newPos.y] = 0
                }
            }

            return null
        }

        return null
    }


    override fun getColors() = uniqueColors

    override fun getGrid() = grid
    override fun getGridPixelSize(): Int {
        return gridPxSize
    }
    override fun getFlows() = flows
    override fun cancel() {
        cancel = true
    }

}