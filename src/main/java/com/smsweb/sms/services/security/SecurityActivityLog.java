package com.smsweb.sms.services.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * A short, in-memory list of the serious sign-in and API events of the last 24 hours (account locks, address blocks,
 * API callers held off), shown to admins on the App Config screen. Counts and masked names only: no password, no
 * guess, no full mobile number. Bounded (at most {@value #MAX_EVENTS} events) and cleared on restart, like every
 * other counter of the throttle. Nothing here ever throws or blocks a sign-in.
 */
@Component
public class SecurityActivityLog {

    public enum Type {
        /** A staff (or student-portal) web sign-in or change-password was locked. */
        ACCOUNT_LOCK,
        /** A parent mobile number was locked. */
        PARENT_LOCK,
        /** An address was blocked (many wrong attempts, or many different accounts). */
        IP_BLOCK,
        /** An API caller stayed over the request limit for several minutes and was held off. */
        API_HELD
    }

    /**
     * @param staffKey lower-cased username of a web sign-in lock, used only to find the account's branch when the
     *                 list is shown; never sent to the browser (null for every other event)
     * @param who      masked name, number or address, safe to show
     */
    public record Event(long at, Type type, String who, String staffKey, int level, long lockMinutes, int accounts,
                        boolean enforced) {
    }

    static final int MAX_EVENTS = 300;
    static final long KEEP_MS = 24L * 60 * 60 * 1000;

    private final LongSupplier clock;
    private final Deque<Event> events = new ArrayDeque<>();

    public SecurityActivityLog() {
        this(System::currentTimeMillis);
    }

    SecurityActivityLog(LongSupplier clock) {
        this.clock = clock;
    }

    public void accountLock(String maskedName, String staffKey, int level, long lockMinutes, boolean enforced) {
        add(new Event(clock.getAsLong(), Type.ACCOUNT_LOCK, maskedName, staffKey, level, lockMinutes, 0, enforced));
    }

    public void parentLock(String maskedMobile, int level, long lockMinutes) {
        add(new Event(clock.getAsLong(), Type.PARENT_LOCK, maskedMobile, null, level, lockMinutes, 0, true));
    }

    public void ipBlock(String ip, int level, long lockMinutes, int differentAccounts, boolean enforced) {
        add(new Event(clock.getAsLong(), Type.IP_BLOCK, ip, null, level, lockMinutes, differentAccounts, enforced));
    }

    public void apiHeld(String who, long holdMinutes, boolean enforced) {
        add(new Event(clock.getAsLong(), Type.API_HELD, who, null, 0, holdMinutes, 0, enforced));
    }

    private void add(Event e) {
        try {
            synchronized (events) {
                events.addFirst(e);
                prune(e.at());
                while (events.size() > MAX_EVENTS) {
                    events.removeLast();
                }
            }
        } catch (RuntimeException ignored) {
            // the activity list is only a convenience; it must never affect a sign-in
        }
    }

    private void prune(long now) {
        while (!events.isEmpty() && now - events.peekLast().at() > KEEP_MS) {
            events.removeLast();
        }
    }

    /** The events of the last 24 hours, newest first. */
    public List<Event> recent() {
        try {
            synchronized (events) {
                prune(clock.getAsLong());
                return new ArrayList<>(events);
            }
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }
}
