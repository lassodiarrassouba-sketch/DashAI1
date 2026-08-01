package com.dashai.app.obd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WorkshopOperationPolicyTest {
    @Test
    public void clearDtcRequiresSavedReportEngineOffAndTypedWord() {
        WorkshopOperationPolicy.Checklist incomplete = checklist(true, false, true, false, false, false, false);
        assertFalse(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.CLEAR_DTC,
                incomplete,
                "EFFACER"
        ).allowed);

        WorkshopOperationPolicy.Checklist complete = checklist(true, true, true, false, false, false, false);
        assertFalse(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.CLEAR_DTC,
                complete,
                "OK"
        ).allowed);
        assertTrue(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.CLEAR_DTC,
                complete,
                "effacer"
        ).allowed);
    }

    @Test
    public void ecuCodingRequiresBatterySupportAndVinVerification() {
        WorkshopOperationPolicy.Checklist missingPower = checklist(true, true, true, false, true, false, false);
        assertFalse(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.ECU_CODING,
                missingPower,
                "CODAGE"
        ).allowed);

        WorkshopOperationPolicy.Checklist complete = checklist(true, true, true, true, true, false, false);
        assertTrue(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.ECU_CODING,
                complete,
                "codage"
        ).allowed);
    }

    @Test
    public void highRiskActuatorRequiresProfessionalMode() {
        WorkshopOperationPolicy.Checklist normal = checklist(true, false, false, false, false, true, false);
        assertFalse(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.ACTUATOR_POWERTRAIN,
                normal,
                "PROFESSIONNEL"
        ).allowed);

        WorkshopOperationPolicy.Checklist professional = checklist(true, false, false, false, false, true, true);
        assertTrue(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.ACTUATOR_POWERTRAIN,
                professional,
                "professionnel"
        ).allowed);
    }

    @Test
    public void srsActuationIsAlwaysBlocked() {
        WorkshopOperationPolicy.Checklist allChecked = checklist(true, true, true, true, true, true, true);
        assertFalse(WorkshopOperationPolicy.evaluate(
                WorkshopOperationPolicy.Operation.ACTUATOR_SRS,
                allChecked,
                "PROFESSIONNEL"
        ).allowed);
    }

    private WorkshopOperationPolicy.Checklist checklist(
            boolean vehicleSecured,
            boolean reportSaved,
            boolean engineOff,
            boolean batterySupport,
            boolean vinVerified,
            boolean areaClear,
            boolean professionalMode
    ) {
        return new WorkshopOperationPolicy.Checklist(
                vehicleSecured,
                reportSaved,
                engineOff,
                batterySupport,
                vinVerified,
                areaClear,
                professionalMode
        );
    }
}
