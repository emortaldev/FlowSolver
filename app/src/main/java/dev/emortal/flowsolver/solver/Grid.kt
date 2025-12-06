package dev.emortal.flowsolver.solver

import Dirs
import Pos

class Grid(val sizeX: Int, val sizeY: Int) {
    val array = Array(sizeX) { ByteArray(sizeY) }

    operator fun get(x: Int, y: Int): Byte? {
        if (outOfBounds(x, y)) return null
        return array[x][y]
    }
    operator fun set(x: Int, y: Int, value: Byte) {
        array[x][y] = value
    }

    fun outOfBounds(x: Int, y: Int) = x !in 0 until sizeX || y !in 0 until sizeY

    fun emptyNeighbours(x: Int, y: Int): List<Dirs> = neighbours(x, y, 0)

    fun neighbours(x: Int, y: Int, colour: Byte): List<Dirs> {
        val possibleDirs = mutableListOf<Dirs>()

        for (value in Dirs.entries) {
            val newX = x + value.x
            val newY = y + value.y

            if (this[newX, newY] == colour) {
                possibleDirs.add(value)
            }
        }

        return possibleDirs
    }

    fun surroundedSquare(flowNum: Byte): Boolean {
        forEach { x, y, num ->
            if (num.toInt() != 0) return@forEach

            var i = 0
            for (value in Dirs.entries) {
                val newX = x + value.x
                val newY = y + value.y

                val gridValue = this[newX, newY]
                if (gridValue == flowNum) {
                    i++
                    if (i >= 3) return true
                }

            }
        }
        return false
    }

    fun isSurrounded(x: Int, y: Int, flowNum: Byte): Boolean {
        val north = this[x, y + 1] == flowNum
        val south = this[x, y - 1] == flowNum
        val west = this[x - 1, y] == flowNum
        val east = this[x + 1, y] == flowNum

        if (
            north && south && west ||
            north && south && east ||
            north && west  && east ||
            south && west  && east
        ) return true

        val northEast = this[x + 1, y + 1] == flowNum
        val northWest = this[x - 1, y + 1] == flowNum
        val southEast = this[x + 1, y - 1] == flowNum
        val southWest = this[x - 1, y - 1] == flowNum

        return north && northEast && east ||
                north && northWest && west ||
                south && southEast && east ||
                south && southWest && west
    }

    // Flood fill to see if two points could theoretically be connected
    fun hasPath(x: Int, y: Int, targetX: Int, targetY: Int, tried: MutableSet<Pos>): Boolean {
        for (dir in Dirs.entries) {
            val newX = x + dir.x
            val newY = y + dir.y

            if (newX == targetX && newY == targetY) return true

            val newPos = Pos(newX, newY)

            if (!tried.add(newPos)) continue

            if (this[x + dir.x, y + dir.y]?.toInt() != 0) continue
            if (hasPath(newX, newY, targetX, targetY, tried)) {
                return true
            }
        }

        return false
    }

    fun borderingFlow(x: Int, y: Int): Boolean {
        for (dir in Dirs.entries) {
            // includes out of bounds check due to null check (?.)
            if (this[x + dir.x, y + dir.y]?.toInt() != 0) return true
        }
        return false
    }

    fun onEdge(x: Int, y: Int): Boolean {
        for (dir in Dirs.entries) {
            if (outOfBounds(x + dir.x, y + dir.y)) return true
        }
        return false
    }

    inline fun forEach(action: (x: Int, y: Int, value: Byte) -> Unit) {
        array.forEachIndexed { x, ints ->
            ints.forEachIndexed { y, num ->
                action(x, y, num)
            }
        }
    }

}
