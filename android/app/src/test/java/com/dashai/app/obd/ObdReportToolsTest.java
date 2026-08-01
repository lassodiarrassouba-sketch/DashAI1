package com.dashai.app.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class ObdReportToolsTest {
    @Test
    public void extractsAndDeduplicatesStandardCodes() {
        List<String> codes = ObdReportTools.extractCodes(
                "ECM Fault P0302 current\nTCM P0700 stored\nP0302 repeated\nBody B1000"
        );

        assertEquals(Arrays.asList("P0302", "P0700", "B1000"), codes);
        assertEquals("P0302, P0700, B1000", ObdReportTools.codesLabel(codes));
    }

    @Test
    public void allNoFaultReportRemainsInformational() {
        String report = "ECM - Engine Control Module\nNo Fault\nABS\nNo Fault\nSRS\nNo Fault";

        assertEquals(
                "INFORMATION",
                ObdReportTools.detectSeverity(report, ObdReportTools.extractCodes(report))
        );
    }

    @Test
    public void safetyMessageRaisesCriticalSeverity() {
        String report = "Engine overheating. Stop immediately and do not drive.";

        assertEquals(
                "CRITIQUE",
                ObdReportTools.detectSeverity(report, ObdReportTools.extractCodes(report))
        );
    }

    @Test
    public void acceptsOnlyOfficialHttpsThinkCarHosts() {
        assertTrue(ObdReportTools.isAllowedThinkCarUrl(
                "https://thinklinkus.api.thinkcar.com/Home/ThinkCar/report/123"
        ));
        assertTrue(ObdReportTools.isAllowedThinkCarUrl("https://api.mythinkcar.com/report/123"));

        assertFalse(ObdReportTools.isAllowedThinkCarUrl("http://thinkcar.com/report/123"));
        assertFalse(ObdReportTools.isAllowedThinkCarUrl("https://thinkcar.com.example.org/report/123"));
        assertFalse(ObdReportTools.isAllowedThinkCarUrl("https://example.org/thinkcar.com/report/123"));
    }

    @Test
    public void extractsOfficialLinkFromSharedText() {
        String shared = "Rapport ThinkDiag : https://thinklinkus.api.thinkcar.com/Home/ThinkCar/report/123).";

        assertEquals(
                "https://thinklinkus.api.thinkcar.com/Home/ThinkCar/report/123",
                ObdReportTools.extractFirstAllowedThinkCarUrl(shared)
        );
    }

    @Test
    public void emptyVehicleProfileUsesNeutralLabel() {
        assertEquals(
                "Véhicule non renseigné",
                ObdReportTools.vehicleLabel("", "", "", "")
        );
    }

    @Test
    public void vehicleProfileAcceptsAnyMakeAndEnergy() {
        assertEquals(
                "Toyota · Hilux · 2021 · Diesel",
                ObdReportTools.vehicleLabel("Toyota", "Hilux", "2021", "Diesel")
        );
        assertEquals(
                "Model Y · Électrique",
                ObdReportTools.vehicleLabel("", "Model Y", "", "Électrique")
        );
    }
}
