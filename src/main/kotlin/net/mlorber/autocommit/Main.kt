package net.mlorber.autocommit

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

object CF : Library {
    init { Native.register("CoreFoundation") } // charge la lib et mappe directement

    @JvmStatic external fun CFNotificationCenterGetDistributedCenter(): Pointer
    @JvmStatic external fun CFNotificationCenterAddObserver(
        center: Pointer, observer: Pointer?, callBack: CFNotificationCallback,
        name: Pointer?, obj: Pointer?, suspensionBehavior: Int
    )
    @JvmStatic external fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer
    @JvmStatic external fun CFRunLoopRunInMode(mode: Pointer, seconds: Double, returnAfterSourceHandled: Boolean): Int
    @JvmStatic external fun CFRelease(ref: Pointer)
}

//// ---- CoreFoundation bindings (minimal) ----
//interface CoreFoundation : Library {
//    fun CFNotificationCenterGetDistributedCenter(): Pointer
//    fun CFNotificationCenterAddObserver(
//        center: Pointer,
//        observer: Pointer?,
//        callBack: CFNotificationCallback,
//        name: Pointer?,     // CFStringRef
//        obj: Pointer?,      // CFStringRef (filtre)
//        suspensionBehavior: Int
//    )
//    fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer
//    fun CFRunLoopGetCurrent(): Pointer
//    fun CFRunLoopRunInMode(mode: Pointer, seconds: Double, returnAfterSourceHandled: Boolean): Int
//    fun CFRelease(ref: Pointer)
//}

//fun cf(): CoreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)

// Callback signature
interface CFNotificationCallback : Callback {
    fun callback(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?)
}

private const val kCFStringEncodingUTF8 = 0x08000100
private fun cfString(s: String): Pointer = CF.CFStringCreateWithCString(null, s, kCFStringEncodingUTF8)

// CFRunLoop constants
private fun cfStr(s: String) = cfString(s)
private val kCFRunLoopDefaultMode: Pointer by lazy { cfStr("kCFRunLoopDefaultMode") }

private const val CFNotificationSuspensionBehaviorDeliverImmediately = 4

class NotifCallback(
    private val lockName: com.sun.jna.Pointer,
    private val unlockName: com.sun.jna.Pointer
) : CFNotificationCallback {
    override fun callback(center: com.sun.jna.Pointer?, observer: com.sun.jna.Pointer?,
                          name: com.sun.jna.Pointer?, obj: com.sun.jna.Pointer?, userInfo: com.sun.jna.Pointer?) {
        val label = when (name) {
            lockName   -> "LOCKED"
            unlockName -> "UNLOCKED"
            else       -> "UNKNOWN"
        }
        println("[macOS] Screen $label @ ${java.time.ZonedDateTime.now()}")
    }
}

fun main() {
    // IMPORTANT: lancez avec -XstartOnFirstThread (voir plus bas)
    val center = CF.CFNotificationCenterGetDistributedCenter()

    val lockName   = cfString("com.apple.screenIsLocked")
    val unlockName = cfString("com.apple.screenIsUnlocked")

    val cb = NotifCallback(lockName, unlockName)

    CF.CFNotificationCenterAddObserver(center, null, cb, lockName,   null, CFNotificationSuspensionBehaviorDeliverImmediately)
    CF.CFNotificationCenterAddObserver(center, null, cb, unlockName, null, CFNotificationSuspensionBehaviorDeliverImmediately)

    println("Listening for macOS screen lock/unlock… (Ctrl+C to quit)")

    // Boucle de runloop "pompe" le mode par défaut et ne termine pas
    while (true) {
        // Attend et traite les sources max 5s, puis revient — on boucle
        CF.CFRunLoopRunInMode(kCFRunLoopDefaultMode, 5.0, true)
    }

    // (jamais atteint) CF.CFRelease(lockName); CF.CFRelease(unlockName)
}