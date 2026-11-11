package app.organicmaps.sdk;

import androidx.annotation.NonNull;

public enum Router
{
  Vehicle(0),
  Pedestrian(1),
  Bicycle(2),
  Transit(3),
  Ruler(4);

  Router(int type)
  {
    this.type = type;
  }

  public static void set(@NonNull Router routerType)
  {
    nativeSet(routerType.type);
  }

  /// Atlas: routes for car, foot and bicycle come from a server instead of the map files.
  /// An empty address means the default public server.
  public static void setOnline(boolean enabled, @NonNull String serverUrl)
  {
    nativeSetOnline(enabled, serverUrl);
  }

  public static boolean isOnline()
  {
    return nativeIsOnline();
  }

  public static Router get()
  {
    return valueOf(nativeGet());
  }

  public static Router getLastUsed()
  {
    return valueOf(nativeGetLastUsed());
  }

  public static Router getBest(double srcLat, double srcLon, double dstLat, double dstLon)
  {
    return Router.values()[nativeGetBest(srcLat, srcLon, dstLat, dstLon)];
  }

  public static Router valueOf(int type)
  {
    return Router.values()[type];
  }

  private final int type;

  private static native void nativeSet(int routerType);

  private static native void nativeSetOnline(boolean enabled, String serverUrl);

  private static native boolean nativeIsOnline();

  private static native int nativeGet();

  private static native int nativeGetLastUsed();

  private static native int nativeGetBest(double srcLat, double srcLon, double dstLat, double dstLon);
}
