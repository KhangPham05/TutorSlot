package com.tutorslot.service;

import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import com.tutorslot.model.AvailabilitySlot;
import com.tutorslot.repository.AppointmentRepository;
import com.tutorslot.repository.AvailabilitySlotRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BookingService {

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AppointmentRepository appointmentRepository;

    public BookingService(AvailabilitySlotRepository availabilitySlotRepository,
                           AppointmentRepository appointmentRepository) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // READ COMMITTED (Postgres's default, set explicitly so it's visible here): the row lock
    // below blocks a second booking attempt on the same slot until this transaction commits or
    // rolls back. Because READ COMMITTED re-reads on every new statement, the second
    // transaction's hasActiveBooking check then sees this transaction's committed row and
    // correctly reports a conflict. Under REPEATABLE READ, that check would still see the old
    // snapshot from before the lock was acquired and miss the booking -- only the unique index
    // backstop below would catch it.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long book(Long slotId, Long customerId, String notes) {
        AvailabilitySlot slot = availabilitySlotRepository.lockForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException("No slot with id " + slotId));

        if (!slot.startTime().isAfter(LocalDateTime.now())) {
            throw new SlotConflictException("This slot is in the past.");
        }
        if (appointmentRepository.hasActiveBooking(slotId)) {
            throw new SlotConflictException("This slot is already booked.");
        }

        try {
            return appointmentRepository.insertBooked(slotId, customerId, notes);
        } catch (DuplicateKeyException e) {
            // Backstop: uq_one_active_booking_per_slot caught a race the checks above missed.
            throw new SlotConflictException("This slot is already booked.");
        }
    }
}
