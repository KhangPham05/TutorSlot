package com.tutorslot.mocknotification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

// The mock service's current mode, changeable at runtime through the admin endpoint -- no
// restart needed, which matters because a restart resets the database.
@Component
public class NotificationModeHolder {

    private final AtomicReference<NotificationMode> mode;

    public NotificationModeHolder(@Value("${mock.notification.mode:UP}") NotificationMode initialMode) {
        this.mode = new AtomicReference<>(initialMode);
    }

    public NotificationMode get() {
        return mode.get();
    }

    public void set(NotificationMode newMode) {
        mode.set(newMode);
    }
}
