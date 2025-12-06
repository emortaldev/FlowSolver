package dev.emortal.flowsolver

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.emortal.flowsolver.solver.FlowSolverArray
import dev.emortal.flowsolver.solver.FlowSolverInterface
import dev.emortal.flowsolver.ui.theme.FlowSolverTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlowSolverTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FlowSolver()
                }
            }
        }
    }
}

var SOLVING_TEXT = "Solving flow..."

val COROUTINE_SCOPE = CoroutineScope(Dispatchers.IO)

@Composable
fun FlowSolver(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var flowStatusText by remember { mutableStateOf("") }

    // Buttons / Switches
    var visualize by remember { mutableStateOf(true) }
    var slow by remember { mutableStateOf(false) }

    // Solver data
    var gridStates: Array<ByteArray>? by remember { mutableStateOf(null) }
    var flowColors by remember { mutableStateOf(listOf(Color.Black)) }
    var startTimestamp by remember { mutableLongStateOf(0L) }
    var flowSolver: FlowSolverInterface? by remember { mutableStateOf(null) }

    val mainHandler by remember { mutableStateOf(Handler(Looper.getMainLooper())) }
    var timerRunnable: Runnable? by remember { mutableStateOf(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (timerRunnable != null) mainHandler.removeCallbacks(timerRunnable!!)

            val handler = CoroutineExceptionHandler { _, exception ->
                Log.e("CoroutineException", "CoroutineExceptionHandler got $exception")
            }

            COROUTINE_SCOPE.launch(handler) {
                flowSolver?.cancel()
                flowSolver = FlowSolverArray(slow)

                val success = flowSolver!!.detect(context, uri)

                if (!success) {
                    gridStates = null
                    flowStatusText = "Could not find flows in screenshot"
                    return@launch
                }

                val timeRunnable = object : Runnable {
                    override fun run() {
                        val millis = (System.nanoTime() - startTimestamp) / 1_000_000
                        flowStatusText = "$SOLVING_TEXT (${millis}ms)"

                        mainHandler.postDelayed(this, 50)
                    }
                }
                timerRunnable = timeRunnable
                mainHandler.post(timerRunnable!!)

                startTimestamp = System.nanoTime()
                gridStates = null
                if (visualize) gridStates = flowSolver!!.getVisualisableGrid()

                flowColors = listOf(Color.Black) + flowSolver!!.getColors().map { Color(it) }
                Log.i("Flow colors", flowColors.toString())

                val result = flowSolver!!.doSolve()

                result.thenAccept { result ->
                    mainHandler.removeCallbacks(timeRunnable)

                    if (result == null) {
                        flowStatusText = "Failed to solve flow"
//                        gridStates = null
                        return@thenAccept
                    }

                    flowStatusText = "Took: ${(System.nanoTime() - startTimestamp) / 1_000_000}ms"
                    gridStates = result

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vms = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        val vibrator = vms.defaultVibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(25, 255))
                    }
                }
            }

        }
        Log.i("FilePicker", uri.toString())
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Flow Solver", modifier = modifier.padding(top = 50.dp, bottom = 20.dp), fontSize = 24.sp, fontFamily = FontFamily.Monospace)

        Row(modifier) {
            Button(
                onClick = {
                    launcher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = modifier.padding(12.dp)
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Solve Flow Icon",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))

                Text("Solve Flow", fontSize = 24.sp)
            }
        }


        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = modifier.width(200.dp).padding(horizontal = 20.dp)
            ) {
                Text("Slow")
                Switch(checked = slow, onCheckedChange = {slow = !slow})
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.width(200.dp).padding(horizontal = 20.dp)
            ) {
                Text("Visualize")
                Switch(checked = visualize, onCheckedChange = {visualize = !visualize})
            }
        }


        HorizontalDivider(modifier = modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (flowStatusText.startsWith(SOLVING_TEXT)) {
                CircularProgressIndicator()
            }

            Text(flowStatusText, modifier = modifier.padding(12.dp))
        }

        gridStates?.forEachIndexed { _, ints ->
            Row {
                ints.forEachIndexed { _, num ->
                    if (num >= flowColors.size) {
                        Log.e("MainActivity", "Num exceeded flow colors. This is a problem!")
                    }

                    Box(modifier = modifier
                        .width(40.dp)
                        .height(40.dp)
                        .background(color = flowColors[num.toInt().coerceAtMost(flowColors.size - 1)])) {
                    }
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.Bottom) {
        Text("© 2025 emortal", modifier = Modifier
            .align(Alignment.Bottom)
            .padding(start = 5.dp, bottom = 20.dp)
            .alpha(0.3f))
    }
}

@Preview(showBackground = true)
@Composable
fun FlowSolverPreview() {
    FlowSolverTheme {
        FlowSolver()
    }
}