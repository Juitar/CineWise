package com.miaoyu.ticket.ticketing.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** A 配置的本地沙箱事实，绝不采用 Provider 的标价、余座或影厅身份。 */
@Validated
@ConfigurationProperties("cinewise.ticketing.sandbox-import")
public record ExternalShowtimeSandboxImportProperties(
        @DecimalMin(value = "0.00") BigDecimal basePrice,
        @Min(1) @Max(50) int rowCount,
        @Min(1) @Max(10) int seatsPerRow,
        @NotBlank String languageVersion) { }
