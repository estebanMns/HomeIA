package com.home.ia.domain.policy;

import com.home.ia.domain.exception.ProtectedOutletException;
import com.home.ia.domain.model.device.SmartOutlet;

public class OutletProtectionPolicy {

    public boolean canDisableAutomatically(SmartOutlet outlet) {
        return outlet.isOnline()
                && outlet.isOn()
                && outlet.canBeDisabledAutomatically();
    }

    public void ensureCanDisableAutomatically(SmartOutlet outlet) {
        if (!outlet.canBeDisabledAutomatically()) {
            throw new ProtectedOutletException(outlet.getId());
        }
    }
}