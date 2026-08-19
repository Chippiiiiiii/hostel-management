package com.outpass.portal.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
    
    public RateLimitExceededException() {
        super("Rate limit exceeded. Please try again later.");
    }
}
