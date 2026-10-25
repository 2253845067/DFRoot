package df.root;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/** Applies the system (Monet) palette so the UI follows the same accent as KernelSU Manager. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
