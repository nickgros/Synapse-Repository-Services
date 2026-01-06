package org.sagebionetworks.repo.model.grid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for encoding and decoding vu57 (Variable Length Unsigned 57-bit Integer).
 *
 * vu57 encoding uses 1-8 bytes where:
 * - Each byte has 7 bits of data and 1 continuation bit (high bit)
 * - Continuation bit = 1 means another byte follows
 * - Continuation bit = 0 means this is the last byte
 * - Maximum value: 2^57 - 1
 */
public class EncodingUtils {

  private static final int CONTINUATION_BIT = 0x80; // 10000000
  private static final int DATA_MASK = 0x7F;        // 01111111
  private static final long MAX_VU57_VALUE = (1L << 57) - 1; // 2^57 - 1

  // b1u56 constants
  private static final int B1U56_FLAG_BIT = 0x80;              // 10000000 - flag bit in first byte
  private static final int B1U56_FIRST_CONTINUATION = 0x40;   // 01000000 - continuation bit in first byte
  private static final int B1U56_FIRST_DATA_MASK = 0x3F;      // 00111111 - 6 data bits in first byte
  private static final long MAX_B1U56_VALUE = (1L << 56) - 1; // 2^56 - 1

  private static void validateVu57Value(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("Value must be non-negative: " + value);
    }
    if (value > MAX_VU57_VALUE) {
      throw new IllegalArgumentException("Value exceeds 57 bits: " + value);
    }
  }

  private static void validateB1u56Value(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("Value must be non-negative: " + value);
    }
    if (value > MAX_B1U56_VALUE) {
      throw new IllegalArgumentException("Value exceeds 56 bits: " + value);
    }
  }

  /**
   * Encodes a long value as vu57 and writes it to the output stream.
   *
   * @param value the unsigned 57-bit integer to encode (must be in range [0, 2^57-1])
   * @param out the output stream to write to
   * @throws IOException if an I/O error occurs
   * @throws IllegalArgumentException if value is negative or exceeds 57 bits
   */
  public static void encodeVu57(long value, OutputStream out) throws IOException {
    validateVu57Value(value);

    // Encode with continuation bits
    // Process 7 bits at a time from least significant to most significant
    int byteCount = 0;
    while (value > 0x7F && byteCount < 7) {
      // More bytes follow: set continuation bit
      out.write((int) (value & DATA_MASK) | CONTINUATION_BIT);
      value >>>= 7; // Unsigned right shift
      byteCount++;
    }

    // Last byte: no continuation bit
    // If we've written 7 bytes, the 8th byte can use all 8 bits (mask to ensure it's within 0xFF)
    // Otherwise, the last byte will be 0-127 (fits in 7 bits, but we write it without continuation bit)
    out.write((int) (value & 0xFF));
  }

  /**
   * Encodes a long value as vu57 and returns it as a byte array.
   *
   * @param value the unsigned 57-bit integer to encode (must be in range [0, 2^57-1])
   * @return the encoded byte array
   * @throws IllegalArgumentException if value is negative or exceeds 57 bits
   */
  public static byte[] encodeVu57(long value) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
    try {
      encodeVu57(value, baos);
    } catch (IOException e) {
      // ByteArrayOutputStream doesn't throw IOException
      throw new RuntimeException("Unexpected IOException", e);
    }
    return baos.toByteArray();
  }

  /**
   * Decodes a vu57 encoded value from the input stream.
   *
   * @param in the input stream to read from
   * @return the decoded unsigned 57-bit integer
   * @throws IOException if an I/O error occurs or end of stream is reached unexpectedly
   * @throws IllegalArgumentException if the encoded value exceeds 57 bits
   */
  public static long decodeVu57(InputStream in) throws IOException {
    long result = 0;
    int shift = 0;
    int byteCount = 0;

    while (true) {
      int b = in.read();
      if (b == -1) {
        throw new IOException("Unexpected end of stream while decoding vu57");
      }

      byteCount++;
      if (byteCount > 8) {
        throw new IllegalArgumentException("Invalid vu57 encoding: more than 8 bytes");
      }

      // For the 8th byte, use all 8 bits (it never has a continuation bit)
      if (byteCount == 8) {
        // 8th byte: use all 8 bits, no continuation bit
        long dataBits = b & 0xFF;
        result |= (dataBits << shift);
        break; // 8th byte is always the last byte
      }

      // Bytes 1-7: check continuation bit and extract 7 data bits
      boolean hasContinuation = (b & CONTINUATION_BIT) != 0;
      long dataBits = b & DATA_MASK;

      // Add the data bits to the result
      result |= (dataBits << shift);

      if (!hasContinuation) {
        // This was the last byte
        break;
      }

      shift += 7;
    }

    return result;
  }

  /**
   * Decodes a vu57 encoded value from a byte array.
   *
   * @param bytes the byte array containing the encoded value
   * @param offset the starting offset in the byte array
   * @return the decoded unsigned 57-bit integer
   * @throws IllegalArgumentException if the encoded value is invalid or exceeds 57 bits
   */
  public static long decodeVu57(byte[] bytes, int offset) {
    long result = 0;
    int shift = 0;
    int byteCount = 0;
    int index = offset;

    while (index < bytes.length) {
      int b = bytes[index++] & 0xFF; // Convert to unsigned
      byteCount++;

      if (byteCount > 8) {
        throw new IllegalArgumentException("Invalid vu57 encoding: more than 8 bytes");
      }

      // For the 8th byte, use all 8 bits (it never has a continuation bit)
      if (byteCount == 8) {
        // 8th byte: use all 8 bits, no continuation bit
        long dataBits = b & 0xFF;
        result |= (dataBits << shift);
        break; // 8th byte is always the last byte
      }

      // Bytes 1-7: check continuation bit and extract 7 data bits
      boolean hasContinuation = (b & CONTINUATION_BIT) != 0;
      long dataBits = b & DATA_MASK;

      // Add the data bits to the result
      result |= (dataBits << shift);

      if (!hasContinuation) {
        // This was the last byte
        break;
      }

      shift += 7;
    }

    return result;
  }

  /**
   * Decodes a vu57 encoded value from a byte array starting at offset 0.
   *
   * @param bytes the byte array containing the encoded value
   * @return the decoded unsigned 57-bit integer
   * @throws IllegalArgumentException if the encoded value is invalid or exceeds 57 bits
   */
  public static long decodeVu57(byte[] bytes) {
    return decodeVu57(bytes, 0);
  }

  /**
   * Calculates the number of bytes required to encode a value as vu57.
   *
   * @param value the unsigned 57-bit integer
   * @return the number of bytes required (1-8)
   * @throws IllegalArgumentException if value is negative or exceeds 57 bits
   */
  public static int getVu57EncodedSize(long value) {
    validateVu57Value(value);

    if (value == 0) {
      return 1;
    }

    // Calculate the number of significant bits
    int significantBits = 64 - Long.numberOfLeadingZeros(value);

    // First 7 bytes can hold 7 bits each (49 bits total)
    // 8th byte can hold 8 bits
    // So: 1-7 bits = 1 byte, 8-14 bits = 2 bytes, ..., 50-57 bits = 8 bytes
    if (significantBits <= 49) {
      // Fits in 1-7 bytes (7 bits per byte)
      return (significantBits + 6) / 7; // Ceiling division
    } else {
      // Requires 8 bytes (49 bits in first 7 bytes, up to 8 more bits in 8th byte)
      return 8;
    }
  }

  // ==================== b1u56 Encoding ====================

  /**
   * Result of decoding a b1u56 value.
   */
  public static class B1u56Result {
    private final boolean flag;
    private final long value;

    public B1u56Result(boolean flag, long value) {
      this.flag = flag;
      this.value = value;
    }

    public boolean getFlag() {
      return flag;
    }

    public long getValue() {
      return value;
    }
  }

  /**
   * Encodes a boolean flag and a long value as b1u56 and writes it to the output stream.
   *
   * b1u56 encoding:
   * - Byte 1: flag bit (bit 7), continuation bit (bit 6), 6 data bits (bits 0-5)
   * - Bytes 2-7: continuation bit (bit 7), 7 data bits (bits 0-6)
   * - Byte 8: 8 data bits (no continuation bit)
   *
   * @param flag the boolean flag to encode
   * @param value the unsigned 56-bit integer to encode (must be in range [0, 2^56-1])
   * @param out the output stream to write to
   * @throws IOException if an I/O error occurs
   * @throws IllegalArgumentException if value is negative or exceeds 56 bits
   */
  public static void encodeB1u56(boolean flag, long value, OutputStream out) throws IOException {
    validateB1u56Value(value);

    // First byte: flag | continuation | 6 data bits
    int firstByte = flag ? B1U56_FLAG_BIT : 0;
    int dataBits = (int) (value & B1U56_FIRST_DATA_MASK);
    value >>>= 6;

    if (value == 0) {
      // Single byte - no continuation needed
      firstByte |= dataBits;
      out.write(firstByte);
      return;
    }

    // More bytes follow - set continuation bit
    firstByte |= B1U56_FIRST_CONTINUATION | dataBits;
    out.write(firstByte);

    // Encode remaining bits using 7-bit chunks (bytes 2-7)
    int byteCount = 1;
    while (value > 0x7F && byteCount < 7) {
      out.write((int) (value & DATA_MASK) | CONTINUATION_BIT);
      value >>>= 7;
      byteCount++;
    }

    // Last byte: no continuation bit
    out.write((int) (value & 0xFF));
  }

  /**
   * Encodes a boolean flag and a long value as b1u56 and returns it as a byte array.
   *
   * @param flag the boolean flag to encode
   * @param value the unsigned 56-bit integer to encode (must be in range [0, 2^56-1])
   * @return the encoded byte array
   * @throws IllegalArgumentException if value is negative or exceeds 56 bits
   */
  public static byte[] encodeB1u56(boolean flag, long value) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8);
    try {
      encodeB1u56(flag, value, baos);
    } catch (IOException e) {
      // ByteArrayOutputStream doesn't throw IOException
      throw new RuntimeException("Unexpected IOException", e);
    }
    return baos.toByteArray();
  }

  /**
   * Decodes a b1u56 encoded value from the input stream.
   *
   * @param in the input stream to read from
   * @return the decoded B1u56Result containing the flag and value
   * @throws IOException if an I/O error occurs or end of stream is reached unexpectedly
   * @throws IllegalArgumentException if the encoded value exceeds 56 bits
   */
  public static B1u56Result decodeB1u56(InputStream in) throws IOException {
    int firstByte = in.read();
    if (firstByte == -1) {
      throw new IOException("Unexpected end of stream while decoding b1u56");
    }

    boolean flag = (firstByte & B1U56_FLAG_BIT) != 0;
    boolean hasContinuation = (firstByte & B1U56_FIRST_CONTINUATION) != 0;
    long result = firstByte & B1U56_FIRST_DATA_MASK;

    if (!hasContinuation) {
      return new B1u56Result(flag, result);
    }

    int shift = 6;
    int byteCount = 1;

    while (true) {
      int b = in.read();
      if (b == -1) {
        throw new IOException("Unexpected end of stream while decoding b1u56");
      }

      byteCount++;
      if (byteCount > 8) {
        throw new IllegalArgumentException("Invalid b1u56 encoding: more than 8 bytes");
      }

      // For the 8th byte, use all 8 bits (it never has a continuation bit)
      if (byteCount == 8) {
        long dataBits = b & 0xFF;
        result |= (dataBits << shift);
        break;
      }

      // Bytes 2-7: check continuation bit and extract 7 data bits
      boolean nextHasContinuation = (b & CONTINUATION_BIT) != 0;
      long dataBits = b & DATA_MASK;
      result |= (dataBits << shift);

      if (!nextHasContinuation) {
        break;
      }

      shift += 7;
    }

    return new B1u56Result(flag, result);
  }

  /**
   * Decodes a b1u56 encoded value from a byte array.
   *
   * @param bytes the byte array containing the encoded value
   * @param offset the starting offset in the byte array
   * @return the decoded B1u56Result containing the flag and value
   * @throws IllegalArgumentException if the encoded value is invalid or exceeds 56 bits
   */
  public static B1u56Result decodeB1u56(byte[] bytes, int offset) {
    if (offset >= bytes.length) {
      throw new IllegalArgumentException("Offset beyond array length");
    }

    int firstByte = bytes[offset++] & 0xFF;
    boolean flag = (firstByte & B1U56_FLAG_BIT) != 0;
    boolean hasContinuation = (firstByte & B1U56_FIRST_CONTINUATION) != 0;
    long result = firstByte & B1U56_FIRST_DATA_MASK;

    if (!hasContinuation) {
      return new B1u56Result(flag, result);
    }

    int shift = 6;
    int byteCount = 1;

    while (offset < bytes.length) {
      int b = bytes[offset++] & 0xFF;
      byteCount++;

      if (byteCount > 8) {
        throw new IllegalArgumentException("Invalid b1u56 encoding: more than 8 bytes");
      }

      // For the 8th byte, use all 8 bits (it never has a continuation bit)
      if (byteCount == 8) {
        long dataBits = b & 0xFF;
        result |= (dataBits << shift);
        break;
      }

      // Bytes 2-7: check continuation bit and extract 7 data bits
      boolean nextHasContinuation = (b & CONTINUATION_BIT) != 0;
      long dataBits = b & DATA_MASK;
      result |= (dataBits << shift);

      if (!nextHasContinuation) {
        break;
      }

      shift += 7;
    }

    return new B1u56Result(flag, result);
  }

  /**
   * Decodes a b1u56 encoded value from a byte array starting at offset 0.
   *
   * @param bytes the byte array containing the encoded value
   * @return the decoded B1u56Result containing the flag and value
   * @throws IllegalArgumentException if the encoded value is invalid or exceeds 56 bits
   */
  public static B1u56Result decodeB1u56(byte[] bytes) {
    return decodeB1u56(bytes, 0);
  }

  /**
   * Calculates the number of bytes required to encode a value as b1u56.
   *
   * @param value the unsigned 56-bit integer
   * @return the number of bytes required (1-8)
   * @throws IllegalArgumentException if value is negative or exceeds 56 bits
   */
  public static int getB1u56EncodedSize(long value) {
    validateB1u56Value(value);

    if (value == 0) {
      return 1;
    }

    int significantBits = 64 - Long.numberOfLeadingZeros(value);

    // First byte holds 6 data bits
    // Bytes 2-7 hold 7 bits each (42 bits total)
    // Byte 8 holds 8 bits
    // So: 1-6 bits = 1 byte, 7-13 bits = 2 bytes, ..., 49-56 bits = 8 bytes
    if (significantBits <= 6) {
      return 1;
    } else if (significantBits <= 48) {
      // Bytes 2-7: each adds 7 bits
      // 6 bits in byte 1, then 7 bits per additional byte
      return 1 + ((significantBits - 6 + 6) / 7); // Ceiling division for remaining bits
    } else {
      // Requires 8 bytes (6 + 42 + 8 = 56 bits max)
      return 8;
    }
  }

}