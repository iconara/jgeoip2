package jgeoip2;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.runtime.Block;
import org.jruby.runtime.load.Library;
import org.jruby.runtime.builtin.IRubyObject;

public class JGeoIP2Library implements Library {
  public void load(Ruby runtime, boolean wrap) {
    RubyModule module = runtime.defineModule("JGeoIP2");
    Database.createRubyClass(runtime, module);
  }

  static IRubyObject createInstance(Ruby runtime, String className, IRubyObject... args) {
    RubyClass klass = runtime.getModule("JGeoIP2").getClass(className);
    RubyObject object = (RubyObject) klass.allocate();
    object.callInit(args, Block.NULL_BLOCK);
    return object;
  }
}
