package net.mlorber.autocommit;


import com.sun.jna.Pointer;
import java.time.ZonedDateTime;

public final class NotifCallback implements CFNotificationCallback {
    private final Pointer lockName;
    private final Pointer unlockName;

    public NotifCallback(Pointer lockName, Pointer unlockName) {
        this.lockName = lockName;
        this.unlockName = unlockName;
    }

    @Override
    public void callback(Pointer center, Pointer observer, Pointer name, Pointer obj, Pointer userInfo) {
        String label = name == lockName ? "LOCKED" :
                (name == unlockName ? "UNLOCKED" : "UNKNOWN");
        System.out.println("[macOS] Screen " + label + " @ " + ZonedDateTime.now());
    }
}