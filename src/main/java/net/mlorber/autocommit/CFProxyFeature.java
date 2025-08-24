package net.mlorber.autocommit;

//import org.graalvm.nativeimage.hosted.Feature;
//import org.graalvm.nativeimage.hosted.DynamicProxyRegistry;
//import org.graalvm.nativeimage.hosted.AutomaticFeature;
//
///** Enregistre le proxy dynamique JNA pour CoreFoundation au moment du build native-image. */
//@AutomaticFeature
//public final class CFProxyFeature implements Feature {
//    @Override
//    public void duringSetup(DuringSetupAccess access) {
//        DynamicProxyRegistry registry = access.getDynamicProxyRegistry();
//        // IMPORTANT: ordre des interfaces = (votre interface, com.sun.jna.Library)
//        registry.addProxyClass(net.mlorber.autocommit.CoreFoundation.class,
//                com.sun.jna.Library.class);
//    }
//}