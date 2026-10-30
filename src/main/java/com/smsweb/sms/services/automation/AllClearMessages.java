package com.smsweb.sms.services.automation;

import java.util.List;
import java.util.Random;

/**
 * NEW helper (feature: absence report). The cheerful lines used when nobody is on a long absence streak.
 * One is picked at random per email, never the same one twice in a row for a school. "{days}" is replaced
 * with the configured threshold. (An editable per-school list is a planned later add-on.)
 */
public final class AllClearMessages {

    private AllClearMessages() {}

    public static final List<String> DEFAULTS = List.of(
            "All clear! Every student has been in class. No one has been absent for {days} or more consecutive school days.",
            "Great news! Zero students are on a {days}-day absence streak. Attendance is looking healthy.",
            "Gold star for your school! No student has missed {days} or more school days in a row.",
            "Nothing to chase today! No student has been away for {days} or more consecutive school days. Enjoy the calm.",
            "Full house! Every student is showing up, and nobody has a {days}-day absence streak.",
            "Green across the board! No long absences to report. Keep up the great work!"
    );

    /** Random index into DEFAULTS that differs from {@code lastIndex} whenever there is more than one message. */
    public static int nextIndex(Random random, int lastIndex) {
        int size = DEFAULTS.size();
        if (size <= 1) {
            return 0;
        }
        int next = random.nextInt(size);
        if (next == lastIndex) {
            next = (next + 1 + random.nextInt(size - 1)) % size;
        }
        return next;
    }

    public static String render(int index, int days) {
        return DEFAULTS.get(Math.floorMod(index, DEFAULTS.size())).replace("{days}", String.valueOf(days));
    }
}
