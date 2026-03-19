package com.wsi.platform.enums;

/**
 * Supported export formats.
 */
public enum ExportFormat {
    COCO,
    YOLO,
    VOC,
    GEOJSON,
    MASK_ONLY,
    PATCHES;

    /** Parse from string, default to COCO if null or unknown. */
    public static ExportFormat fromString(String s) {
        if (s == null || s.isBlank()) return COCO;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COCO;
        }
    }
}
