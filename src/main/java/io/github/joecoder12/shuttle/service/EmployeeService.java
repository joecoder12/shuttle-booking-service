package io.github.joecoder12.shuttle.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.joecoder12.shuttle.api.dto.CreateEmployeeRequest;
import io.github.joecoder12.shuttle.api.dto.EmployeeResponse;
import io.github.joecoder12.shuttle.domain.Employee;
import io.github.joecoder12.shuttle.error.ConflictException;
import io.github.joecoder12.shuttle.error.ResourceNotFoundException;
import io.github.joecoder12.shuttle.repository.EmployeeRepository;

@Service
public class EmployeeService {

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (employees.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An employee with email " + email + " already exists");
        }
        return EmployeeResponse.from(employees.save(new Employee(request.name().trim(), email)));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return employees.findById(id)
                .map(EmployeeResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }
}
