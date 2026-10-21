package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

public class InternalTransferUnlinkCommand implements Serializable {

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
