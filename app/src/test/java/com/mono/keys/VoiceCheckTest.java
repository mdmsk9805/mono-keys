package com.mono.keys;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
public class VoiceCheckTest {
    @Test public void bnMatching() {
        assertTrue(VoiceCheck.hasBn(Arrays.asList("en-US", "bn-BD")));
        assertTrue(VoiceCheck.hasBn(Arrays.asList("bn")));
        assertFalse(VoiceCheck.hasBn(Arrays.asList("en-US", "hi-IN")));
        assertFalse(VoiceCheck.hasBn(null));
    }

    @Test public void reportNeverThrowsAndStaysHonest() {
        Context ctx = ApplicationProvider.getApplicationContext();
        String r = VoiceCheck.report(ctx);
        assertTrue(r.contains("Bangla offline recognition: unknown"));
        assertTrue(r.contains("No microphone, recording, network or download was used."));
        AtomicReference<String> got = new AtomicReference<>();
        VoiceCheck.checkBangla(ctx, got::set); // must not throw, must not claim certainty on failure
        assertTrue(got.get() == null || got.get().startsWith("Bangla offline"));
    }

    @Test public void noAudioPermissionOrInternetDeclared() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        String[] perms = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        if (perms != null) for (String p : perms) {
            assertNotEquals("android.permission.RECORD_AUDIO", p);
            assertNotEquals("android.permission.INTERNET", p);
        }
    }
}
