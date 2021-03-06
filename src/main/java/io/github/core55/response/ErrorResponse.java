/*
  Authors: S. Stefani
 */

package io.github.core55.response;

/**
 * Define the structure of an error message with timestamp in Unix time, status
 * code, error name and custom message.
 */
public abstract class ErrorResponse {

    private long timestamp;
    private int status;
    private String error;
    private String message;

    public ErrorResponse() {
        this.setTimestamp();
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp() {
        this.timestamp = System.currentTimeMillis() / 1000L;
    }
}
