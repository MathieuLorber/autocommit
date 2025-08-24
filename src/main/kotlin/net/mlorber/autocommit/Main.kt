package net.mlorber.autocommit

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.time.ZonedDateTime

/** === CoreFoundation via interface mapping JNA (pas de Feature, pas de direct mapping) === */
interface CoreFoundation : Library {
    fun CFNotificationCenterGetDistributedCenter(): Pointer
    fun CFNotificationCenterAddObserver(
        center: Pointer,
        observer: Pointer?,               // opaque
        callBack: CFNotificationCallback, // **une seule** méthode publique nommée 'callback'
        name: Pointer?,                   // CFStringRef (null = toutes)
        obj: Pointer?,                    // filtre (null)
        suspensionBehavior: Int
    )
    fun CFStringCreateWithCString(alloc: Pointer?, cStr: String, encoding: Int): Pointer
    fun CFRunLoopRunInMode(mode: Pointer?, seconds: Double, returnAfterSourceHandled: Boolean): Int
    fun CFRelease(ref: Pointer)
}

/** Helper : charge la lib et récupère le CFStringRef global du runloop default mode. */
object CF {
    val LIB: CoreFoundation by lazy {
        Native.load("CoreFoundation", CoreFoundation::class.java)
    }
    val kCFRunLoopDefaultMode: Pointer by lazy {
        // déréférencer l'adresse du global CFStringRef
        NativeLibrary.getInstance("CoreFoundation")
            .getGlobalVariableAddress("kCFRunLoopDefaultMode")
            .getPointer(0)
    }
}

/** Callback JNA : DOIT avoir une seule méthode publique et s'appeler 'callback'. */
interface CFNotificationCallback : Callback {
    fun callback(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?)
}

private const val kCFStringEncodingUTF8 = 0x08000100
private const val CFNotificationSuspensionBehaviorDeliverImmediately = 4

class NotifCallback(
    private val lockName: Pointer,
    private val unlockName: Pointer
) : CFNotificationCallback {
    override fun callback(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?) {
        val label = when (name) {
            lockName   -> "LOCKED"
            unlockName -> "UNLOCKED"
            else       -> "UNKNOWN"
        }
        println("[macOS] Screen $label @ ${ZonedDateTime.now()}")
    }
}

fun main() {
    // JVM HotSpot uniquement : ajoutez -XstartOnFirstThread
    // En native-image : rien à faire, main est déjà sur le thread 1.

    val center = CF.LIB.CFNotificationCenterGetDistributedCenter()
    require(center != Pointer.NULL) { "Distributed Notification Center not available" }

    val lockName   = CF.LIB.CFStringCreateWithCString(null, "com.apple.screenIsLocked",   kCFStringEncodingUTF8)
    val unlockName = CF.LIB.CFStringCreateWithCString(null, "com.apple.screenIsUnlocked", kCFStringEncodingUTF8)

    val cb = NotifCallback(lockName, unlockName)
    CF.LIB.CFNotificationCenterAddObserver(center, null, cb, lockName,   null, CFNotificationSuspensionBehaviorDeliverImmediately)
    CF.LIB.CFNotificationCenterAddObserver(center, null, cb, unlockName, null, CFNotificationSuspensionBehaviorDeliverImmediately)

    println("Listening for macOS screen lock/unlock… (Ctrl+C to quit)")
    while (true) {
        CF.LIB.CFRunLoopRunInMode(CF.kCFRunLoopDefaultMode, 5.0, true)
    }
    // (si teardown) CF.LIB.CFRelease(lockName); CF.LIB.CFRelease(unlockName)
}