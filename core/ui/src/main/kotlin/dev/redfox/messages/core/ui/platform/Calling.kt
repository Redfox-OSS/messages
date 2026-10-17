package dev.redfox.messages.core.ui.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Returns a "call this number" action. The first call asks for the phone permission; once
 * granted, calls go straight out (ACTION_CALL). If it's declined, the dialler opens with the
 * number filled in instead (ACTION_DIAL, needs no permission), so calling always works.
 */
@Composable
fun rememberCaller(): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { number -> if (granted) call(context, number) else dial(context, number) }
        pending = null
    }
    return remember(context) {
        { number ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                call(context, number)
            } else {
                pending = number
                permission.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }
}

private fun call(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_CALL, "tel:$number".toUri()))
    } catch (e: ActivityNotFoundException) {
        dial(context, number)
    } catch (e: SecurityException) {
        dial(context, number)
    }
}

private fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri())) }
}
