package net.mlorber.autocommit

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

// ---- CoreFoundation bindings (minimal) ----
interface CoreFoundation : Library {
    fun CFNotificationCenterGetDistributedCenter(): Pointer
    fun CFNotificationCenterAddObserver(
        center: Pointer,
        observer: Pointer?,           // opaque, renvoyé tel quel au callback
        callBack: CFNotificationCallback,
        name: Pointer?,               // CFStringRef ou null
        obj: Pointer?,                // CFStringRef (filtre)
        suspensionBehavior: Int
    )
    fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer
    fun CFRunLoopRun()
    fun CFRelease(ref: Pointer)
}

fun cf(): CoreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)

// Callback signature CoreFoundation
interface CFNotificationCallback : Callback {
    fun invoke(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?)
}

private const val kCFStringEncodingUTF8 = 0x08000100
private fun cfString(s: String): Pointer = cf().CFStringCreateWithCString(null, s, kCFStringEncodingUTF8)

private const val CFNotificationSuspensionBehaviorDeliverImmediately = 4

fun main() {
    val CF = cf()
    val center = CF.CFNotificationCenterGetDistributedCenter()

    val lockName   = cfString("com.apple.screenIsLocked")
    val unlockName = cfString("com.apple.screenIsUnlocked")

    val onLock = object : CFNotificationCallback {
        override fun invoke(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?) {
            println("[macOS] Screen LOCKED @ ${java.time.ZonedDateTime.now()}")
        }
    }
    val onUnlock = object : CFNotificationCallback {
        override fun invoke(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?) {
            println("[macOS] Screen UNLOCKED @ ${java.time.ZonedDateTime.now()}")
        }
    }

    CF.CFNotificationCenterAddObserver(
        center, null, onLock, lockName, null, CFNotificationSuspensionBehaviorDeliverImmediately
    )
    CF.CFNotificationCenterAddObserver(
        center, null, onUnlock, unlockName, null, CFNotificationSuspensionBehaviorDeliverImmediately
    )

    println("Listening for macOS screen lock/unlock… (Ctrl+C to quit)")
    CF.CFRunLoopRun()

    // (Jamais atteint ici, mais si vous faites un teardown, pensez à:)
    // CF.CFRelease(lockName); CF.CFRelease(unlockName)
}