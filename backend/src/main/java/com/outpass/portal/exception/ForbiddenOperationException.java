package com.outpass.portal.exception;

// Thrown by service/controller code for a genuine authorization denial (ownership/scope
// checks -- e.g. a warden acting outside their own hostel, a student reading another
// student's record) as opposed to malformed/invalid request data. Distinct from plain
// RuntimeException so GlobalExceptionHandler can map it to 403 Forbidden instead of 400
// Bad Request, matching real HTTP semantics without touching whether access is granted.
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
