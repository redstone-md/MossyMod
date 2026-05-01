package md.redstone.gui;

import net.minecraft.network.chat.Component;

public final class MossyTime {
    private static final long SECOND_MS = 1000L;
    private static final long MINUTE_MS = 60L * SECOND_MS;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;
    private static final long MONTH_MS = 30L * DAY_MS;
    private static final long YEAR_MS = 365L * DAY_MS;

    private MossyTime() {
    }

    public static Component age(long timestamp) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - timestamp);
        if (elapsed < 2L * SECOND_MS) {
            return MossyText.tr("time.now");
        }
        if (elapsed < MINUTE_MS) {
            return MossyText.tr("time.secondsAgo", Math.max(1L, elapsed / SECOND_MS));
        }
        if (elapsed < HOUR_MS) {
            return MossyText.tr("time.minutesAgo", elapsed / MINUTE_MS);
        }
        if (elapsed < DAY_MS) {
            return MossyText.tr("time.hoursAgo", elapsed / HOUR_MS);
        }
        if (elapsed < MONTH_MS) {
            return MossyText.tr("time.daysAgo", elapsed / DAY_MS);
        }
        if (elapsed < YEAR_MS) {
            return MossyText.tr("time.monthsAgo", elapsed / MONTH_MS);
        }
        return MossyText.tr("time.yearsAgo", elapsed / YEAR_MS);
    }
}
