package com.vaultos.app.storage

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/** USB interface descriptors are exposed only after the user grants device permission. */
data class UsbOtgInterface(
    val id: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpointCount: Int
)

/** Read-only snapshot of USB devices currently exposed by Android's USB Host API. */
data class UsbOtgDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val interfaceCount: Int,
    val hasPermission: Boolean,
    val interfaces: List<UsbOtgInterface>
)

object UsbOtgDiagnostics {
    /** Reports the Android package-manager USB Host feature independently from device enumeration. */
    fun hostSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_HOST)

    fun attachedDevices(context: Context): List<UsbOtgDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return manager.deviceList.values
            .sortedWith(compareBy<UsbDevice> { it.vendorId }.thenBy { it.productId }.thenBy { it.deviceName })
            .map { device ->
                val granted = manager.hasPermission(device)
                val interfaces = if (granted) {
                    (0 until device.interfaceCount).map { index ->
                        val descriptor = device.getInterface(index)
                        UsbOtgInterface(
                            id = descriptor.id,
                            interfaceClass = descriptor.interfaceClass,
                            interfaceSubclass = descriptor.interfaceSubclass,
                            interfaceProtocol = descriptor.interfaceProtocol,
                            endpointCount = descriptor.endpointCount
                        )
                    }
                } else emptyList()
                UsbOtgDevice(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturerName = device.manufacturerName,
                    productName = device.productName,
                    deviceClass = device.deviceClass,
                    deviceSubclass = device.deviceSubclass,
                    interfaceCount = device.interfaceCount,
                    hasPermission = granted,
                    interfaces = interfaces
                )
            }
    }
}
