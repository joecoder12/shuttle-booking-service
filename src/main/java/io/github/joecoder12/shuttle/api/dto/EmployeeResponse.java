package io.github.joecoder12.shuttle.api.dto;

import io.github.joecoder12.shuttle.domain.Employee;

public record EmployeeResponse(Long id, String name, String email) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getName(), employee.getEmail());
    }
}
