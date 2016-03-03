package jgeoip2;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyHash;
import org.jruby.RubyArray;
import org.jruby.RubyString;
import org.jruby.RubyBignum;
import org.jruby.RubyFixnum;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Block;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.util.ByteList;

import org.jcodings.specific.UTF8Encoding;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import java.io.IOException;
import java.io.RandomAccessFile;

import java.net.InetAddress;
import java.net.UnknownHostException;

@JRubyClass(name="JGeoIP2::Database")
public class Database extends RubyObject {
  private static final int DATA_SECTION_SEPARATOR_SIZE = 16;
  private static final byte[] METADATA_START_MARKER = {
    (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
    'M', 'a', 'x', 'M', 'i', 'n', 'd', '.', 'c', 'o', 'm'
  };

  private final Decoder decoder;

  private ByteBuffer masterBuffer;
  private RubyObject databaseMetadata;
  private int ipVersion;
  private int ipV4StartNode;
  private int nodeCount;
  private int recordSize;
  private int nodeByteSize;
  private int searchTreeSize;

  public Database(Ruby runtime, RubyClass type) {
    super(runtime, type);
    this.decoder = new Decoder();
  }

  public static ObjectAllocator allocator() {
    return new ObjectAllocator() {
      @Override
      public IRubyObject allocate(Ruby runtime, RubyClass klass) {
        return new Database(runtime, klass);
      }
    };
  }

  public static RubyClass createRubyClass(Ruby runtime, RubyModule parentModule) {
    RubyClass klass = parentModule.defineClassUnder("Database", runtime.getObject(), allocator());
    klass.defineAnnotatedMethods(Database.class);
    return klass;
  }

  @JRubyMethod()
  public IRubyObject open(ThreadContext ctx, IRubyObject path) {
    if (path == null || path.isNil()) {
      throw ctx.runtime.newArgumentError("A path is required to create a Database");
    }
    initializeBuffer(ctx, path.asString().toString());
    initializeMetadata(ctx);
    return this;
  }

  private void initializeBuffer(ThreadContext ctx, String path) {
    RandomAccessFile file = null;
    try {
      file = new RandomAccessFile(path, "r");
      FileChannel channel = file.getChannel();
      this.masterBuffer = channel.map(MapMode.READ_ONLY, 0, channel.size());
    } catch (IOException ioe) {
      throw ctx.runtime.newIOErrorFromException(ioe);
    } finally {
      try {
        if (file != null) {
          file.close();
        }
      } catch (IOException ioe) {
        throw ctx.runtime.newIOErrorFromException(ioe);
      }
    }
  }

  private void initializeMetadata(ThreadContext ctx) {
    ByteBuffer buffer = masterBuffer.duplicate();
    int fileSize = buffer.capacity();
    int metadataStartIndex = -1;

    for (int i = 0; i < fileSize - METADATA_START_MARKER.length + 1; i++) {
      boolean found = true;
      for (int j = 0; j < METADATA_START_MARKER.length; j++) {
        byte b = buffer.get(fileSize - i - j - 1);
        if (b != METADATA_START_MARKER[METADATA_START_MARKER.length - j - 1]) {
          found = false;
          break;
        }
      }
      if (found) {
        metadataStartIndex = fileSize - i;
        break;
      }
    }

    if (metadataStartIndex == -1) {
      throw ctx.runtime.newArgumentError("Malformed database: metadata section not found");
    }

    buffer.position(metadataStartIndex);
    RubyHash metadataHash = (RubyHash) decoder.decode(ctx, buffer);
    RubyClass metadataClass = ctx.runtime.getModule("JGeoIP2").getClass("Metadata");
    databaseMetadata = (RubyObject) metadataClass.allocate();
    databaseMetadata.callInit(metadataHash, Block.NULL_BLOCK);
    ipVersion = (int) ((RubyFixnum) metadataHash.fastARef(ctx.runtime.newString("ip_version"))).getLongValue();
    nodeCount = (int) ((RubyFixnum) metadataHash.fastARef(ctx.runtime.newString("node_count"))).getLongValue();
    recordSize = (int) ((RubyFixnum) metadataHash.fastARef(ctx.runtime.newString("record_size"))).getLongValue();
    nodeByteSize = recordSize/4;
    searchTreeSize = nodeCount * nodeByteSize;
    ipV4StartNode = 0;
    if (ipVersion == 6) {
      for (int i = 0; i < 96 && ipV4StartNode < nodeCount; i++) {
        ipV4StartNode = readNode(ctx, buffer, ipV4StartNode, 0);
      }
    }
  }

  private int readNode(ThreadContext ctx, ByteBuffer buffer, int nodeNumber, int index) {
    int baseOffset = nodeNumber * nodeByteSize;

    switch (recordSize) {
      case 24:
        buffer.position(baseOffset + index * 3);
        return decoder.readInt(buffer, 3);
      case 28:
        int middle = buffer.get(baseOffset + 3);
        if (index == 0) {
          middle = (0xf0 & middle) >>> 4;
        } else {
          middle = 0x0f & middle;
        }
        buffer.position(baseOffset + index * 4);
        return (middle << 24) | decoder.readInt(buffer, 3);
      case 32:
        buffer.position(baseOffset + index * 4);
        return decoder.readInt(buffer, 4);
      default:
        throw ctx.runtime.newArgumentError(String.format("Unsupported record size at position %d\n", buffer.position()));
    }
  }

  @JRubyMethod(module = true, required = 1)
  public static IRubyObject open(ThreadContext ctx, IRubyObject recv, IRubyObject path) {
    RubyClass klass = ctx.runtime.getModule("JGeoIP2").getClass("Database");
    Database instance = (Database) allocator().allocate(ctx.runtime, klass);
    instance.initialize(ctx);
    instance.open(ctx, path);
    return instance;
  }

  @JRubyMethod()
  public IRubyObject metadata(ThreadContext ctx) {
    return databaseMetadata;
  }

  @JRubyMethod(required = 1)
  public IRubyObject get(ThreadContext ctx, IRubyObject ip) {
    try {
      InetAddress address = InetAddress.getByName(ip.asString().toString());
      ByteBuffer buffer = masterBuffer.duplicate();
      return findRecord(ctx, buffer, address);
    } catch (UnknownHostException uhe) {
      throw ctx.runtime.newArgumentError(uhe.getMessage());
    }
  }

  private IRubyObject findRecord(ThreadContext ctx, ByteBuffer buffer, InetAddress address) {
    byte[] rawAddress = address.getAddress();
    int bitLength = rawAddress.length * 8;
    int record = 0;
    if (ipVersion == 6 && bitLength == 32) {
      record = ipV4StartNode;
    }
    for (int i = 0; i < bitLength; i++) {
      if (record >= nodeCount) {
        break;
      }
      int b = 0xff & rawAddress[i/8];
      int bit = 1 & (b >> 7 - (i % 8));
      record = readNode(ctx, buffer, record, bit);
    }
    if (record == nodeCount) {
      return ctx.runtime.getNil();
    } else if (record > nodeCount) {
      return resolvePointer(ctx, buffer, record);
    }
    throw ctx.runtime.newArgumentError("Unexpectedly found no record");
  }

  private IRubyObject resolvePointer(ThreadContext ctx, ByteBuffer buffer, int pointer) {
    int offset = (pointer - nodeCount) + searchTreeSize;
    if (offset >= buffer.capacity()) {
      throw ctx.runtime.newArgumentError("Pointer pointing outside of the database");
    }
    buffer.position(offset);
    return decoder.decode(ctx, buffer);
  }
}
