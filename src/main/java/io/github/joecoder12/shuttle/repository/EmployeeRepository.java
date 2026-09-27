package io.github.joecoder12.shuttle.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.joecoder12.shuttle.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmailIgnoreCase(String email);
}
