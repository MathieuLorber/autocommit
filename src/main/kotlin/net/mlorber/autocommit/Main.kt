package net.mlorber.autocommit

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.time.ZonedDateTime

/** ---- Direct mapping CoreFoundation (pas de Proxy/reflection) ---- */
object CF {
    init {
        // Lie directement les symboles du framework CoreFoundation
        Native.register("CoreFoundation")
    }

    // extern "C" CFNotificationCenterRef CFNotificationCenterGetDistributedCenter(void);
    @JvmStatic external fun CFNotificationCenterGetDistributedCenter(): Pointer

    // extern "C" void CFNotificationCenterAddObserver(CFNotificationCenterRef center, const void *observer,
    //     CFNotificationCallback callBack, CFStringRef name, const void *object, CFNotificationSuspensionBehavior behavior);
    @JvmStatic external fun CFNotificationCenterAddObserver(
        center: Pointer,
        observer: Pointer?,                 // opaque (renvoyé au callback)
        callBack: CFNotificationCallback,   // JNA Callback (une seule méthode `callback`)
        name: Pointer?,                     // CFStringRef (nullable = toutes)
        obj: Pointer?,                      // filtre (nullable)
        suspensionBehavior: Int
    )

    // extern "C" CFStringRef CFStringCreateWithCString(CFAllocatorRef alloc, const char *cStr, CFStringEncoding encoding);
    @JvmStatic external fun CFStringCreateWithCString(
        alloc: Pointer?, cStr: String, encoding: Int
    ): Pointer

    // extern "C" SInt32 CFRunLoopRunInMode(CFStringRef mode, CFTimeInterval seconds, Boolean returnAfterSourceHandled);
    @JvmStatic external fun CFRunLoopRunInMode(
        mode: Pointer?, seconds: Double, returnAfterSourceHandled: Boolean
    ): Int

    // extern "C" void CFRelease(CFTypeRef cf);
    @JvmStatic external fun CFRelease(ref: Pointer)

    /** Récupère l’adresse du global `kCFRunLoopDefaultMode` depuis la lib */
    val kCFRunLoopDefaultMode: Pointer by lazy {
        NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFRunLoopDefaultMode")
    }
}

/** Signature exacte demandée par JNA : une seule méthode publique nommée `callback`. */
interface CFNotificationCallback : Callback {
    fun callback(center: Pointer?, observer: Pointer?, name: Pointer?, obj: Pointer?, userInfo: Pointer?)
}

private const val kCFStringEncodingUTF8 = 0x08000100
private const val CFNotificationSuspensionBehaviorDeliverImmediately = 4

/** Implémentation nommée (évite les classes anonymes) */
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