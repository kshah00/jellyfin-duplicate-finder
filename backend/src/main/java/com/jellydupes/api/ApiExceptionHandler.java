package com.jellydupes.api;

import com.jellydupes.service.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

/** Maps bad input to clear JSON errors: {@code {"status":400,"error":"...","details":[...]}}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(int status, String error, List<String> details) {
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> validation(ValidationException e) {
        return bad(e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return bad("Parameter '" + e.getName() + "' has an invalid value: '" + e.getValue() + "'.", List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiError> missing(Exception e) {
        return bad(e.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return bad("Request body is not valid JSON for this endpoint.", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError(413, "The uploaded file is too large (limit 64 MB).", List.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e) {
        return bad("Unsupported Content-Type " + e.getContentType()
                + ". Send the CSV as multipart/form-data in a field named 'file'.", List.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> multipart(MultipartException e) {
        return bad("Send the CSV as multipart/form-data in a field named 'file'.", List.of());
    }

    private static ResponseEntity<ApiError> bad(String message, List<String> details) {
        return ResponseEntity.badRequest().body(new ApiError(400, message, details));
    }
}
