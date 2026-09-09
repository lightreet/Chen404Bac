package com.chen404.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageBoundsTest {
    @Test
    void boundsPageSizeAndPreservesLargePageWithoutIntegerOffsetOverflow() {
        assertEquals(new PageBounds(1, 10), PageBounds.of(null, -1, PageBounds.DEFAULT_SIZE));
        assertEquals(new PageBounds(1, 20), PageBounds.of(0, null, PageBounds.ADMIN_DEFAULT_SIZE));
        PageBounds bounds = PageBounds.of(Integer.MAX_VALUE, Integer.MAX_VALUE, PageBounds.DEFAULT_SIZE);
        assertEquals(100, bounds.size());
        assertEquals(214748364600L, (bounds.current() - 1) * bounds.size());
    }
}
