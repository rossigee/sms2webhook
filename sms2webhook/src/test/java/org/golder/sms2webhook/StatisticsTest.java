package org.golder.sms2webhook;

import org.junit.Test;

import static org.junit.Assert.*;

public class StatisticsTest {

    @Test
    public void progress_zeroTotal_returnsZero() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(0, 0, 0);
        assertEquals(0, stats.progress);
    }

    @Test
    public void progress_allSent_returns100() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(10, 10, 0);
        assertEquals(100, stats.progress);
    }

    @Test
    public void progress_halfSent_returns50() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(10, 5, 0);
        assertEquals(50, stats.progress);
    }

    @Test
    public void progress_noneSent_returnsZero() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(10, 0, 0);
        assertEquals(0, stats.progress);
    }

    @Test
    public void progress_integerDivision_roundsDown() {
        // 7 / 10 = 70, not 70.0
        MainViewModel.Statistics stats = new MainViewModel.Statistics(10, 7, 3);
        assertEquals(70, stats.progress);
    }

    @Test
    public void progress_singleItemSent_returns100() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(1, 1, 0);
        assertEquals(100, stats.progress);
    }

    @Test
    public void fields_storedCorrectly() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(100, 75, 25);
        assertEquals(100, stats.totalCount);
        assertEquals(75, stats.sentCount);
        assertEquals(25, stats.unsentCount);
    }

    @Test
    public void progress_largeNumbers_calculatedCorrectly() {
        MainViewModel.Statistics stats = new MainViewModel.Statistics(10000, 9999, 1);
        assertEquals(99, stats.progress);
    }
}
