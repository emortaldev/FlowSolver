package dev.emortal.flowsolver.solver

import Dirs
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

private val GRID_START_COLORS = arrayOf(
    Color.hsv(120f, 0.422f, 0.427f).toArgb(),
    Color.hsv(0f, 0.504f, 0.498f).toArgb(),
    Color.hsv(224.5f, 0.457f, 0.498f).toArgb(),
)

class FlowSolverArray(val slow: Boolean = false) : FlowSolverInterface {

    private var gridSizeX = 5
    private var gridSizeY = 5

    private val uniqueColors = mutableSetOf(Color.Black.toArgb())

    private lateinit var grid: Array<ByteArray>

    private val flows = mutableListOf<Flow>()

    private var cancel = false

    fun readable(grid: Array<ByteArray>)
            = grid.joinToString(separator = "\n") { it.joinToString("", transform = { if (it.toInt() == 0) "." else it.toString() }) }

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

        val gridPixelSize = nextGridPixel - gridStartX
        val gridSizeX = (bitmap.width - (gridStartX * 2)) / gridPixelSize

        this.gridSizeX = gridSizeX
        this.gridSizeY = gridSizeX

        // Assumes grid X and Y are the same
        grid = Array(gridSizeX) { ByteArray(gridSizeX) }

        // Detect flows
        for (y in 0 until gridSizeX) {
            val pixelY = y * gridPixelSize + gridStartY + (gridPixelSize / 2)
            for (x in 0 until gridSizeX) {
                val pixelX = x * gridPixelSize + gridStartX + (gridPixelSize / 2)

                val rgb = bitmap[pixelX, pixelY]
                val color = Color(rgb)
                if (color.red + color.green + color.blue < 0.47) {
                    // probably background
                    continue
                }

                if (!uniqueColors.contains(rgb)) {
                    uniqueColors.add(rgb)
                    flows.add(Flow((uniqueColors.indexOf(rgb) + 1).toByte(), Pos(y, x)))
                }

                grid[y][x] = (uniqueColors.indexOf(rgb) + 1).toByte()

                flows[uniqueColors.indexOf(rgb) - 1].endNode = Pos(y, x)


            }
        }

        Log.i("FlowSolver", "Solving flow!")

        val list = flows.sortedBy {
            var weight = 0

            if (isOnEdge(it.startNode.x, it.startNode.y)) weight += 1
            if (isBorderingFlow(it.startNode.x, it.startNode.y)) weight += 1
            if (isOnEdge(it.endNode.x, it.endNode.y)) weight += 1
            if (isBorderingFlow(it.endNode.x, it.endNode.y)) weight += 1
            Log.i("FlowSolver", "Num: ${it.flowNum} got weight ${weight}")
            return@sortedBy weight
        }.reversed().toMutableList()
        Log.i("FlowSolver", list.toString())
        flows.clear()
        flows.addAll(list)

        return flows.isNotEmpty()
    }

    override fun doSolve(): CompletableFuture<Array<ByteArray>?> {
        val future = CompletableFuture.supplyAsync({
            val result = solve()

            if (result == null) {
                Log.e("FlowSolver", "Solving failed!")
                return@supplyAsync null
            }

            Log.i("FlowSolver", "Solving success!")
            Log.i("FlowSolver", readable(grid))

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
            val nei = neighbours(endOfFlow.x, endOfFlow.y).sortedBy {
                var weight = 0
                weight += flow.endNode.distance(it.x, it.y)

                if (!isOnEdge(endOfFlow.x + it.x, endOfFlow.y + it.y)) {
                    weight += 500
                }
                if (!isBorderingFlow(endOfFlow.x + it.x, endOfFlow.y + it.y)) {
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
                grid[newPos.x][newPos.y] = flow.flowNum

                if (flows.any { isSurrounded(it.lastNode.x, it.lastNode.y, it.flowNum) }) {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x][newPos.y] = 0
                    continue
                }
                if (surroundedSquare(flow.flowNum)) {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x][newPos.y] = 0
                    continue
                }
                if (
                    flows.any {
                        if (it.flowNum == flow.flowNum) return@any false
                        if (it.completed) return@any false
                        !hasPath(it.lastNode.x, it.lastNode.y, it.endNode.x, it.endNode.y, mutableSetOf())
                    }
                ) {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x][newPos.y] = 0
                    continue
                }

                val attempt = solve()
                if (attempt != null) {
                    return attempt
                } else {
                    flow.lastNode = endOfFlow
                    flow.completed = false
                    grid[newPos.x][newPos.y] = 0
                }
            }

            return null
        }

        return null
    }


    fun neighbours(x: Int, y: Int): List<Dirs> {
        val possibleDirs = mutableListOf<Dirs>()

        for (value in Dirs.entries) {
            val newX = x + value.x
            val newY = y + value.y

            if (newX !in 0 until gridSizeX || newY !in 0 until gridSizeY) continue

            if (grid[newX][newY].toInt() == 0) {
                possibleDirs.add(value)
            }
        }

        return possibleDirs
    }

    fun isSurrounded(x: Int, y: Int, flowNum: Byte): Boolean {
        val north = gridSafe(x, y + 1) == flowNum
        val south = gridSafe(x, y - 1) == flowNum
        val west = gridSafe(x - 1, y) == flowNum
        val east = gridSafe(x + 1, y) == flowNum

        if (
            north && south && west ||
            north && south && east ||
            north && west  && east ||
            south && west  && east
        ) return true

        val northEast = gridSafe(x + 1, y + 1) == flowNum
        val northWest = gridSafe(x - 1, y + 1) == flowNum
        val southEast = gridSafe(x + 1, y - 1) == flowNum
        val southWest = gridSafe(x - 1, y - 1) == flowNum

        return north && northEast && east ||
                north && northWest && west ||
                south && southEast && east ||
                south && southWest && west
    }

    fun surroundedSquare(flowNum: Byte): Boolean {
        grid.forEachIndexed { x, it ->
            it.forEachIndexed { y, num ->
                if (num.toInt() != 0) return@forEachIndexed

                var i = 0
                for (value in Dirs.entries) {
                    val newX = x + value.x
                    val newY = y + value.y

                    val gridValue = gridSafe(newX, newY)
                    if (gridValue == flowNum) {
                        i++
                        if (i >= 3) return true
                    }

                }
            }
        }
        return false
    }

    fun outOfBounds(x: Int, y: Int) = x !in 0 until gridSizeX || y !in 0 until gridSizeY
    fun gridSafe(x: Int, y: Int) = if (outOfBounds(x, y)) null else grid[x][y]

    // Flood fill to see if two points could theoretically be connected
    fun hasPath(x: Int, y: Int, targetX: Int, targetY: Int, tried: MutableSet<Pos>): Boolean {
        for (dir in Dirs.entries) {
            val newX = x + dir.x
            val newY = y + dir.y

            if (newX == targetX && newY == targetY) return true

            val newPos = Pos(newX, newY)

            if (!tried.add(newPos)) continue

            if (gridSafe(x + dir.x, y + dir.y)?.toInt() != 0) continue
            if (hasPath(newX, newY, targetX, targetY, tried)) {
                return true
            }
        }

        return false
    }

    fun isBorderingFlow(x: Int, y: Int): Boolean {
        for (dir in Dirs.entries) {
            // includes out of bounds check due to null check (?.)
            if (gridSafe(x + dir.x, y + dir.y)?.toInt() != 0) return true
        }
        return false
    }

    fun isOnEdge(x: Int, y: Int): Boolean {
        for (dir in Dirs.entries) {
            if (outOfBounds(x + dir.x, y + dir.y)) return true
        }
        return false
    }

    override fun getGridSizeX() = gridSizeX
    override fun getGridSizeY() = gridSizeY

    override fun getColors() = uniqueColors

    override fun getGrid() = grid
    override fun getVisualisableGrid() = grid
    override fun getFlows() = flows
    override fun cancel() {
        cancel = true
    }

}