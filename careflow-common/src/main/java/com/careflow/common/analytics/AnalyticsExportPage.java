package com.careflow.common.analytics;
import java.time.Instant;
import java.util.*;
public record AnalyticsExportPage(List<AnalyticsSnapshot> items, UUID nextCursor, Instant capturedAt) {}
