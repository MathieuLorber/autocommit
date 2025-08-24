package net.mlorber.autocommit;


import com.sun.jna.Callback;
import com.sun.jna.Pointer;

/** JNA exige une seule méthode publique, idéalement nommée 'callback'. */
public interface CFNotificationCallback extends Callback {
    void callback(Pointer center, Pointer observer, Pointer name, Pointer obj, Pointer userInfo);
}