package com.chen404.domain;

/** 列表请求的共同资源边界；页大小可按用例选择默认值，最大值统一限制。 */
public record PageBounds(long current, long size) {
    public static final int DEFAULT_SIZE = 10;
    public static final int ADMIN_DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageBounds {
        if (current < 1 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("分页参数超出允许范围");
        }
    }

    /** 保留缺省及非正数回退行为，超大页大小收敛到服务上限。 */
    public static PageBounds of(Integer page, Integer size, int defaultSize) {
        long normalizedPage = page == null || page < 1 ? 1L : page.longValue();
        long normalizedSize = size == null || size < 1 ? defaultSize : Math.min(size, MAX_SIZE);
        return new PageBounds(normalizedPage, normalizedSize);
    }
}
