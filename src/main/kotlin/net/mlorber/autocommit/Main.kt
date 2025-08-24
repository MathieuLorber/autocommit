package net.mlorber.autocommit

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.time.ZonedDateTime

interface CoreFoundation : Library {
    fun CFNotificationCenterGetDistributedCenter(): Pointer
    fun CFNotificationCenterAddObserver(
        center: Pointer,
        observer: Pointer?,
        callBack: CFNotificationCallback, // <- interface Java
        name: Pointer?,
        obj: Pointer?,
        suspensionBehavior: Int
    )
    fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer
    fun CFRunLoopRunInMode(mode: Pointer?, seconds: Double, returnAfterSourceHandled: Boolean): Int
    fun CFRelease(ref: Pointer)
}

object CF {
    val LIB: CoreFoundation by lazy {
        Native.load("CoreFoundation", CoreFoundation::class.java)
    }
    val kCFRunLoopDefaultMode: Pointer by lazy {
        NativeLibrary.getInstance("CoreFoundation")
            .getGlobalVariableAddress("kCFRunLoopDefaultMode")
            .getPointer(0) // déréférencement du CFStringRef global
    }
}

private const val kCFStringEncodingUTF8 = 0x08000100
private const val CFNotificationSuspensionBehaviorDeliverImmediately = 4

fun main() {
    val center = CF.LIB.CFNotificationCenterGetDistributedCenter()
    require(center != Pointer.NULL) { "Distributed Notification Center not available" }

    val lockName   = CF.LIB.CFStringCreateWithCString(null, "com.apple.screenIsLocked",   kCFStringEncodingUTF8)
    val unlockName = CF.LIB.CFStringCreateWithCString(null, "com.apple.screenIsUnlocked", kCFStringEncodingUTF8)

    val cb = NotifCallback(lockName, unlockName) // <- classe Java

    CF.LIB.CFNotificationCenterAddObserver(center, null, cb, lockName,   null, CFNotificationSuspensionBehaviorDeliverImmediately)
    CF.LIB.CFNotificationCenterAddObserver(center, null, cb, unlockName, null, CFNotificationSuspensionBehaviorDeliverImmediately)

    println("Listening for macOS screen lock/unlock… (Ctrl+C to quit)")
    while (true) {
        CF.LIB.CFRunLoopRunInMode(CF.kCFRunLoopDefaultMode, 5.0, true)
    }
}