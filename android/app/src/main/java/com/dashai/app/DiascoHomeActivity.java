package com.dashai.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dashai.app.obd.ThinkDiagActivity;
import com.dashai.app.obd.ThinkDiagWorkshopActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Unique launcher for DIASCO.
 *
 * The existing assistant, ThinkDiag report analysis and workshop workflows
 * remain isolated activities, but they are opened from one application home
 * screen instead of appearing as separate launcher icons.
 */
public final class DiascoHomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        int pagePadding = dp(18);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(243, 246, 245));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pagePadding, dp(22), pagePadding, dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("DIASCO");
        title.setTextSize(31);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(23, 33, 38));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Un seul assistant · toutes les fonctionnalités");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(0, 122, 97));
        subtitle.setPadding(0, dp(3), 0, dp(8));
        root.addView(subtitle);

        TextView explanation = new TextView(this);
        explanation.setText(
                "Choisissez l’espace à ouvrir. La conversation, la caméra, le diagnostic ThinkDiag "
                        + "et les fonctions d’atelier font partie de la même application DIASCO."
        );
        explanation.setTextSize(15);
        explanation.setTextColor(Color.rgb(69, 82, 78));
        explanation.setLineSpacing(0, 1.1f);
        explanation.setPadding(0, 0, 0, dp(12));
        root.addView(explanation);

        root.addView(moduleCard(
                "Assistant DIASCO",
                "Conversation, réveil vocal, caméra, génération d’images, code et création de sites.",
                "Ouvrir l’assistant",
                R.drawable.ic_sparkles,
                true,
                view -> open(MainActivity.class)
        ), cardParams());

        root.addView(moduleCard(
                "Diagnostic Auto",
                "Importer ou partager un rapport ThinkDiag, analyser les défauts et conserver l’historique.",
                "Ouvrir le diagnostic",
                R.drawable.ic_globe,
                false,
                view -> open(ThinkDiagActivity.class)
        ), cardParams());

        root.addView(moduleCard(
                "Atelier ThinkDiag",
                "Préparer l’effacement des défauts, le codage, les adaptations et les tests d’actionneurs.",
                "Ouvrir l’atelier",
                R.drawable.ic_code,
                false,
                view -> open(ThinkDiagWorkshopActivity.class)
        ), cardParams());

        TextView note = new TextView(this);
        note.setText(
                "Une seule icône DIASCO apparaît dans le lanceur. Les espaces Assistant, Diagnostic "
                        + "et Atelier restent regroupés à l’intérieur de la même application."
        );
        note.setTextSize(13);
        note.setTextColor(Color.rgb(92, 107, 102));
        note.setLineSpacing(0, 1.08f);
        note.setPadding(dp(2), dp(8), dp(2), 0);
        root.addView(note);

        setContentView(scrollView);
    }

    private MaterialCardView moduleCard(
            String title,
            String description,
            String buttonLabel,
            int iconRes,
            boolean primary,
            View.OnClickListener listener
    ) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(primary ? Color.rgb(151, 211, 193) : Color.rgb(220, 228, 225));
        card.setCardBackgroundColor(primary ? Color.rgb(239, 250, 246) : Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(15), dp(16), dp(16));

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(Color.rgb(23, 33, 38));
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = new TextView(this);
        detail.setText(description);
        detail.setTextSize(14);
        detail.setTextColor(Color.rgb(69, 82, 78));
        detail.setLineSpacing(0, 1.08f);
        detail.setPadding(0, dp(5), 0, dp(12));
        content.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        MaterialButton button = new MaterialButton(this);
        button.setText(buttonLabel);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setIconResource(iconRes);
        button.setIconPadding(dp(8));
        button.setCornerRadius(dp(9));
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setIconTint(ColorStateList.valueOf(Color.WHITE));
            button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(0, 143, 114)));
        } else {
            button.setTextColor(Color.rgb(23, 33, 38));
            button.setIconTint(ColorStateList.valueOf(Color.rgb(0, 122, 97)));
            button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(244, 247, 246)));
            button.setStrokeColor(ColorStateList.valueOf(Color.rgb(210, 220, 216)));
            button.setStrokeWidth(dp(1));
        }
        content.addView(button, new LinearLayout.LayoutParams(-1, dp(50)));

        card.addView(content);
        return card;
    }

    private void open(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        return params;
    }

    @SuppressWarnings("unused")
    private GradientDrawable roundedBackground(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
