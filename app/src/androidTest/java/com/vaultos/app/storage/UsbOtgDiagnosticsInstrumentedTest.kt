package com.vaultos.app.storage

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbOtgDiagnosticsInstrumentedTest {
    @Test
    fun reportsAndroidUsbHostSnapshotWithoutRequestingDeviceAccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = UsbOtgDiagnostics.attachedDevices(context)

        assertEquals(manager.deviceList.size, devices.size)
        assertEquals(devices.size, devices.map { it.deviceName }.distinct().size)
        assertTrue(devices.zipWithNext().all { (left, right) ->
            compareValuesBy(left, right, UsbOtgDevice::vendorId, UsbOtgDevice::productId, UsbOtgDevice::deviceName) <= 0
        })
        // The diagnostic is deliberately passive: it never calls requestPermission().
        assertTrue(devices.all { it.interfaceCount >= 0 })
        assertTrue(devices.all { device ->
            if (device.hasPermission) device.interfaces.size == device.interfaceCount
            else device.interfaces.isEmpty()
        })
    }
}
