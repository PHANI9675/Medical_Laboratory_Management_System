package com.medlab.inventory;

import com.medlab.inventory.dto.LabTestRequest;
import com.medlab.inventory.dto.LabTestResponse;
import com.medlab.inventory.entity.LabTest;
import com.medlab.inventory.repository.LabTestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabTestServiceTest {

    // Mock the repository — no real DB needed
    @Mock
    private LabTestRepository repo;

    // Inject the mocked repo into the real service
    @InjectMocks
    private LabTestService service;

    // ── Helper to build a sample LabTest entity ──────────────────────────────
    private LabTest sampleTest() {
        LabTest t = new LabTest();
        t.setId(1L);
        t.setCode("CBC");
        t.setName("Complete Blood Count");
        t.setPrice(new BigDecimal("12.50"));
        t.setTurnaroundHours(24);
        t.setDescription("Measures RBC, WBC, platelets");
        return t;
    }

    // ── Test 1: getAllTests returns list ──────────────────────────────────────
    @Test
    void getAllTests_shouldReturnListOfTests() {
        // arrange — tell mock what to return when findAll() is called
        when(repo.findAll()).thenReturn(List.of(sampleTest()));

        // act
        List<LabTestResponse> result = service.getAllTests();

        // assert
        assertEquals(1, result.size());
        assertEquals("CBC", result.get(0).getCode());
        //assertEquals("LFT", result.get(0).getCode()); // for fail test case
        assertEquals(new BigDecimal("12.50"), result.get(0).getPrice());
    }

    // ── Test 2: getTestById returns correct test ──────────────────────────────
    @Test
    void getTestById_whenExists_shouldReturnTest() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleTest()));

        LabTestResponse result = service.getTestById(1L);

        assertEquals("CBC", result.getCode());
        assertEquals("Complete Blood Count", result.getName());
    }

    // ── Test 3: getTestById throws when not found ─────────────────────────────
    @Test
    void getTestById_whenNotFound_shouldThrowException() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.getTestById(99L));

        assertEquals("Test not found with id: 99", ex.getMessage());
    }

    // ── Test 4: createTest saves and returns response ─────────────────────────
    @Test
    void createTest_whenCodeIsNew_shouldSaveAndReturn() {
        LabTestRequest req = new LabTestRequest();
        req.setCode("CBC");
        req.setName("Complete Blood Count");
        req.setPrice(new BigDecimal("12.50"));
        req.setTurnaroundHours(24);

        when(repo.existsByCode("CBC")).thenReturn(false);
        when(repo.save(any(LabTest.class))).thenReturn(sampleTest());

        LabTestResponse result = service.createTest(req);

        assertEquals("CBC", result.getCode());
        verify(repo, times(1)).save(any(LabTest.class)); // confirm save was called
    }

    // ── Test 5: createTest throws when duplicate code ─────────────────────────
    @Test
    void createTest_whenCodeExists_shouldThrowException() {
        LabTestRequest req = new LabTestRequest();
        req.setCode("CBC");
        req.setName("Duplicate");
        req.setPrice(new BigDecimal("10.00"));

        when(repo.existsByCode("CBC")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createTest(req));

        assertEquals("Test with code 'CBC' already exists", ex.getMessage());
        verify(repo, never()).save(any()); // confirm save was NOT called
    }
}