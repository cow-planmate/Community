package com.planmate.community.domain.report.dto;

import com.planmate.community.domain.report.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull ReportReason reason,
        @Size(max = 1000) String details
) {}
