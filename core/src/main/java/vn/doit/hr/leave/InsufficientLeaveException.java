package vn.doit.hr.leave;

public class InsufficientLeaveException extends RuntimeException {

    public InsufficientLeaveException(String message) {
        super(message);
    }
}
