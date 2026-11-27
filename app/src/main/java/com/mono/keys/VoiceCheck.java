package com.mono.keys;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

/**
 * Read-only voice diagnostic. It never starts listening, never records, needs no
 * microphone permission and no network, and never triggers a model download.
 * Anything it cannot know is reported as "unknown", not guessed.
 */
final class VoiceCheck {
    private VoiceCheck() {}

    static String report(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Android API ").append(Build.VERSION.SDK_INT).append('\n');
        try {
            sb.append("Recognition available: ").append(SpeechRecognizer.isRecognitionAvailable(ctx)).append('\n');
        } catch (Throwable t) { sb.append("Recognition available: unknown\n"); }
        boolean onDevice = false;
        if (Build.VERSION.SDK_INT >= 31) {
            try { onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx); } catch (Throwable ignored) {}
            sb.append("On-device recognition available: ").append(onDevice).append('\n');
        } else sb.append("On-device recognition available: not reported before Android 12\n");
        sb.append("Recognition services:\n");
        try {
            List<ResolveInfo> svcs = ctx.getPackageManager().queryIntentServices(
                    new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            if (svcs.isEmpty()) sb.append("  none visible\n");
            for (ResolveInfo ri : svcs) sb.append("  ").append(ri.serviceInfo.packageName).append('\n');
        } catch (Throwable t) { sb.append("  unknown\n"); }
        sb.append("Voice typing / keyboards installed:\n");
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            for (InputMethodInfo i : imm.getInputMethodList()) sb.append("  ").append(i.getPackageName()).append('\n');
        } catch (Throwable t) { sb.append("  unknown\n"); }
        sb.append("Bangla offline recognition: unknown (not checked here)\n");
        sb.append("No microphone, recording, network or download was used.");
        return sb.toString();
    }

    /** Optional API 33 language check. Safe: any failure leaves the answer "unknown". */
    static void checkBangla(Context ctx, java.util.function.Consumer<String> done) {
        if (Build.VERSION.SDK_INT < 33) { done.accept("Bangla offline: unknown (needs Android 13)"); return; }
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) { done.accept("Bangla offline: no on-device recognizer"); return; }
            final SpeechRecognizer sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
            Intent in = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            sr.checkRecognitionSupport(in, ctx.getMainExecutor(), new android.speech.RecognitionSupportCallback() {
                @Override public void onSupportResult(android.speech.RecognitionSupport s) {
                    boolean inst = hasBn(s.getInstalledOnDeviceLanguages()), sup = hasBn(s.getSupportedOnDeviceLanguages());
                    done.accept("Bangla offline: installed=" + inst + ", supported=" + sup);
                    sr.destroy();
                }
                @Override public void onError(int e) { done.accept("Bangla offline: unknown (error " + e + ")"); sr.destroy(); }
            });
        } catch (Throwable t) {
            done.accept("Bangla offline: unknown (" + t.getClass().getSimpleName() + ")");
        }
    }

    static boolean hasBn(List<String> langs) {
        if (langs == null) return false;
        for (String l : langs) if (l != null && (l.equals("bn") || l.startsWith("bn-") || l.startsWith("bn_"))) return true;
        return false;
    }
}
