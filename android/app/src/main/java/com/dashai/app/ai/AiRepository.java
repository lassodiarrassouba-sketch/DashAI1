package com.dashai.app.ai;

import android.content.Context;

import java.util.Locale;

public final class AiRepository {
    private final LocalAnswerEngine localAnswerEngine = new LocalAnswerEngine();
    private final RemoteAiClient remoteAiClient = new RemoteAiClient();

    public String answer(Context context, String question, boolean onlineEnabled, String endpoint) {
        return answer(context, question, question, onlineEnabled, endpoint, "", false, isDebugBuild(context));
    }

    public String answer(Context context, String question, boolean onlineEnabled, String endpoint, String history) {
        return answer(context, question, question, onlineEnabled, endpoint, history, false, isDebugBuild(context));
    }

    public String answer(
            Context context,
            String visibleQuestion,
            String questionForAi,
            boolean onlineEnabled,
            String endpoint,
            String history,
            boolean forceRemote,
            boolean allowHttp
    ) {
        if (!forceRemote) {
            String local = localAnswerEngine.answer(context, visibleQuestion);
            if (local != null) return local;
        }

        if (!onlineEnabled) {
            return localAnswerEngine.offlineFallback();
        }

        String localeTag = Locale.getDefault().toLanguageTag();
        return remoteAiClient.ask(endpoint, questionForAi, localeTag, history, allowHttp);
    }

    private boolean isDebugBuild(Context context) {
        return (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
