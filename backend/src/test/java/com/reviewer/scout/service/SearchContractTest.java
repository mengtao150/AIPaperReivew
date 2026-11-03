package com.reviewer.scout.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.reviewer.scout.repository.ScanRunRepository;
import com.reviewer.scout.entity.ScanRun;

class SearchContractTest {
    private final ScanService scans = new ScanService(null, null, null, null, null, null, null, null, null, null);

    @Test void pastedQueryHasNoHiddenAbstractFilter() {
        assertEquals("(HIV)", scans.buildQuery("HIV", null, null, "", ""));
    }

    @Test void invalidDateRangeCannotBecomeAnExecutableQuery() {
        assertThrows(IllegalArgumentException.class, () -> scans.buildQuery("diabetes", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 1, 1), "", ""));
    }

    @Test void unbalancedParenthesesAreReportedBeforePubMedCall() {
        assertThrows(IllegalArgumentException.class, () -> scans.buildQuery("(diabetes OR obesity", null, null, "", ""));
    }
    @Test void unicodeTaskNamesAndRepeatedNamesCreateDistinctRunIds() throws Exception {
        ScanRunRepository repository = mock(ScanRunRepository.class);
        when(repository.save(any(ScanRun.class))).thenAnswer(call -> call.getArgument(0));
        ScanService service = new ScanService(repository, null, null, null, null, null, null, null, null, null);
        String first = service.createScan("🔬🔬🔬", "HIV", null, null, "", "").getRunId();
        String second = service.createScan("🔬🔬🔬", "HIV", null, null, "", "").getRunId();
        assertNotEquals(first, second);
    }
}
