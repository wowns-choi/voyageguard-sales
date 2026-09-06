package com.voyageguard.sales.domain.inventory;

import com.voyageguard.common.exception.BusinessException;

public class InsufficientInventoryException extends BusinessException {
    public InsufficientInventoryException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "INSUFFICIENT_INVENTORY";
    }
}
