package jgeoip2;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyObject;
import org.jruby.RubyHash;
import org.jruby.RubyArray;
import org.jruby.RubyString;
import org.jruby.RubyBignum;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.ThreadContext;
import org.jruby.util.ByteList;

import org.jcodings.specific.UTF8Encoding;
import org.jcodings.specific.ASCIIEncoding;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import java.io.IOException;
import java.io.RandomAccessFile;

import java.math.BigInteger;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Decoder {
  private final boolean symbolizeKeys;
  private final int dataSectionOffset;

  public Decoder(boolean symbolizeKeys) {
    this(symbolizeKeys, 0);
  }

  public Decoder(boolean symbolizeKeys, int dataSectionOffset) {
    this.symbolizeKeys = symbolizeKeys;
    this.dataSectionOffset = dataSectionOffset;
  }

  public IRubyObject decode(ThreadContext ctx, ByteBuffer buffer) {
    int b = buffer.get() & 0xff;
    int type = b >>> 5;
    int size = b & 0x1f;
    if (type != 1) {
      switch (size) {
        case 29:
          size = 29 + readInt(buffer, 1);
          break;
        case 30:
          size = 285 + readInt(buffer, 2);
          break;
        case 31:
          size = 65821 + readInt(buffer, 3);
          break;
        default:
          if (size > 31) {
            throw ctx.runtime.newArgumentError(String.format("Unexpected size %d at position %d", size, buffer.position()));
          }
      }
    }
    switch (type) {
      case 0: return decodeExtended(ctx, buffer, size);
      case 1: return decodePointer(ctx, buffer, size);
      case 2: return decodeUtf8String(ctx, buffer, size);
      case 3: return decodeDouble(ctx, buffer, size);
      case 4: return decodeBytes(ctx, buffer, size);
      case 5: return decodeUInt16(ctx, buffer, size);
      case 6: return decodeUInt32(ctx, buffer, size);
      case 7: return decodeMap(ctx, buffer, size);
      default: throw ctx.runtime.newArgumentError(String.format("Unsupported type %d at position %d", type, buffer.position()));
    }
  }

  protected final int readInt(ByteBuffer buffer, int size) {
    int s0, s1, s2, s3;
    switch (size) {
    case 0:
      return 0;
    case 1:
      return buffer.get() & 0xff;
    case 2:
      s0 = buffer.get() & 0xff;
      s1 = buffer.get() & 0xff;
      return (s0 << 8) | s1;
    case 3:
      s0 = buffer.get() & 0xff;
      s1 = buffer.get() & 0xff;
      s2 = buffer.get() & 0xff;
      return (s0 << 16) | (s1 << 8) | s2;
    case 4:
      s0 = buffer.get() & 0xff;
      s1 = buffer.get() & 0xff;
      s2 = buffer.get() & 0xff;
      s3 = buffer.get() & 0xff;
      return (s0 << 24) | (s1 << 16) | (s2 << 8) | s3;
    default:
      throw new IndexOutOfBoundsException(String.format("Unexpected size of an integer field %d (0-4)", size));
    }
  }

  protected final long readLong(ByteBuffer buffer, int size) {
    if (size == 0) {
      return 0L;
    } else if (size < 5) {
      return readInt(buffer, size);
    } else if (size < 9) {
      long i0 = readInt(buffer, 8 - size);
      long i1 = readInt(buffer, 4);
      return (i0 << 32) | i1;
    } else {
      throw new IndexOutOfBoundsException(String.format("Unexpected size of a long field %d (0-8)", size));
    }
  }

  private IRubyObject decodeExtended(ThreadContext ctx, ByteBuffer buffer, int size) {
    int b = buffer.get() & 0xff;
    int type = b + 7;
    switch (type) {
      case 8: return decodeInt32(ctx, buffer, size);
      case 9: return decodeUInt64(ctx, buffer, size);
      case 10: return decodeUInt128(ctx, buffer, size);
      case 11: return decodeArray(ctx, buffer, size);
      case 12: return decodeDataCacheContainer(ctx, buffer, size);
      case 13: return decodeEndMarker(ctx, buffer, size);
      case 14: return decodeBoolean(ctx, buffer, size);
      case 15: return decodeFloat(ctx, buffer, size);
      default: throw ctx.runtime.newArgumentError(String.format("Unsupported extended type %d at position %d", type, buffer.position()));
    }
  }

  private IRubyObject decodePointer(ThreadContext ctx, ByteBuffer buffer, int sizeAndValue) {
    int size = 1 + ((sizeAndValue >>> 3) & 0x03);
    int value = sizeAndValue & 0x07;
    int offset = readInt(buffer, size);
    if (size < 4) {
      offset = value << (8 * size) | offset;
    }
    offset += dataSectionOffset;
    if (size == 2) {
      offset += (1 << 11);
    } else if (size == 3) {
      offset += (1 << 19) + (1 << 11);
    }
    int oldPosition = buffer.position();
    buffer.position(offset);
    IRubyObject data = decode(ctx, buffer);
    buffer.position(oldPosition);
    return data;
  }

  private IRubyObject decodeUtf8String(ThreadContext ctx, ByteBuffer buffer, int size) {
    byte[] bytes = new byte[size];
    buffer.get(bytes);
    ByteList byteList = new ByteList(bytes, UTF8Encoding.INSTANCE);
    return ctx.runtime.newString(byteList);
  }

  private IRubyObject decodeDouble(ThreadContext ctx, ByteBuffer buffer, int size) {
    if (size != 8) {
      throw ctx.runtime.newArgumentError(String.format("Unexpected size of a double field %d (expected 8)", size));
    }
    return ctx.runtime.newFloat(buffer.getDouble());
  }

  private IRubyObject decodeBytes(ThreadContext ctx, ByteBuffer buffer, int size) {
    byte[] bytes = new byte[size];
    buffer.get(bytes);
    ByteList byteList = new ByteList(bytes, ASCIIEncoding.INSTANCE);
    return ctx.runtime.newString(byteList);
  }

  private IRubyObject decodeUInt16(ThreadContext ctx, ByteBuffer buffer, int size) {
    return ctx.runtime.newFixnum(readInt(buffer, size));
  }

  private IRubyObject decodeUInt32(ThreadContext ctx, ByteBuffer buffer, int size) {
    long l = readLong(buffer, size);
    return ctx.runtime.newFixnum(l);
  }

  private IRubyObject decodeMap(ThreadContext ctx, ByteBuffer buffer, int size) {
    RubyHash hash = RubyHash.newHash(ctx.runtime);
    for (int i = 0; i < size; i++) {
      IRubyObject key = decode(ctx, buffer);
      IRubyObject value = decode(ctx, buffer);
      if (symbolizeKeys) {
        key = ctx.runtime.newSymbol(key.asString().toString());
      }
      hash.fastASet(key, value);
    }
    return hash;
  }

  private IRubyObject decodeInt32(ThreadContext ctx, ByteBuffer buffer, int size) {
    return ctx.runtime.newFixnum(readInt(buffer, size));
  }

  private IRubyObject decodeUInt64(ThreadContext ctx, ByteBuffer buffer, int size) {
    byte[] bytes = new byte[size];
    buffer.get(bytes);
    return RubyBignum.newBignum(ctx.runtime, new BigInteger(1, bytes));
  }

  private IRubyObject decodeUInt128(ThreadContext ctx, ByteBuffer buffer, int size) {
    byte[] bytes = new byte[size];
    buffer.get(bytes);
    return RubyBignum.newBignum(ctx.runtime, new BigInteger(1, bytes));
  }

  private IRubyObject decodeArray(ThreadContext ctx, ByteBuffer buffer, int size) {
    IRubyObject[] elements = new IRubyObject[size];
    for (int i = 0; i < size; i++) {
      elements[i] = decode(ctx, buffer);
    }
    return ctx.runtime.newArray(elements);
  }

  private IRubyObject decodeDataCacheContainer(ThreadContext ctx, ByteBuffer buffer, int size) {
    throw ctx.runtime.newNotImplementedError("Cannot decode data cache container");
  }

  private IRubyObject decodeEndMarker(ThreadContext ctx, ByteBuffer buffer, int size) {
    throw ctx.runtime.newNotImplementedError("Cannot decode end marker");
  }

  private IRubyObject decodeBoolean(ThreadContext ctx, ByteBuffer buffer, int size) {
    if (size == 0) {
      return ctx.runtime.getFalse();
    } else if (size == 1) {
      return ctx.runtime.getTrue();
    } else {
      throw ctx.runtime.newArgumentError(String.format("Unexpected value for boolean %d", size));
    }
  }

  private IRubyObject decodeFloat(ThreadContext ctx, ByteBuffer buffer, int size) {
    if (size != 4) {
      throw ctx.runtime.newArgumentError(String.format("Unexpected size of a float field %d (expected 4)", size));
    }
    return ctx.runtime.newFloat(buffer.getFloat());
  }
}
