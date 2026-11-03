package com.reviewer.scout.service;

import com.reviewer.scout.controller.ScanController;
import com.reviewer.scout.entity.ScanRun;
import com.reviewer.scout.repository.ScanRunRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterruptedTaskTest {
    @Test void missingWorkerBecomesRecoverableInsteadOfPollingForever() {
        ScanRunRepository repository = mock(ScanRunRepository.class); ScanRun run = new ScanRun(); run.setId(1L); run.setStatus("FETCHING");
        when(repository.findById(1L)).thenReturn(Optional.of(run));
        ScanController controller = new ScanController(null, repository, null, null, null, null, null, null, null, null, new TaskProgressService(), null);
        assertEquals("INTERRUPTED", ((Map<?, ?>) controller.status(1L).getBody()).get("phase"));
        assertThrows(IllegalArgumentException.class, () -> controller.pause(1L));
    }
}
