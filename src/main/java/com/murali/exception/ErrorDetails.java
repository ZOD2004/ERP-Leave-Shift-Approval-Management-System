package com.murali.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data

public class ErrorDetails {
    String msg;
    String desc;
    LocalDateTime timestamp;
}
