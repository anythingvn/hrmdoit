package vn.doit.hr.leave;

public class OvertimeLimitException extends RuntimeException {

    public OvertimeLimitException(String message) {
        super(message);
    }
}
