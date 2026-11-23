package com.vaultos.app.storage

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

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
    val hasPermission: Boolean
)

object UsbOtgDiagnostics {
    fun attachedDevices(context: Context): List<UsbOtgDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return manager.deviceList.values
            .sortedWith(compareBy<UsbDevice> { it.vendorId }.thenBy { it.productId }.thenBy { it.deviceName })
            .map { device ->
                UsbOtgDevice(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturerName = device.manufacturerName,
                    productName = device.productName,
                    deviceClass = device.deviceClass,
                    deviceSubclass = device.deviceSubclass,
                    interfaceCount = device.interfaceCount,
                    hasPermission = manager.hasPermission(device)
                )
            }
    }
}
