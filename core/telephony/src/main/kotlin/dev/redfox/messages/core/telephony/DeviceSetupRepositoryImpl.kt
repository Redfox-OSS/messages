package dev.redfox.messages.core.telephony

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.domain.repository.DeviceSetupRepository
import dev.redfox.messages.core.model.DeviceSetupStatus
import javax.inject.Inject

internal class DeviceSetupRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceSetupRepository {

    override fun currentStatus(): DeviceSetupStatus {
        val roles = context.getSystemService(RoleManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return DeviceSetupStatus(
            isDefaultSmsApp = roles.isRoleAvailable(RoleManager.ROLE_SMS) && roles.isRoleHeld(RoleManager.ROLE_SMS),
            hasContactsPermission = granted(Manifest.permission.READ_CONTACTS),
            hasNotificationPermission = granted(Manifest.permission.POST_NOTIFICATIONS),
            isIgnoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            hasReadSmsPermission = granted(Manifest.permission.READ_SMS),
        )
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TelephonyModule {
    @Binds
    abstract fun deviceSetupRepository(impl: DeviceSetupRepositoryImpl): DeviceSetupRepository
}
