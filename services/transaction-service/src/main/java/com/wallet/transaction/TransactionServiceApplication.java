package com.wallet.transaction;

import com.wallet.common.dto.ApiResponse;
import com.wallet.common.exception.ServiceException;
import feign.FeignException;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@SpringBootApplication(scanBasePackages = "com.wallet")
@EnableDiscoveryClient
@EnableFeignClients
public class TransactionServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TransactionServiceApplication.class, args);
    }

    @RestControllerAdvice
    static class GlobalExceptionHandler {
        @ExceptionHandler(ServiceException.class)
        public ResponseEntity<ApiResponse<?>> handleServiceException(ServiceException ex) {
            return ResponseEntity.status(ex.getHttpStatus())
                    .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
        }

        @ExceptionHandler(FeignException.class)
        public ResponseEntity<ApiResponse<?>> handleFeignException(FeignException ex) {
            HttpStatus status = ex.status() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            String message = ex.status() == 404 ? "Requested resource not found in downstream service"
                    : "Downstream service error";
            String errorCode = ex.status() == 404 ? "DOWNSTREAM_NOT_FOUND" : "DOWNSTREAM_ERROR";

            return ResponseEntity.status(status)
                    .body(ApiResponse.error(message, errorCode));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal error", "INTERNAL_ERROR"));
        }
    }

}
