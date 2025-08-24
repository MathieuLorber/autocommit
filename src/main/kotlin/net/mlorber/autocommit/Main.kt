package net.mlorber.autocommit

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.time.ZonedDateTime
object CF {
    init {
        // ✅ passez explicitement la classe au lieu de Native.register("CoreFoundation")
        com.sun.jna.Native.register(CF::class.java, "CoreFoundation")
    }

    // Méthodes direct mapping (inchangées)
    @JvmStatic external fun CFNotificationCenterGetDistributedCenter(): com.sun.jna.Pointer
    @JvmStatic external fun CFNotificationCenterAddObserver(
        center: com.sun.jna.Pointer,
        observer: com.sun.jna.Pointer?,
        callBack: CFNotificationCallback,
        name: com.sun.jna.Pointer?,
        obj: com.sun.jna.Pointer?,
        suspensionBehavior: Int
    )
    @JvmStatic external fun CFStringCreateWithCString(
        alloc: com.sun.jna.Pointer?, cStr: String, encoding: Int
    ): com.sun.jna.Pointer
    @JvmStatic external fun CFRunLoopRunInMode(
        mode: com.sun.jna.Pointer?, seconds: Double, returnAfterSourceHandled: Boolean
    ): Int
    @JvmStatic external fun CFRelease(ref: com.sun.jna.Pointer)

    // ⚠️ Déréférencer le global pour obtenir le CFStringRef effectif
    val kCFRunLoopDefaultMode: com.sun.jna.Pointer by lazy {
        val addr = com.sun.jna.NativeLibrary
            .getInstance("CoreFoundation")
            .getGlobalVariableAddress("kCFRunLoopDefaultMode")
        addr.getPointer(0) // ✅ CFStringRef
    }
}
interface CFNotificationCallback : com.sun.jna.Callback {
    fun callback(center: com.sun.jna.Pointer?, observer: com.sun.jna.Pointer?,
                 name: com.sun.jna.Pointer?, obj: com.sun.jna.Pointer?, userInfo: com.sun.jna.Pointer?)
}

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

private const val kCFStringEncodingUTF8 = 0x08000100
private const val CFNotificationSuspensionBehaviorDeliverImmediately = 4


fun main() {
    // Sur **JVM HotSpot**, lancez avec -XstartOnFirstThread
    // En **native-image**, rien à faire : main tourne déjà sur le thread 1.

    val center = CF.CFNotificationCenterGetDistributedCenter()
    require(center != Pointer.NULL) { "Distributed notification center not available" }

    val lockName   = CF.CFStringCreateWithCString(null, "com.apple.screenIsLocked",   kCFStringEncodingUTF8)
    val unlockName = CF.CFStringCreateWithCString(null, "com.apple.screenIsUnlocked", kCFStringEncodingUTF8)

    val cb = NotifCallback(lockName, unlockName)

    CF.CFNotificationCenterAddObserver(center, null, cb, lockName,   null, CFNotificationSuspensionBehaviorDeliverImmediately)
    CF.CFNotificationCenterAddObserver(center, null, cb, unlockName, null, CFNotificationSuspensionBehaviorDeliverImmediately)

    println("Listening for macOS screen lock/unlock… (Ctrl+C to quit)")

    // Runloop: mode par défaut (vrai symbole global), “pompe” toutes les 5s
    while (true) {
        CF.CFRunLoopRunInMode(CF.kCFRunLoopDefaultMode, 5.0, true)
    }

    // (Jamais atteint; si vous ajoutez un shutdown hook, pensez à:)
    // CF.CFRelease(lockName); CF.CFRelease(unlockName)
}