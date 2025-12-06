import kotlin.math.abs

data class Pos(val x: Int, val y: Int) {

    fun equals(x: Int, y: Int) = this.x == x && this.y == y

    fun add(x: Int, y: Int) = Pos(this.x + x, this.y + y)

    fun distance(x: Int, y: Int) = abs(this.x - x) + abs(this.y - y)
    fun distance(other: Pos) = abs(this.x - other.x) + abs(this.y - other.y)

}