package org.sagebionetworks.repo.model.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class EncodingUtilsTest {

  @Test
  public void testEncodeVu57_Zero() {
    byte[] encoded = EncodingUtils.encodeVu57(0L);
    assertArrayEquals(new byte[]{0x00}, encoded);
    assertEquals(1, EncodingUtils.getVu57EncodedSize(0L));
  }

  @Test
  public void testEncodeVu57_SmallValue() {
    // Value 1: fits in 1 byte
    byte[] encoded = EncodingUtils.encodeVu57(1L);
    assertArrayEquals(new byte[]{0x01}, encoded);
    assertEquals(1, EncodingUtils.getVu57EncodedSize(1L));
  }

  @Test
  public void testEncodeVu57_MaxSingleByte() {
    // Value 127 (0x7F): max value that fits in 1 byte
    byte[] encoded = EncodingUtils.encodeVu57(127L);
    assertArrayEquals(new byte[]{0x7F}, encoded);
    assertEquals(1, EncodingUtils.getVu57EncodedSize(127L));
  }

  @Test
  public void testEncodeVu57_TwoBytes() {
    // Value 128 (0x80): requires 2 bytes
    // Binary: 10000000
    // Encoded: [0x80, 0x01]
    // First byte: 0000000 with continuation bit = 10000000 = 0x80
    // Second byte: 0000001 = 0x01
    byte[] encoded = EncodingUtils.encodeVu57(128L);
    assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, encoded);
    assertEquals(2, EncodingUtils.getVu57EncodedSize(128L));
  }

  @Test
  public void testEncodeVu57_MaxTwoBytes() {
    // Value 16383 (2^14 - 1): max value that fits in 2 bytes
    // Binary: 11111111111111
    // Encoded: [0xFF, 0x7F]
    byte[] encoded = EncodingUtils.encodeVu57(16383L);
    assertArrayEquals(new byte[]{(byte) 0xFF, 0x7F}, encoded);
    assertEquals(2, EncodingUtils.getVu57EncodedSize(16383L));
  }

  @Test
  public void testEncodeVu57_ThreeBytes() {
    // Value 16384 (2^14): requires 3 bytes
    byte[] encoded = EncodingUtils.encodeVu57(16384L);
    assertArrayEquals(new byte[]{(byte) 0x80, (byte) 0x80, 0x01}, encoded);
    assertEquals(3, EncodingUtils.getVu57EncodedSize(16384L));
  }

  @Test
  public void testEncodeVu57_LargeValue() {
    // Value 1,000,000
    byte[] encoded = EncodingUtils.encodeVu57(1_000_000L);
    assertEquals(3, encoded.length);
    assertEquals(3, EncodingUtils.getVu57EncodedSize(1_000_000L));
  }

  @Test
  public void testEncodeVu57_Max57Bits() {
    // Maximum 57-bit value: 2^57 - 1
    long maxValue = (1L << 57) - 1;
    byte[] encoded = EncodingUtils.encodeVu57(maxValue);
    assertEquals(8, encoded.length); // Should use 8 bytes
    assertEquals(8, EncodingUtils.getVu57EncodedSize(maxValue));
  }

  @Test
  public void testEncodeVu57_NegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> {
      EncodingUtils.encodeVu57(-1L);
    });
  }

  @Test
  public void testEncodeVu57_ExceedsMaxValue() {
    long tooLarge = (1L << 57);
    assertThrows(IllegalArgumentException.class, () -> {
      EncodingUtils.encodeVu57(tooLarge);
    });
  }

  @Test
  public void testDecodeVu57_Zero() throws IOException {
    byte[] encoded = new byte[]{0x00};
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(0L, decoded);

    // Test with InputStream
    ByteArrayInputStream in = new ByteArrayInputStream(encoded);
    decoded = EncodingUtils.decodeVu57(in);
    assertEquals(0L, decoded);
  }

  @Test
  public void testDecodeVu57_SmallValue() throws IOException {
    byte[] encoded = new byte[]{0x01};
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(1L, decoded);
  }

  @Test
  public void testDecodeVu57_MaxSingleByte() throws IOException {
    byte[] encoded = new byte[]{0x7F};
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(127L, decoded);
  }

  @Test
  public void testDecodeVu57_TwoBytes() throws IOException {
    byte[] encoded = new byte[]{(byte) 0x80, 0x01};
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(128L, decoded);
  }

  @Test
  public void testDecodeVu57_MaxTwoBytes() throws IOException {
    byte[] encoded = new byte[]{(byte) 0xFF, 0x7F};
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(16383L, decoded);
  }

  @Test
  public void testDecodeVu57_ThreeBytes() throws IOException {
    byte[] encoded = new byte[]{(byte) 0x80, (byte) 0x80, 0x01};
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(16384L, decoded);
  }

  @Test
  public void testDecodeVu57_LargeValue() throws IOException {
    byte[] encoded = EncodingUtils.encodeVu57(1_000_000L);
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(1_000_000L, decoded);
  }

  @Test
  public void testDecodeVu57_Max57Bits() throws IOException {
    long maxValue = (1L << 57) - 1;
    byte[] encoded = EncodingUtils.encodeVu57(maxValue);
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(maxValue, decoded);
  }

  @Test
  public void testDecodeVu57_WithOffset() {
    byte[] data = new byte[]{0x42, 0x42, (byte) 0xFF, 0x7F, 0x42};
    long decoded = EncodingUtils.decodeVu57(data, 2);
    assertEquals(16383L, decoded);
  }

  @Test
  public void testRoundTrip() throws IOException {
    long[] testValues = {
      0L,
      1L,
      127L,
      128L,
      255L,
      16383L,
      16384L,
      1_000_000L,
      1_000_000_000L,
      1_000_000_000_000L,
      (1L << 56) - 1,
      (1L << 57) - 1
    };

    for (long value : testValues) {
      byte[] encoded = EncodingUtils.encodeVu57(value);
      long decoded = EncodingUtils.decodeVu57(encoded);
      assertEquals(value, decoded, "Round trip failed for value: " + value);

      // Test with InputStream
      ByteArrayInputStream in = new ByteArrayInputStream(encoded);
      decoded = EncodingUtils.decodeVu57(in);
      assertEquals(value, decoded, "Round trip with InputStream failed for value: " + value);
    }
  }

  @Test
  public void testEncodeVu57_ToOutputStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    EncodingUtils.encodeVu57(1_000_000L, out);

    byte[] encoded = out.toByteArray();
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(1_000_000L, decoded);
  }

  @Test
  public void testDecodeVu57_UnexpectedEndOfStream() {
    byte[] incomplete = new byte[]{(byte) 0x80}; // Continuation bit set but no next byte

    assertThrows(IOException.class, () -> {
      ByteArrayInputStream in = new ByteArrayInputStream(incomplete);
      EncodingUtils.decodeVu57(in);
    });
  }

  @Test
  public void testDecodeVu57_TooManyBytes() {
    // The 8th byte is always the last byte and doesn't have a continuation bit
    // So having 9 bytes would only happen if the byte array has extra data after a valid encoding
    // In practice, the decoder stops at byte 8 (for 8-byte encodings) or earlier
    // This test verifies that the decoder correctly handles the maximum 8-byte case

    // This is a valid 8-byte encoding (max 57-bit value)
    long maxValue = (1L << 57) - 1;
    byte[] encoded = EncodingUtils.encodeVu57(maxValue);
    assertEquals(8, encoded.length);

    // Should decode successfully
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(maxValue, decoded);

    // If we have extra bytes after a valid encoding, they're simply not read
    // (this is expected behavior for stream-based protocols)
    byte[] withExtra = new byte[]{
      encoded[0], encoded[1], encoded[2], encoded[3],
      encoded[4], encoded[5], encoded[6], encoded[7],
      (byte) 0x42  // Extra byte that won't be read
    };
    decoded = EncodingUtils.decodeVu57(withExtra);
    assertEquals(maxValue, decoded);
  }

  @Test
  public void testGetVu57EncodedSize_AllByteSizes() {
    // Test values that require each byte size
    assertEquals(1, EncodingUtils.getVu57EncodedSize(0L));
    assertEquals(1, EncodingUtils.getVu57EncodedSize(127L)); // 2^7 - 1
    assertEquals(2, EncodingUtils.getVu57EncodedSize(128L)); // 2^7
    assertEquals(2, EncodingUtils.getVu57EncodedSize(16383L)); // 2^14 - 1
    assertEquals(3, EncodingUtils.getVu57EncodedSize(16384L)); // 2^14
    assertEquals(3, EncodingUtils.getVu57EncodedSize(2_097_151L)); // 2^21 - 1
    assertEquals(4, EncodingUtils.getVu57EncodedSize(2_097_152L)); // 2^21
    assertEquals(4, EncodingUtils.getVu57EncodedSize(268_435_455L)); // 2^28 - 1
    assertEquals(5, EncodingUtils.getVu57EncodedSize(268_435_456L)); // 2^28
    assertEquals(5, EncodingUtils.getVu57EncodedSize(34_359_738_367L)); // 2^35 - 1
    assertEquals(6, EncodingUtils.getVu57EncodedSize(34_359_738_368L)); // 2^35
    assertEquals(6, EncodingUtils.getVu57EncodedSize(4_398_046_511_103L)); // 2^42 - 1
    assertEquals(7, EncodingUtils.getVu57EncodedSize(4_398_046_511_104L)); // 2^42
    assertEquals(7, EncodingUtils.getVu57EncodedSize(562_949_953_421_311L)); // 2^49 - 1
    assertEquals(8, EncodingUtils.getVu57EncodedSize(562_949_953_421_312L)); // 2^49
    assertEquals(8, EncodingUtils.getVu57EncodedSize((1L << 57) - 1)); // 2^57 - 1
  }

  @Test
  public void testBitPattern() throws IOException {
    // Test specific bit patterns to ensure correct encoding
    // Value: 0b10101010_10101010 (43690)
    long value = 0b10101010_10101010L;
    byte[] encoded = EncodingUtils.encodeVu57(value);
    long decoded = EncodingUtils.decodeVu57(encoded);
    assertEquals(value, decoded);
  }

  @Test
  public void testMultipleValuesInStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    EncodingUtils.encodeVu57(100L, out);
    EncodingUtils.encodeVu57(200L, out);
    EncodingUtils.encodeVu57(300L, out);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    assertEquals(100L, EncodingUtils.decodeVu57(in));
    assertEquals(200L, EncodingUtils.decodeVu57(in));
    assertEquals(300L, EncodingUtils.decodeVu57(in));
  }

  // ==================== b1u56 Tests ====================

  @Test
  public void testEncodeB1u56_Zero_FlagFalse() {
    byte[] encoded = EncodingUtils.encodeB1u56(false, 0L);
    assertArrayEquals(new byte[]{0x00}, encoded);
    assertEquals(1, EncodingUtils.getB1u56EncodedSize(0L));
  }

  @Test
  public void testEncodeB1u56_Zero_FlagTrue() {
    byte[] encoded = EncodingUtils.encodeB1u56(true, 0L);
    // Flag bit is 0x80
    assertArrayEquals(new byte[]{(byte) 0x80}, encoded);
    assertEquals(1, EncodingUtils.getB1u56EncodedSize(0L));
  }

  @Test
  public void testEncodeB1u56_SmallValue_FlagFalse() {
    byte[] encoded = EncodingUtils.encodeB1u56(false, 1L);
    assertArrayEquals(new byte[]{0x01}, encoded);
    assertEquals(1, EncodingUtils.getB1u56EncodedSize(1L));
  }

  @Test
  public void testEncodeB1u56_SmallValue_FlagTrue() {
    byte[] encoded = EncodingUtils.encodeB1u56(true, 1L);
    // Flag bit (0x80) | value (0x01) = 0x81
    assertArrayEquals(new byte[]{(byte) 0x81}, encoded);
  }

  @Test
  public void testEncodeB1u56_MaxSingleByte() {
    // Max value in 6 bits = 63 (0x3F)
    byte[] encoded = EncodingUtils.encodeB1u56(false, 63L);
    assertArrayEquals(new byte[]{0x3F}, encoded);
    assertEquals(1, EncodingUtils.getB1u56EncodedSize(63L));
  }

  @Test
  public void testEncodeB1u56_MaxSingleByte_FlagTrue() {
    // Max value in 6 bits = 63, with flag = 0x80 | 0x3F = 0xBF
    byte[] encoded = EncodingUtils.encodeB1u56(true, 63L);
    assertArrayEquals(new byte[]{(byte) 0xBF}, encoded);
  }

  @Test
  public void testEncodeB1u56_TwoBytes() {
    // Value 64 (0x40): requires 2 bytes
    // First byte: continuation (0x40) | first 6 bits (0) = 0x40
    // Second byte: remaining bits (1) = 0x01
    byte[] encoded = EncodingUtils.encodeB1u56(false, 64L);
    assertArrayEquals(new byte[]{0x40, 0x01}, encoded);
    assertEquals(2, EncodingUtils.getB1u56EncodedSize(64L));
  }

  @Test
  public void testEncodeB1u56_TwoBytes_FlagTrue() {
    // Value 64 with flag
    // First byte: flag (0x80) | continuation (0x40) | first 6 bits (0) = 0xC0
    byte[] encoded = EncodingUtils.encodeB1u56(true, 64L);
    assertArrayEquals(new byte[]{(byte) 0xC0, 0x01}, encoded);
  }

  @Test
  public void testEncodeB1u56_MaxTwoBytes() {
    // Max value in 13 bits (6 + 7) = 2^13 - 1 = 8191
    // First byte: continuation (0x40) | first 6 bits (all 1s = 0x3F) = 0x7F
    // Second byte: next 7 bits (all 1s = 0x7F) = 0x7F (no continuation)
    byte[] encoded = EncodingUtils.encodeB1u56(false, 8191L);
    assertArrayEquals(new byte[]{0x7F, 0x7F}, encoded);
    assertEquals(2, EncodingUtils.getB1u56EncodedSize(8191L));
  }

  @Test
  public void testEncodeB1u56_ThreeBytes() {
    // Value 8192 (2^13): requires 3 bytes
    byte[] encoded = EncodingUtils.encodeB1u56(false, 8192L);
    assertEquals(3, encoded.length);
    assertEquals(3, EncodingUtils.getB1u56EncodedSize(8192L));
  }

  @Test
  public void testEncodeB1u56_LargeValue() {
    // Value 1,000,000
    byte[] encoded = EncodingUtils.encodeB1u56(false, 1_000_000L);
    assertEquals(3, encoded.length);
    assertEquals(3, EncodingUtils.getB1u56EncodedSize(1_000_000L));
  }

  @Test
  public void testEncodeB1u56_Max56Bits() {
    // Maximum 56-bit value: 2^56 - 1
    long maxValue = (1L << 56) - 1;
    byte[] encoded = EncodingUtils.encodeB1u56(false, maxValue);
    assertEquals(8, encoded.length);
    assertEquals(8, EncodingUtils.getB1u56EncodedSize(maxValue));
  }

  @Test
  public void testEncodeB1u56_NegativeValue() {
    assertThrows(IllegalArgumentException.class, () -> {
      EncodingUtils.encodeB1u56(false, -1L);
    });
  }

  @Test
  public void testEncodeB1u56_ExceedsMaxValue() {
    long tooLarge = (1L << 56);
    assertThrows(IllegalArgumentException.class, () -> {
      EncodingUtils.encodeB1u56(false, tooLarge);
    });
  }

  @Test
  public void testDecodeB1u56_Zero_FlagFalse() throws IOException {
    byte[] encoded = new byte[]{0x00};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertFalse(result.getFlag());
    assertEquals(0L, result.getValue());

    // Test with InputStream
    ByteArrayInputStream in = new ByteArrayInputStream(encoded);
    result = EncodingUtils.decodeB1u56(in);
    assertFalse(result.getFlag());
    assertEquals(0L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_Zero_FlagTrue() throws IOException {
    byte[] encoded = new byte[]{(byte) 0x80};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertTrue(result.getFlag());
    assertEquals(0L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_SmallValue() throws IOException {
    byte[] encoded = new byte[]{0x01};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertFalse(result.getFlag());
    assertEquals(1L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_MaxSingleByte() throws IOException {
    byte[] encoded = new byte[]{0x3F};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertFalse(result.getFlag());
    assertEquals(63L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_TwoBytes() throws IOException {
    byte[] encoded = new byte[]{0x40, 0x01};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertFalse(result.getFlag());
    assertEquals(64L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_TwoBytes_FlagTrue() throws IOException {
    byte[] encoded = new byte[]{(byte) 0xC0, 0x01};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertTrue(result.getFlag());
    assertEquals(64L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_MaxTwoBytes() throws IOException {
    byte[] encoded = new byte[]{0x7F, 0x7F};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertFalse(result.getFlag());
    assertEquals(8191L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_WithOffset() {
    byte[] data = new byte[]{0x42, 0x42, (byte) 0xFF, 0x7F, 0x42};
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(data, 2);
    assertTrue(result.getFlag());
    assertEquals(8191L, result.getValue());
  }

  @Test
  public void testB1u56RoundTrip() throws IOException {
    long[] testValues = {
      0L,
      1L,
      63L,
      64L,
      127L,
      8191L,
      8192L,
      1_000_000L,
      1_000_000_000L,
      1_000_000_000_000L,
      (1L << 55) - 1,
      (1L << 56) - 1
    };

    for (long value : testValues) {
      for (boolean flag : new boolean[]{false, true}) {
        byte[] encoded = EncodingUtils.encodeB1u56(flag, value);
        EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
        assertEquals(flag, result.getFlag(), "Round trip failed for flag: " + flag + ", value: " + value);
        assertEquals(value, result.getValue(), "Round trip failed for flag: " + flag + ", value: " + value);

        // Test with InputStream
        ByteArrayInputStream in = new ByteArrayInputStream(encoded);
        result = EncodingUtils.decodeB1u56(in);
        assertEquals(flag, result.getFlag(), "Round trip with InputStream failed for flag: " + flag + ", value: " + value);
        assertEquals(value, result.getValue(), "Round trip with InputStream failed for flag: " + flag + ", value: " + value);
      }
    }
  }

  @Test
  public void testEncodeB1u56_ToOutputStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    EncodingUtils.encodeB1u56(true, 1_000_000L, out);

    byte[] encoded = out.toByteArray();
    EncodingUtils.B1u56Result result = EncodingUtils.decodeB1u56(encoded);
    assertTrue(result.getFlag());
    assertEquals(1_000_000L, result.getValue());
  }

  @Test
  public void testDecodeB1u56_UnexpectedEndOfStream() {
    byte[] incomplete = new byte[]{0x40}; // Continuation bit set but no next byte

    assertThrows(IOException.class, () -> {
      ByteArrayInputStream in = new ByteArrayInputStream(incomplete);
      EncodingUtils.decodeB1u56(in);
    });
  }

  @Test
  public void testGetB1u56EncodedSize_AllByteSizes() {
    // Test values that require each byte size
    assertEquals(1, EncodingUtils.getB1u56EncodedSize(0L));
    assertEquals(1, EncodingUtils.getB1u56EncodedSize(63L)); // 2^6 - 1
    assertEquals(2, EncodingUtils.getB1u56EncodedSize(64L)); // 2^6
    assertEquals(2, EncodingUtils.getB1u56EncodedSize(8191L)); // 2^13 - 1
    assertEquals(3, EncodingUtils.getB1u56EncodedSize(8192L)); // 2^13
    assertEquals(3, EncodingUtils.getB1u56EncodedSize(1_048_575L)); // 2^20 - 1
    assertEquals(4, EncodingUtils.getB1u56EncodedSize(1_048_576L)); // 2^20
    assertEquals(4, EncodingUtils.getB1u56EncodedSize(134_217_727L)); // 2^27 - 1
    assertEquals(5, EncodingUtils.getB1u56EncodedSize(134_217_728L)); // 2^27
    assertEquals(5, EncodingUtils.getB1u56EncodedSize(17_179_869_183L)); // 2^34 - 1
    assertEquals(6, EncodingUtils.getB1u56EncodedSize(17_179_869_184L)); // 2^34
    assertEquals(6, EncodingUtils.getB1u56EncodedSize(2_199_023_255_551L)); // 2^41 - 1
    assertEquals(7, EncodingUtils.getB1u56EncodedSize(2_199_023_255_552L)); // 2^41
    assertEquals(7, EncodingUtils.getB1u56EncodedSize(281_474_976_710_655L)); // 2^48 - 1
    assertEquals(8, EncodingUtils.getB1u56EncodedSize(281_474_976_710_656L)); // 2^48
    assertEquals(8, EncodingUtils.getB1u56EncodedSize((1L << 56) - 1)); // 2^56 - 1
  }

  @Test
  public void testB1u56_MultipleValuesInStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    EncodingUtils.encodeB1u56(true, 100L, out);
    EncodingUtils.encodeB1u56(false, 200L, out);
    EncodingUtils.encodeB1u56(true, 300L, out);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    EncodingUtils.B1u56Result result1 = EncodingUtils.decodeB1u56(in);
    assertTrue(result1.getFlag());
    assertEquals(100L, result1.getValue());

    EncodingUtils.B1u56Result result2 = EncodingUtils.decodeB1u56(in);
    assertFalse(result2.getFlag());
    assertEquals(200L, result2.getValue());

    EncodingUtils.B1u56Result result3 = EncodingUtils.decodeB1u56(in);
    assertTrue(result3.getFlag());
    assertEquals(300L, result3.getValue());
  }
}

