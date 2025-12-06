package dev.emortal.flowsolver.solver

import Pos

data class Flow(val flowNum: Byte, val startNode: Pos, var endNode: Pos = Pos(0, 0), val path: MutableList<Pos> = mutableListOf(startNode), var completed: Boolean = false) {
//    var completed = false
    var lastNode: Pos = startNode

    var end = path.lastOrNull() ?: startNode

    override fun toString(): String {
        return "Flow(flowNum=$flowNum, start=$startNode, end=$endNode)"
    }


}