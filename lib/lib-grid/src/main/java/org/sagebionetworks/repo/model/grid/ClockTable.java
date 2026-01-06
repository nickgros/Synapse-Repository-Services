package org.sagebionetworks.repo.model.grid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

/**
 * Clock table for serializing to a binary format described by the JSON CRDT specification. The clock table is directly
 * encoded into the serialized document, and is also used referentially to serialize every timestamp in the document.
 * <br/>
 * This implementation currently supports the Indexed Binary format. In the Indexed Binary format, timestamps are encoded
 * in two parts. The replica ID is encoded as an index which references an entry in the clock table, and the sequence number
 * is encoded as-is as a variable-length unsigned integer.
 * <br />
 * Note that the Binary Structural format (which is not currently supported) encodes the sequence number as a delta from the
 * current clock table value.
 */
public class ClockTable {
    private final List<LogicalTimestamp> clocks;

    public ClockTable(List<LogicalTimestamp> clocks) {
        this.clocks = clocks;
    }

    /**
     * Get the list of clocks in the clock table.
     *
     * @return the clocks
     */
    public List<LogicalTimestamp> getClocks() {
        return clocks;
    }

    /**
     * Serializes the clock table to a binary format.
     *
     * @return the binary-encoded clock table
     */
    public byte[] toBinary() {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(EncodingUtils.encodeVu57(clocks.size()));
            for (LogicalTimestamp clock : clocks) {
                outputStream.write(clock.toBinary());
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode ClockTable to binary", e);
        }
    }


    /**
     * Decodes a binary-encoded timestamp from a byte array which references the clock table.
     *
     * @param bytes the byte array containing the encoded root reference
     * @return the decoded LogicalTimestamp
     */
    public LogicalTimestamp decodeTimestamp(byte[] bytes) {
        try {
            return decodeTimestamp(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode root reference", e);
        }
    }

    /**
     * Encodes a timestamp where the replica ID is mapped to a session index in the clock table.
     *
     * @param timestamp the timestamp to encode
     * @return the encoded bytes
     */
    public byte[] encodeTimestamp(LogicalTimestamp timestamp) {
        long sessionIndex = findSessionIndex(timestamp.getReplicaId());

        // Calculate the value to encode based on mode
        long valueToEncode = timestamp.getSequenceNumber();

        // Use compact single-byte encoding if possible
        if (sessionIndex < 8 && valueToEncode < 16) {
            // b1u3u4 encoding: flag=0, 3 bits for session index, 4 bits for value
            int encodedByte = (int) ((sessionIndex << 4) | valueToEncode);
            return new byte[] { (byte) encodedByte };
        }

        // Use multi-byte encoding
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            EncodingUtils.encodeB1u56(true, sessionIndex, outputStream);
            outputStream.write(EncodingUtils.encodeVu57(valueToEncode));
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode timestamp", e);
        }
    }

    /**
     * Internal method to decode a timestamp with specified encoding mode.
     *
     * @param in the input stream to read from
     * @return the decoded LogicalTimestamp
     * @throws IOException if an I/O error occurs
     */
    public LogicalTimestamp decodeTimestamp(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) {
            throw new IOException("Unexpected end of stream while decoding timestamp");
        }

        long sessionIndex;
        long encodedValue;

        if ((firstByte & 0x80) == 0) {
            // Single-byte b1u3u4 encoding
            sessionIndex = (firstByte >> 4) & 0x07;
            encodedValue = firstByte & 0x0F;
        } else {
            // Multi-byte encoding
            java.io.ByteArrayInputStream tempIn = new java.io.ByteArrayInputStream(new byte[] { (byte) firstByte });
            java.io.SequenceInputStream combinedIn = new java.io.SequenceInputStream(tempIn, in);
            EncodingUtils.B1u56Result b1u56Result = EncodingUtils.decodeB1u56(combinedIn);
            sessionIndex = b1u56Result.getValue();
            encodedValue = EncodingUtils.decodeVu57(in);
        }

        if (sessionIndex >= clocks.size()) {
            throw new IllegalArgumentException("Session index out of bounds: " + sessionIndex + " >= " + clocks.size());
        }

        LogicalTimestamp clockEntry = clocks.get((int) sessionIndex);

        // Calculate final sequence number based on mode
        long sequenceNumber = encodedValue;

        return new LogicalTimestamp()
            .setReplicaId(clockEntry.getReplicaId())
            .setSequenceNumber(sequenceNumber);
    }

    /**
     * Finds the session index for a given replica ID.
     *
     * @param replicaId the replica ID to find
     * @return the session index
     * @throws IllegalArgumentException if replica ID not found
     */
    private long findSessionIndex(Long replicaId) {
        for (int i = 0; i < clocks.size(); i++) {
            if (clocks.get(i).getReplicaId().equals(replicaId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Timestamp replicaId not found in clock table: " + replicaId);
    }
}
