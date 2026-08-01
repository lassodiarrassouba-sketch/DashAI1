package com.dashai.app.obd;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Controlled preparation and audit workflow for ThinkDiag write operations.
 *
 * The official ThinkDiag+ app remains the only component that communicates with
 * the scanner. DIASCO validates prerequisites, records the operation intent and
 * hands control to ThinkDiag+. No proprietary protocol is reproduced here.
 */
public final class ThinkDiagWorkshopActivity extends AppCompatActivity {
    private static final String THINKDIAG_PACKAGE = "com.us.thinkdiag.plus";
    private static final String PROFILE_PREFS = "diasco_obd_profile";
    private static final String KEY_MAKE = "make";
    private static final String KEY_MODEL = "model";
    private static final String KEY_YEAR = "year";
    private static final String KEY_FUEL = "fuel";
    private static final String KEY_WORKSHOP_NOTICE = "workshop_notice_seen";

    private SharedPreferences profilePreferences;
    private TextView vehicleText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profilePreferences = getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE);
        buildUi();
        showFirstUseNotice();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshVehicleLabel();
    }

    private void buildUi() {
        int padding = dp(16);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(243, 246, 245));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("DIASCO Atelier");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(23, 33, 38));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Effacement, codage et tests actifs avec ThinkDiag+");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(0, 122, 97));
        subtitle.setPadding(0, dp(2), 0, dp(12));
        root.addView(subtitle);

        TextView explanation = new TextView(this);
        explanation.setText("DIASCO prépare l’intervention, vérifie les conditions de sécurité et garde une trace locale. L’ordre envoyé au véhicule reste exécuté dans l’application officielle ThinkDiag+, qui possède la liaison Bluetooth et les logiciels constructeurs.");
        explanation.setTextSize(15);
        explanation.setTextColor(Color.rgb(55, 68, 64));
        explanation.setLineSpacing(0, 1.1f);
        explanation.setPadding(dp(12), dp(11), dp(12), dp(11));
        root.addView(cardWithContent(explanation), cardParams(0, dp(10)));

        root.addView(sectionTitle("Véhicule"), sectionParams());
        vehicleText = new TextView(this);
        vehicleText.setTextSize(16);
        vehicleText.setTypeface(Typeface.DEFAULT_BOLD);
        vehicleText.setTextColor(Color.rgb(23, 33, 38));
        vehicleText.setPadding(dp(12), dp(12), dp(12), dp(6));

        MaterialButton editProfile = actionButton("Modifier le profil dans DIASCO Auto", Color.WHITE, Color.rgb(23, 33, 38));
        editProfile.setStrokeColor(ColorStateList.valueOf(Color.rgb(210, 220, 216)));
        editProfile.setStrokeWidth(dp(1));
        editProfile.setOnClickListener(view -> openDiascoAuto());

        LinearLayout vehicleContent = new LinearLayout(this);
        vehicleContent.setOrientation(LinearLayout.VERTICAL);
        vehicleContent.addView(vehicleText, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(48));
        editParams.setMargins(dp(10), dp(4), dp(10), dp(10));
        vehicleContent.addView(editProfile, editParams);
        root.addView(cardWithContent(vehicleContent), cardParams(0, dp(8)));

        root.addView(sectionTitle("Fonctions atelier"), sectionParams());

        MaterialButton clearCodes = actionButton("Effacer les défauts", Color.rgb(0, 143, 114), Color.WHITE);
        clearCodes.setOnClickListener(view -> showPreparation(WorkshopOperationPolicy.Operation.CLEAR_DTC));
        root.addView(clearCodes, fullButtonParams());

        MaterialButton ecuCoding = actionButton("Codage / adaptation calculateur", Color.rgb(28, 91, 154), Color.WHITE);
        ecuCoding.setOnClickListener(view -> showPreparation(WorkshopOperationPolicy.Operation.ECU_CODING));
        root.addView(ecuCoding, fullButtonParams());

        MaterialButton actuatorTests = actionButton("Tests d’actionneurs", Color.rgb(126, 78, 28), Color.WHITE);
        actuatorTests.setOnClickListener(view -> showActuatorSelection());
        root.addView(actuatorTests, fullButtonParams());

        MaterialButton openThinkDiag = actionButton("Ouvrir directement ThinkDiag+", Color.WHITE, Color.rgb(23, 33, 38));
        openThinkDiag.setStrokeColor(ColorStateList.valueOf(Color.rgb(210, 220, 216)));
        openThinkDiag.setStrokeWidth(dp(1));
        openThinkDiag.setOnClickListener(view -> launchThinkDiag(null, "Ouverture manuelle"));
        root.addView(openThinkDiag, fullButtonParams());

        MaterialButton history = actionButton("Historique des interventions", Color.rgb(234, 239, 237), Color.rgb(23, 33, 38));
        history.setOnClickListener(view -> showHistory());
        root.addView(history, fullButtonParams());

        statusText = new TextView(this);
        statusText.setText("Prêt. Choisissez une opération.");
        statusText.setTextSize(13);
        statusText.setTextColor(Color.rgb(92, 107, 102));
        statusText.setPadding(dp(2), dp(8), dp(2), dp(8));
        root.addView(statusText);

        TextView afterTitle = sectionTitle("Après l’intervention");
        root.addView(afterTitle, sectionParams());
        TextView afterText = new TextView(this);
        afterText.setText("Relancez un scan complet dans ThinkDiag+, puis partagez le nouveau rapport vers DIASCO Auto. DIASCO vérifiera si les défauts reviennent et conservera l’analyse.");
        afterText.setTextSize(15);
        afterText.setTextColor(Color.rgb(55, 68, 64));
        afterText.setPadding(dp(12), dp(11), dp(12), dp(11));
        root.addView(cardWithContent(afterText), cardParams(0, dp(8)));

        MaterialButton openAuto = actionButton("Ouvrir DIASCO Auto", Color.rgb(0, 143, 114), Color.WHITE);
        openAuto.setOnClickListener(view -> openDiascoAuto());
        root.addView(openAuto, fullButtonParams());

        TextView safety = new TextView(this);
        safety.setText("Important : l’effacement ne répare pas la panne et remet certains moniteurs antipollution à zéro. Un codage interrompu peut immobiliser un calculateur. Les tests actifs peuvent provoquer un mouvement immédiat d’un organe.");
        safety.setTextSize(13);
        safety.setTextColor(Color.rgb(126, 48, 35));
        safety.setLineSpacing(0, 1.08f);
        safety.setPadding(dp(12), dp(10), dp(12), dp(10));
        MaterialCardView safetyCard = cardWithContent(safety);
        safetyCard.setCardBackgroundColor(Color.rgb(255, 240, 236));
        safetyCard.setStrokeColor(Color.rgb(230, 171, 157));
        root.addView(safetyCard, cardParams(dp(4), 0));

        setContentView(scroll);
        refreshVehicleLabel();
    }

    private void showFirstUseNotice() {
        if (profilePreferences.getBoolean(KEY_WORKSHOP_NOTICE, false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Mode atelier")
                .setMessage("ThinkDiag prend officiellement en charge l’effacement des codes, le codage ou l’adaptation de certains calculateurs et des tests bidirectionnels selon le véhicule et la licence. DIASCO ajoute des contrôles de préparation, mais l’exécution reste dans ThinkDiag+ tant qu’aucun SDK partenaire officiel n’est fourni.")
                .setPositiveButton("J’ai compris", (dialog, which) -> profilePreferences.edit()
                        .putBoolean(KEY_WORKSHOP_NOTICE, true)
                        .apply())
                .show();
    }

    private void showActuatorSelection() {
        String[] choices = {
                "Carrosserie : feux, klaxon, serrures, vitres, rétroviseurs, essuie-glaces",
                "Moteur : ventilateur, papillon, purge, pompe, injecteurs",
                "Châssis : freinage, direction, transmission, suspension",
                "SRS : airbags et prétensionneurs"
        };
        new AlertDialog.Builder(this)
                .setTitle("Choisir le groupe d’actionneurs")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        showPreparation(WorkshopOperationPolicy.Operation.ACTUATOR_BODY);
                    } else if (which == 1) {
                        showPreparation(WorkshopOperationPolicy.Operation.ACTUATOR_POWERTRAIN);
                    } else if (which == 2) {
                        showPreparation(WorkshopOperationPolicy.Operation.ACTUATOR_CHASSIS);
                    } else {
                        WorkshopOperationPolicy.Decision decision = WorkshopOperationPolicy.evaluate(
                                WorkshopOperationPolicy.Operation.ACTUATOR_SRS,
                                emptyChecklist(),
                                ""
                        );
                        WorkshopOperationStore.add(
                                this,
                                vehicleLabel(),
                                WorkshopOperationPolicy.label(WorkshopOperationPolicy.Operation.ACTUATOR_SRS),
                                "BLOQUÉ PAR DIASCO",
                                decision.message
                        );
                        new AlertDialog.Builder(this)
                                .setTitle("Fonction non lancée")
                                .setMessage(decision.message)
                                .setPositiveButton("Fermer", null)
                                .show();
                    }
                })
                .show();
    }

    private void showPreparation(WorkshopOperationPolicy.Operation operation) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), 0);

        TextView description = new TextView(this);
        description.setText(operationDescription(operation));
        description.setTextSize(14);
        description.setTextColor(Color.rgb(55, 68, 64));
        description.setLineSpacing(0, 1.08f);
        content.addView(description, new LinearLayout.LayoutParams(-1, -2));

        CheckBox vehicleSecured = checkBox("Le véhicule est à l’arrêt, en P ou au point mort, frein de stationnement serré.");
        content.addView(vehicleSecured);

        CheckBox reportSaved = checkBox("Le rapport avant intervention et les codes ont été enregistrés.");
        CheckBox engineOff = checkBox("Le moteur est coupé ; le contact sera utilisé seulement comme demandé par ThinkDiag+.");
        CheckBox batterySupport = checkBox("Un maintien de tension automobile stable est branché et surveillé.");
        CheckBox vinVerified = checkBox("Le VIN, le calculateur cible et la pièce remplacée ont été vérifiés.");
        CheckBox areaClear = checkBox("Personne ni objet ne se trouve dans la zone de mouvement de l’organe testé.");
        CheckBox professionalMode = checkBox("Je confirme que ce test à risque est réalisé en mode professionnel, avec arrêt immédiat possible.");

        if (operation == WorkshopOperationPolicy.Operation.CLEAR_DTC
                || operation == WorkshopOperationPolicy.Operation.ECU_CODING) {
            content.addView(reportSaved);
            content.addView(engineOff);
        }
        if (operation == WorkshopOperationPolicy.Operation.ECU_CODING) {
            content.addView(batterySupport);
            content.addView(vinVerified);
        }
        if (operation == WorkshopOperationPolicy.Operation.ACTUATOR_BODY
                || operation == WorkshopOperationPolicy.Operation.ACTUATOR_POWERTRAIN
                || operation == WorkshopOperationPolicy.Operation.ACTUATOR_CHASSIS) {
            content.addView(areaClear);
        }
        if (operation == WorkshopOperationPolicy.Operation.ACTUATOR_POWERTRAIN
                || operation == WorkshopOperationPolicy.Operation.ACTUATOR_CHASSIS) {
            content.addView(professionalMode);
        }

        String word = WorkshopOperationPolicy.confirmationWord(operation);
        EditText confirmation = new EditText(this);
        confirmation.setSingleLine(true);
        confirmation.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        confirmation.setHint("Saisissez " + word);
        confirmation.setTextSize(15);
        confirmation.setPadding(dp(10), dp(8), dp(10), dp(8));
        confirmation.setBackground(roundedBackground(Color.rgb(248, 250, 249), Color.rgb(220, 228, 225), 8));
        LinearLayout.LayoutParams confirmationParams = new LinearLayout.LayoutParams(-1, dp(52));
        confirmationParams.topMargin = dp(10);
        content.addView(confirmation, confirmationParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(WorkshopOperationPolicy.label(operation))
                .setView(content)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Continuer", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            WorkshopOperationPolicy.Checklist checklist = new WorkshopOperationPolicy.Checklist(
                    vehicleSecured.isChecked(),
                    reportSaved.isChecked(),
                    engineOff.isChecked(),
                    batterySupport.isChecked(),
                    vinVerified.isChecked(),
                    areaClear.isChecked(),
                    professionalMode.isChecked()
            );
            WorkshopOperationPolicy.Decision decision = WorkshopOperationPolicy.evaluate(
                    operation,
                    checklist,
                    confirmation.getText().toString()
            );
            if (!decision.allowed) {
                Toast.makeText(this, decision.message, Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            showFinalHandoff(operation, decision.message);
        }));
        dialog.show();
    }

    private void showFinalHandoff(WorkshopOperationPolicy.Operation operation, String preparationMessage) {
        String message = preparationMessage + "\n\n" + finalWarning(operation)
                + "\n\nDans ThinkDiag+, sélectionnez exactement le véhicule et le calculateur concernés, lisez chaque avertissement, puis revenez faire un nouveau scan.";

        new AlertDialog.Builder(this)
                .setTitle("Dernière confirmation")
                .setMessage(message)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Ouvrir ThinkDiag+", (dialog, which) -> {
                    String label = WorkshopOperationPolicy.label(operation);
                    WorkshopOperationStore.add(
                            this,
                            vehicleLabel(),
                            label,
                            "PRÉPARÉ — À EXÉCUTER DANS THINKDIAG+",
                            finalWarning(operation)
                    );
                    launchThinkDiag(operation, label);
                })
                .show();
    }

    private String operationDescription(WorkshopOperationPolicy.Operation operation) {
        switch (operation) {
            case CLEAR_DTC:
                return "Utilisez cette fonction après avoir lu et sauvegardé les codes, diagnostiqué la cause et effectué la réparation. L’effacement seul peut masquer temporairement le voyant sans supprimer la panne.";
            case ECU_CODING:
                return "Cette préparation couvre le codage, l’adaptation, l’initialisation ou l’appairage pris en charge par ThinkDiag+. La couverture varie selon le calculateur. Une programmation firmware complète n’est pas garantie par ThinkDiag.";
            case ACTUATOR_BODY:
                return "Tests à faible énergie des équipements de carrosserie. Même ces organes peuvent pincer, heurter ou surprendre : gardez la zone dégagée.";
            case ACTUATOR_POWERTRAIN:
                return "Tests moteur à risque : ventilateur, papillon, pompe, injecteurs ou électrovannes peuvent démarrer immédiatement. Gardez les mains, vêtements et outils éloignés.";
            case ACTUATOR_CHASSIS:
                return "Tests châssis à risque : pompe ABS, direction, transmission ou suspension peuvent créer une pression ou un mouvement soudain. Utilisez un environnement d’atelier sécurisé.";
            case ACTUATOR_SRS:
            default:
                return "Opération non prise en charge par DIASCO.";
        }
    }

    private String finalWarning(WorkshopOperationPolicy.Operation operation) {
        switch (operation) {
            case CLEAR_DTC:
                return "Après l’effacement, certains moniteurs OBD et données figées peuvent être réinitialisés. Effectuez immédiatement un nouveau scan et vérifiez le retour des défauts.";
            case ECU_CODING:
                return "Ne débranchez ni la batterie, ni le boîtier, ni Internet pendant l’opération. Une chute de tension ou un mauvais calculateur sélectionné peut rendre le module inutilisable.";
            case ACTUATOR_BODY:
                return "Gardez les doigts éloignés des vitres, serrures, rétroviseurs et essuie-glaces. Arrêtez le test au premier mouvement inattendu.";
            case ACTUATOR_POWERTRAIN:
                return "Éloignez-vous des courroies, ventilateurs, papillon, carburant et pièces chaudes. Aucune personne ne doit travailler sous le capot pendant la commande.";
            case ACTUATOR_CHASSIS:
                return "Le véhicule doit être correctement immobilisé. N’exécutez pas de procédure de freinage, direction ou transmission sans la procédure constructeur correspondante.";
            default:
                return "Respectez la procédure constructeur et les avertissements ThinkDiag+.";
        }
    }

    private void launchThinkDiag(WorkshopOperationPolicy.Operation operation, String label) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(THINKDIAG_PACKAGE);
        if (launchIntent != null) {
            status("ThinkDiag+ ouvert · " + label);
            startActivity(launchIntent);
            return;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + THINKDIAG_PACKAGE)));
        } catch (ActivityNotFoundException exception) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + THINKDIAG_PACKAGE)));
            } catch (ActivityNotFoundException secondException) {
                Toast.makeText(this, "ThinkDiag+ n’est pas installé.", Toast.LENGTH_LONG).show();
                if (operation != null) {
                    WorkshopOperationStore.add(
                            this,
                            vehicleLabel(),
                            WorkshopOperationPolicy.label(operation),
                            "ÉCHEC D’OUVERTURE",
                            "L’application ThinkDiag+ est absente."
                    );
                }
            }
        }
    }

    private void openDiascoAuto() {
        try {
            startActivity(new Intent(this, ThinkDiagActivity.class));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "DIASCO Auto n’a pas pu être ouvert.", Toast.LENGTH_LONG).show();
        }
    }

    private void showHistory() {
        List<WorkshopOperationStore.Entry> entries = WorkshopOperationStore.load(this);
        if (entries.isEmpty()) {
            Toast.makeText(this, "Aucune intervention enregistrée.", Toast.LENGTH_SHORT).show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.FRANCE);

        for (WorkshopOperationStore.Entry entry : entries) {
            TextView item = new TextView(this);
            String date = entry.timestamp > 0 ? formatter.format(new Date(entry.timestamp)) : "Date inconnue";
            item.setText(date + "\n" + entry.vehicle + "\n" + entry.operation
                    + "\nStatut : " + entry.status + (entry.note.isEmpty() ? "" : "\n\n" + entry.note));
            item.setTextSize(14);
            item.setTextColor(Color.rgb(23, 33, 38));
            item.setTextIsSelectable(true);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackground(roundedBackground(Color.WHITE, Color.rgb(220, 228, 225), 8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            content.addView(item, params);
        }
        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Historique DIASCO Atelier")
                .setView(scroll)
                .setPositiveButton("Fermer", null)
                .setNegativeButton("Effacer l’historique", (dialog, which) -> {
                    WorkshopOperationStore.clear(this);
                    Toast.makeText(this, "Historique effacé.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void refreshVehicleLabel() {
        if (vehicleText != null) vehicleText.setText(vehicleLabel());
    }

    private String vehicleLabel() {
        return ObdReportTools.vehicleLabel(
                profilePreferences.getString(KEY_MAKE, ""),
                profilePreferences.getString(KEY_MODEL, ""),
                profilePreferences.getString(KEY_YEAR, ""),
                profilePreferences.getString(KEY_FUEL, "")
        );
    }

    private WorkshopOperationPolicy.Checklist emptyChecklist() {
        return new WorkshopOperationPolicy.Checklist(false, false, false, false, false, false, false);
    }

    private CheckBox checkBox(String label) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextSize(14);
        box.setTextColor(Color.rgb(23, 33, 38));
        box.setPadding(0, dp(5), 0, dp(2));
        return box;
    }

    private TextView sectionTitle(String value) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(23, 33, 38));
        return title;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        params.bottomMargin = dp(6);
        return params;
    }

    private MaterialCardView cardWithContent(View content) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(220, 228, 225));
        card.setCardBackgroundColor(Color.WHITE);
        card.addView(content);
        return card;
    }

    private LinearLayout.LayoutParams cardParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private MaterialButton actionButton(String label, int background, int foreground) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setCornerRadius(dp(8));
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.bottomMargin = dp(8);
        return params;
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void status(String message) {
        if (statusText != null) {
            statusText.setText(message == null || message.trim().isEmpty() ? "Prêt" : message.trim());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
