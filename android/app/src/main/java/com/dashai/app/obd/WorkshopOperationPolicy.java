package com.dashai.app.obd;

import java.util.Locale;

/**
 * Pure validation rules for operations that may modify a vehicle or command an actuator.
 *
 * This class never sends a diagnostic command. It validates the preparation workflow
 * before DIASCO hands control to the official ThinkDiag+ application.
 */
public final class WorkshopOperationPolicy {
    private WorkshopOperationPolicy() {
    }

    public enum Operation {
        CLEAR_DTC,
        ECU_CODING,
        ACTUATOR_BODY,
        ACTUATOR_POWERTRAIN,
        ACTUATOR_CHASSIS,
        ACTUATOR_SRS
    }

    public static final class Checklist {
        public final boolean vehicleSecured;
        public final boolean reportSaved;
        public final boolean engineOff;
        public final boolean batterySupport;
        public final boolean vinVerified;
        public final boolean areaClear;
        public final boolean professionalMode;

        public Checklist(
                boolean vehicleSecured,
                boolean reportSaved,
                boolean engineOff,
                boolean batterySupport,
                boolean vinVerified,
                boolean areaClear,
                boolean professionalMode
        ) {
            this.vehicleSecured = vehicleSecured;
            this.reportSaved = reportSaved;
            this.engineOff = engineOff;
            this.batterySupport = batterySupport;
            this.vinVerified = vinVerified;
            this.areaClear = areaClear;
            this.professionalMode = professionalMode;
        }
    }

    public static final class Decision {
        public final boolean allowed;
        public final String message;

        private Decision(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public static Decision allow(String message) {
            return new Decision(true, message);
        }

        public static Decision deny(String message) {
            return new Decision(false, message);
        }
    }

    public static Decision evaluate(Operation operation, Checklist checklist, String typedConfirmation) {
        if (operation == null || checklist == null) {
            return Decision.deny("Préparation incomplète.");
        }

        String typed = normalize(typedConfirmation);
        switch (operation) {
            case CLEAR_DTC:
                if (!checklist.vehicleSecured || !checklist.reportSaved || !checklist.engineOff) {
                    return Decision.deny("Immobilisez le véhicule, enregistrez le rapport avant effacement et coupez le moteur.");
                }
                if (!"EFFACER".equals(typed)) {
                    return Decision.deny("Saisissez exactement EFFACER pour confirmer.");
                }
                return Decision.allow("Préparation validée. L’effacement sera exécuté dans ThinkDiag+.");

            case ECU_CODING:
                if (!checklist.vehicleSecured || !checklist.reportSaved || !checklist.engineOff) {
                    return Decision.deny("Immobilisez le véhicule, sauvegardez le rapport et coupez le moteur.");
                }
                if (!checklist.batterySupport) {
                    return Decision.deny("Un maintien de tension stable est obligatoire avant un codage ou une adaptation.");
                }
                if (!checklist.vinVerified) {
                    return Decision.deny("Vérifiez le VIN et le calculateur cible avant de continuer.");
                }
                if (!"CODAGE".equals(typed)) {
                    return Decision.deny("Saisissez exactement CODAGE pour confirmer.");
                }
                return Decision.allow("Préparation validée. Le codage ou l’adaptation sera exécuté dans ThinkDiag+ selon la couverture du véhicule.");

            case ACTUATOR_BODY:
                if (!checklist.vehicleSecured || !checklist.areaClear) {
                    return Decision.deny("Immobilisez le véhicule et dégagez les vitres, serrures, rétroviseurs et essuie-glaces.");
                }
                if (!"TEST".equals(typed)) {
                    return Decision.deny("Saisissez exactement TEST pour confirmer.");
                }
                return Decision.allow("Préparation validée pour un test de carrosserie à faible énergie dans ThinkDiag+.");

            case ACTUATOR_POWERTRAIN:
            case ACTUATOR_CHASSIS:
                if (!checklist.vehicleSecured || !checklist.areaClear || !checklist.professionalMode) {
                    return Decision.deny("Ce test exige un véhicule immobilisé, une zone dégagée et la confirmation du mode professionnel.");
                }
                if (!"PROFESSIONNEL".equals(typed)) {
                    return Decision.deny("Saisissez exactement PROFESSIONNEL pour confirmer.");
                }
                return Decision.allow("Préparation renforcée validée. ThinkDiag+ restera responsable du test actif et de ses propres avertissements.");

            case ACTUATOR_SRS:
                return Decision.deny("Les commandes d’airbag et de prétensionneur ne sont pas lancées par DIASCO. Utilisez la procédure constructeur avec un technicien qualifié.");

            default:
                return Decision.deny("Opération inconnue.");
        }
    }

    public static String confirmationWord(Operation operation) {
        if (operation == null) return "";
        switch (operation) {
            case CLEAR_DTC:
                return "EFFACER";
            case ECU_CODING:
                return "CODAGE";
            case ACTUATOR_BODY:
                return "TEST";
            case ACTUATOR_POWERTRAIN:
            case ACTUATOR_CHASSIS:
                return "PROFESSIONNEL";
            case ACTUATOR_SRS:
            default:
                return "";
        }
    }

    public static String label(Operation operation) {
        if (operation == null) return "Opération inconnue";
        switch (operation) {
            case CLEAR_DTC:
                return "Effacement des défauts";
            case ECU_CODING:
                return "Codage / adaptation calculateur";
            case ACTUATOR_BODY:
                return "Test actionneur carrosserie";
            case ACTUATOR_POWERTRAIN:
                return "Test actionneur moteur";
            case ACTUATOR_CHASSIS:
                return "Test actionneur châssis";
            case ACTUATOR_SRS:
                return "Test SRS / airbag";
            default:
                return "Opération inconnue";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
