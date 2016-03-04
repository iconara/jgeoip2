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

  private volatile ByteBuffer masterBuffer;

  private Decoder decoder;
  private IRubyObject databaseMetadata;
  private int ipVersion;
  private int ipV4StartNode;
  private int nodeCount;
  private int recordSize;
  private int nodeByteSize;
  private int searchTreeSize;

  public Database(Ruby runtime, RubyClass type) {
    super(runtime, type);
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

  @JRubyMethod(required = 1, optional = 1)
  public IRubyObject open(ThreadContext ctx, IRubyObject[] args) {
    IRubyObject path = args[0];
    if (path == null || path.isNil()) {
      throw ctx.runtime.newArgumentError("A path is required to create a Database");
    }
    boolean symbolizeKeys = false;
    if (args.length > 1 && args[args.length - 1] instanceof RubyHash) {
      RubyHash options = (RubyHash) args[args.length - 1];
      IRubyObject mode = options.fastARef(ctx.runtime.newSymbol("symbolize_keys"));
      symbolizeKeys = (mode != null) && mode.isTrue();
    }
    initializeBuffer(ctx, path.asString().toString());
    initializeMetadata(ctx, symbolizeKeys);
    return this;
  }

  @JRubyMethod()
  public IRubyObject close(ThreadContext ctx) {
    masterBuffer = null;
    return this;
  }

  private void initializeBuffer(ThreadContext ctx, String path) {
    RandomAccessFile file = null;
    try {
      file = new RandomAccessFile(path, "r");
      FileChannel channel = file.getChannel();
      masterBuffer = channel.map(MapMode.READ_ONLY, 0, channel.size());
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

  private void initializeMetadata(ThreadContext ctx, boolean symbolizeKeys) {
    ByteBuffer buffer = masterBuffer.duplicate();
    int metadataStartIndex = findMetadataStartOffset(buffer);
    if (metadataStartIndex == -1) {
      throw JGeoIP2Library.createErrorInstance(ctx.runtime, "MalformedDatabaseError", "Metadata section not found");
    }
    buffer.position(metadataStartIndex);
    RubyHash metadataHash = (RubyHash) new Decoder(true).decode(ctx, buffer);
    databaseMetadata = JGeoIP2Library.createInstance(ctx.runtime, "Metadata", metadataHash);
    ipVersion = (int) ((RubyFixnum) metadataHash.fastARef(ctx.runtime.newSymbol("ip_version"))).getLongValue();
    nodeCount = (int) ((RubyFixnum) metadataHash.fastARef(ctx.runtime.newSymbol("node_count"))).getLongValue();
    recordSize = (int) ((RubyFixnum) metadataHash.fastARef(ctx.runtime.newSymbol("record_size"))).getLongValue();
    if (recordSize != 24 && recordSize != 28 && recordSize != 32) {
      throw JGeoIP2Library.createErrorInstance(ctx.runtime, "MalformedDatabaseError", String.format("Unsupported record size, expected 24, 28 or 32 but was %d", recordSize));
    }
    nodeByteSize = recordSize/4;
    searchTreeSize = nodeCount * nodeByteSize;
    decoder = new Decoder(symbolizeKeys, searchTreeSize + DATA_SECTION_SEPARATOR_SIZE);
    ipV4StartNode = findIpV4StartNode(ctx, buffer);
  }

  private int findIpV4StartNode(ThreadContext ctx, ByteBuffer buffer) {
    int startNode = 0;
    if (ipVersion == 6) {
      for (int i = 0; i < 96 && startNode < nodeCount; i++) {
        startNode = getNodePointer(buffer, startNode, 0);
      }
    }
    return startNode;
  }

  private int findMetadataStartOffset(ByteBuffer buffer) {
    int fileSize = buffer.capacity();
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
        return fileSize - i;
      }
    }
    return -1;
  }

  private int getNodePointer(ByteBuffer buffer, int nodeNumber, int pointerIndex) {
    int nodeOffset = nodeNumber * nodeByteSize;
    switch (recordSize) {
      case 24:
        buffer.position(nodeOffset + pointerIndex * 3);
        return decoder.readInt(buffer, 3);
      case 28:
        int topBits = buffer.get(nodeOffset + 3);
        if (pointerIndex == 0) {
          topBits = (0xf0 & topBits) >>> 4;
        } else {
          topBits = 0x0f & topBits;
        }
        buffer.position(nodeOffset + pointerIndex * 4);
        return (topBits << 24) | decoder.readInt(buffer, 3);
      case 32:
        buffer.position(nodeOffset + pointerIndex * 4);
        return decoder.readInt(buffer, 4);
    }
    return -1;
  }

  @JRubyMethod(module = true, required = 1, optional = 1)
  public static IRubyObject open(ThreadContext ctx, IRubyObject recv, IRubyObject[] args) {
    IRubyObject database = JGeoIP2Library.createInstance(ctx.runtime, "Database", new IRubyObject[] {});
    database.callMethod(ctx, "open", args);
    return database;
  }

  @JRubyMethod()
  public IRubyObject metadata(ThreadContext ctx) {
    return databaseMetadata;
  }

  @JRubyMethod(required = 1)
  public IRubyObject get(ThreadContext ctx, IRubyObject ip) {
    final ByteBuffer mb = masterBuffer;
    if (mb == null) {
      throw ctx.runtime.newIOError("Database is closed");
    } else {
      try {
        InetAddress address = InetAddress.getByName(ip.asString().toString());
        ByteBuffer buffer = mb.duplicate();
        return findAndDecodeRecord(ctx, buffer, address);
      } catch (UnknownHostException uhe) {
        throw ctx.runtime.newArgumentError(uhe.getMessage());
      }
    }
  }

  private IRubyObject findAndDecodeRecord(ThreadContext ctx, ByteBuffer buffer, InetAddress address) {
    byte[] rawAddress = address.getAddress();
    int bitLength = rawAddress.length * 8;
    int node = 0;
    if (ipVersion == 6 && bitLength == 32) {
      node = ipV4StartNode;
    }
    for (int i = 0; i < bitLength; i++) {
      if (node >= nodeCount) {
        break;
      }
      int b = 0xff & rawAddress[i/8];
      int bit = 1 & (b >> 7 - (i % 8));
      node = getNodePointer(buffer, node, bit);
    }
    if (node == nodeCount) {
      return ctx.runtime.getNil();
    } else if (node > nodeCount) {
      int offset = (node - nodeCount) + searchTreeSize;
      if (offset >= buffer.capacity()) {
        throw JGeoIP2Library.createErrorInstance(ctx.runtime, "MalformedDatabaseError", "Pointer pointing outside of the database");
      }
      buffer.position(offset);
      return decoder.decode(ctx, buffer);
    }
    throw JGeoIP2Library.createErrorInstance(ctx.runtime, "MalformedDatabaseError", "Unexpectedly found no record");
  }
}
