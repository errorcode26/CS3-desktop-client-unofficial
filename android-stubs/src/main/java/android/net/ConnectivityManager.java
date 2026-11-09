package android.net;

@android.annotation.Stub
public class ConnectivityManager {
    public static final int TYPE_MOBILE = 0;
    public static final int TYPE_WIFI = 1;
    public static final int TYPE_MOBILE_MMS = 2;
    public static final int TYPE_MOBILE_SUPL = 3;
    public static final int TYPE_MOBILE_DUN = 4;
    public static final int TYPE_MOBILE_HIPRI = 5;
    public static final int TYPE_WIMAX = 6;
    public static final int TYPE_BLUETOOTH = 7;
    public static final int TYPE_DUMMY = 8;
    public static final int TYPE_ETHERNET = 9;
    public static final int TYPE_VPN = 17;

    public NetworkInfo getActiveNetworkInfo() {
        return new NetworkInfo();
    }

    public NetworkInfo getNetworkInfo(int networkType) {
        return new NetworkInfo();
    }

    public NetworkInfo[] getAllNetworkInfo() {
        return new NetworkInfo[] { new NetworkInfo() };
    }

    public boolean isActiveNetworkMetered() {
        return false;
    }
}
