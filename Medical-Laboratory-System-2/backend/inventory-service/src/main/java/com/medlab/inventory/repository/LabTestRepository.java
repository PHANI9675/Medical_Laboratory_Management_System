
package com.medlab.inventory.repository;

import com.medlab.inventory.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {
    Optional<LabTest> findByCode(String code);
    boolean existsByCode(String code);
}