
package com.dtrps.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Xử lý lỗi Backend.
 * Giữ định dạng JSON { message } cho các REST API.
 *
 * Riêng kết nối SSE bị ngắt sẽ không cố gửi JSON
 * lên một response có Content-Type text/event-stream.
 */
@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    public static final String UNAUTHORIZED_MESSAGE =
            "Bạn cần đăng nhập để tiếp tục";

    public static final String FORBIDDEN_MESSAGE =
            "Bạn không có quyền thực hiện thao tác này";

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String INVALID_REQUEST =
            "Dữ liệu gửi lên không hợp lệ";

    private static final String NOT_FOUND =
            "Không tìm thấy dữ liệu yêu cầu";

    private static final String REQUEST_REJECTED =
            "Không thể xử lý yêu cầu";

    private static final String SERVER_ERROR =
            "Hệ thống đang gặp sự cố, vui lòng thử lại sau";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex) {

        return ResponseEntity
                .status(ex.getStatus())
                .body(ErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(FORBIDDEN_MESSAGE));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(UNAUTHORIZED_MESSAGE));
    }

    /**
     * Xử lý lỗi validation của query và path parameter.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = new LinkedHashMap<>();

        for (ConstraintViolation<?> violation :
                ex.getConstraintViolations()) {

            String path =
                    violation.getPropertyPath().toString();

            errors.putIfAbsent(
                    path.substring(path.lastIndexOf('.') + 1),
                    violation.getMessage()
            );
        }

        String message = errors.size() == 1
                ? errors.values().iterator().next()
                : INVALID_REQUEST;

        return ResponseEntity.badRequest()
                .body(
                        new ErrorResponse(
                                message,
                                Map.copyOf(errors)
                        )
                );
    }

    /**
     * Xử lý lỗi không mong muốn.
     *
     * Trường hợp đặc biệt:
     * Khi SSE notification bị ngắt kết nối, Backend không
     * được gửi JSON ErrorResponse lên luồng SSE đã đóng.
     *
     * Trả về null để Spring không ghi thêm response.
     *
     * Các REST API thông thường vẫn giữ cách xử lý 500.
     */
    @ExceptionHandler(Exception.class)
    @Nullable
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (isNotificationStream(request)) {

            // Trình duyệt đóng tab, tải lại trang hoặc mất mạng.
            if (causedByIOException(ex)) {

                log.debug(
                        "Notification SSE client disconnected: {}",
                        ex.toString()
                );

                return null;
            }

            // Response đã gửi đi thì không thể chuyển sang JSON.
            if (response.isCommitted()) {

                log.warn(
                        "Notification SSE failed after response was committed",
                        ex
                );

                return null;
            }
        }

        // Giữ nguyên xử lý lỗi cho REST API thông thường.
        log.error("Unhandled exception", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(SERVER_ERROR));
    }

    /**
     * Chỉ nhận diện endpoint SSE thông báo.
     * Không ảnh hưởng API Goong Maps hoặc Travel Plan.
     */
    private static boolean isNotificationStream(
            HttpServletRequest request) {

        return request.getRequestURI()
                .endsWith("/api/notifications/stream");
    }

    /**
     * Kiểm tra IOException ở exception hoặc cause.
     */
    private static boolean causedByIOException(
            Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof IOException) {
                return true;
            }

            if (current == current.getCause()) {
                break;
            }

            current = current.getCause();
        }

        return false;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError error :
                ex.getBindingResult().getFieldErrors()) {

            errors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }

        return handleExceptionInternal(
                ex,
                new ErrorResponse(
                        INVALID_REQUEST,
                        Map.copyOf(errors)
                ),
                headers,
                status,
                request
        );
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        Object payload =
                body instanceof ErrorResponse
                        ? body
                        : ErrorResponse.of(
                        messageFor(statusCode)
                );

        return super.handleExceptionInternal(
                ex,
                payload,
                headers,
                statusCode,
                request
        );
    }

    private static String messageFor(
            HttpStatusCode status) {

        if (status.is5xxServerError()) {
            return SERVER_ERROR;
        }

        if (
                status.value() ==
                        HttpStatus.BAD_REQUEST.value()
        ) {
            return INVALID_REQUEST;
        }

        if (
                status.value() ==
                        HttpStatus.NOT_FOUND.value()
        ) {
            return NOT_FOUND;
        }

        return REQUEST_REJECTED;
    }
}
