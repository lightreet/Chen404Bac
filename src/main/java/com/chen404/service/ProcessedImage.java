package com.chen404.service;

/**
 * 压缩后的图片字节与元数据
 */
public record ProcessedImage(byte[] bytes, String contentType, String extension) {}
