import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import it.fast4x.riplay.getAsyncImageLoader
import it.fast4x.riplay.ui.ThreeColumnsApp
import it.fast4x.riplay.ui.theme.DesktopTheme
import it.fast4x.riplay.commonutils.initializeEnvironment
import org.jetbrains.compose.resources.painterResource
import riplay.composeapp.generated.resources.Res
import riplay.composeapp.generated.resources.yammbo_app_icon


@OptIn(ExperimentalCoilApi::class)
fun main() {
    runCatching {
        val logDir = java.io.File(System.getProperty("user.home"), ".yammbo-music")
        logDir.mkdirs()
        val logFile = java.io.File(logDir, "yammbo-music.log")
        val ps = java.io.PrintStream(java.io.FileOutputStream(logFile, true), true)
        System.setOut(ps)
        System.setErr(ps)
        println("==== Yammbo Music started ${java.time.LocalDateTime.now()} pid=${ProcessHandle.current().pid()} ====")
        println("user.dir=${System.getProperty("user.dir")}")
        println("compose.application.resources.dir=${System.getProperty("compose.application.resources.dir")}")
    }
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        System.err.println("UNCAUGHT in thread ${t.name}:")
        e.printStackTrace(System.err)
        System.err.flush()
    }
    initializeEnvironment()
    application {
        setSingletonImageLoaderFactory { context ->
            getAsyncImageLoader(context)
        }
        Window(
            icon = painterResource(Res.drawable.yammbo_app_icon),
            onCloseRequest = ::exitApplication,
            state = WindowState(
                placement = WindowPlacement.Maximized,
            ),
            title = "Yammbo Music",
            undecorated = true
        ) {
            DesktopTheme {
                ThreeColumnsApp()
            }
        }
    }
}