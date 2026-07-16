package androidx.preference;
import android.content.Context;
@android.annotation.Implemented
public class PreferenceManager {
    private Context context;
    public PreferenceManager(Context context) { this.context = context; }
    public PreferenceScreen createPreferenceScreen(Context context) {
        return new PreferenceScreen(context, null);
    }
}
