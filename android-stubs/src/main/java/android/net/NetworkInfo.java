package android.net;

@android.annotation.Stub
public class NetworkInfo {
    public enum State {
        CONNECTING,
        CONNECTED,
        SUSPENDED,
        DISCONNECTING,
        DISCONNECTED,
        UNKNOWN
    }

    public enum DetailedState {
        IDLE,
        SCANNING,
        CONNECTING,
        AUTHENTICATING,
        OBTAINING_IPADDR,
        CONNECTED,
        SUSPENDED,
        DISCONNECTING,
        DISCONNECTED,
        FAILED,
        BLOCKED,
        VERIFYING_POOR_LINK,
        CAPTIVE_PORTAL_CHECK
    }

    public boolean isConnected() {
        return true;
    }

    public boolean isConnectedOrConnecting() {
        return true;
    }

    public boolean isAvailable() {
        return true;
    }

    public State getState() {
        return State.CONNECTED;
    }

    public DetailedState getDetailedState() {
        return DetailedState.CONNECTED;
    }

    public int getType() {
        return ConnectivityManager.TYPE_WIFI;
    }

    public String getTypeName() {
        return "WIFI";
    }

    public String getSubtypeName() {
        return "";
    }

    public String getExtraInfo() {
        return "";
    }

    public String getReason() {
        return "";
    }
}
