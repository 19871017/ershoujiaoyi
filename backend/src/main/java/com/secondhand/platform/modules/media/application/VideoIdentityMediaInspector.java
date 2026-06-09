package com.secondhand.platform.modules.media.application;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

public final class VideoIdentityMediaInspector {
    public static final double MAX_VIDEO_IDENTITY_DURATION_SECONDS = 10.0;
    private static final int MAX_BOX_SCAN_DEPTH = 8;
    private static final Set<String> MP4_CONTAINER_BOXES = Set.of("moov", "trak", "mdia", "minf", "stbl", "edts", "udta", "meta");

    private VideoIdentityMediaInspector() {
    }

    public static double requireValidVideoIdentityMedia(Path mediaPath) {
        double durationSeconds = readDurationSeconds(mediaPath);
        if (durationSeconds > MAX_VIDEO_IDENTITY_DURATION_SECONDS) {
            throw new IllegalArgumentException("video identity media invalid");
        }
        return durationSeconds;
    }

    public static double readDurationSeconds(Path mediaPath) {
        if (mediaPath == null || !Files.isRegularFile(mediaPath)) {
            throw new IllegalArgumentException("video identity media invalid");
        }
        try (SeekableByteChannel channel = Files.newByteChannel(mediaPath, StandardOpenOption.READ)) {
            Mp4Inspection inspection = new Mp4Inspection();
            scanMp4Boxes(channel, 0, channel.size(), 0, inspection);
            if (!inspection.isValid()) {
                throw new IllegalArgumentException("video identity media invalid");
            }
            return inspection.durationSeconds;
        } catch (IOException exception) {
            throw new IllegalArgumentException("video identity media invalid", exception);
        }
    }

    private static void scanMp4Boxes(SeekableByteChannel channel, long start, long end, int depth, Mp4Inspection inspection) throws IOException {
        if (depth > MAX_BOX_SCAN_DEPTH || start < 0 || end <= start) {
            return;
        }
        long position = start;
        while (position + 8 <= end) {
            ByteBuffer header = readBytes(channel, position, 8);
            long boxSize = Integer.toUnsignedLong(header.getInt());
            String boxType = fourCc(header);
            long headerSize = 8;
            if (boxSize == 1) {
                if (position + 16 > end) {
                    return;
                }
                ByteBuffer extendedSize = readBytes(channel, position + 8, 8);
                boxSize = extendedSize.getLong();
                headerSize = 16;
            } else if (boxSize == 0) {
                boxSize = end - position;
            }
            if (boxSize < headerSize || boxSize > end - position) {
                return;
            }
            long contentStart = position + headerSize;
            long boxEnd = position + boxSize;
            long contentLength = boxEnd - contentStart;
            if (depth == 0 && "moov".equals(boxType)) {
                inspection.hasMovie = true;
            }
            if (depth == 0 && "mdat".equals(boxType) && contentLength > 0) {
                inspection.hasMediaData = true;
            }
            if ("mvhd".equals(boxType)) {
                double durationSeconds = parseMvhdDurationSeconds(channel, contentStart, contentLength);
                if (Double.isFinite(durationSeconds) && durationSeconds > 0) {
                    inspection.durationSeconds = durationSeconds;
                }
            }
            if ("hdlr".equals(boxType) && isVideoHandler(channel, contentStart, contentLength)) {
                inspection.hasVideoTrack = true;
            }
            if (MP4_CONTAINER_BOXES.contains(boxType)) {
                long nestedStart = contentStart;
                if ("meta".equals(boxType) && contentLength > 4) {
                    nestedStart += 4;
                }
                scanMp4Boxes(channel, nestedStart, boxEnd, depth + 1, inspection);
            }
            position = boxEnd;
        }
    }

    private static double parseMvhdDurationSeconds(SeekableByteChannel channel, long contentStart, long contentLength) throws IOException {
        if (contentLength < 24) {
            return Double.NaN;
        }
        ByteBuffer versionBuffer = readBytes(channel, contentStart, 4);
        int version = Byte.toUnsignedInt(versionBuffer.get());
        if (version == 0) {
            ByteBuffer values = readBytes(channel, contentStart + 12, 8);
            long timescale = Integer.toUnsignedLong(values.getInt());
            long duration = Integer.toUnsignedLong(values.getInt());
            return durationSeconds(timescale, duration);
        }
        if (version == 1 && contentLength >= 36) {
            ByteBuffer values = readBytes(channel, contentStart + 20, 12);
            long timescale = Integer.toUnsignedLong(values.getInt());
            long duration = values.getLong();
            if (duration < 0) {
                return Double.NaN;
            }
            return durationSeconds(timescale, duration);
        }
        return Double.NaN;
    }

    private static double durationSeconds(long timescale, long duration) {
        if (timescale <= 0 || duration <= 0) {
            return Double.NaN;
        }
        return (double) duration / (double) timescale;
    }

    private static boolean isVideoHandler(SeekableByteChannel channel, long contentStart, long contentLength) throws IOException {
        if (contentLength < 12) {
            return false;
        }
        ByteBuffer handlerBuffer = readBytes(channel, contentStart + 8, 4);
        return "vide".equals(fourCc(handlerBuffer));
    }

    private static ByteBuffer readBytes(SeekableByteChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        channel.position(position);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("video identity media truncated");
            }
        }
        buffer.flip();
        return buffer;
    }

    private static String fourCc(ByteBuffer buffer) {
        byte[] bytes = new byte[4];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static final class Mp4Inspection {
        private boolean hasMovie;
        private boolean hasVideoTrack;
        private boolean hasMediaData;
        private double durationSeconds = Double.NaN;

        private boolean isValid() {
            return hasMovie
                    && hasVideoTrack
                    && hasMediaData
                    && Double.isFinite(durationSeconds)
                    && durationSeconds > 0
                    && durationSeconds <= MAX_VIDEO_IDENTITY_DURATION_SECONDS;
        }
    }
}
