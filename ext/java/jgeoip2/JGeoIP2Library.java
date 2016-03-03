package jgeoip2;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.runtime.load.Library;

public class JGeoIP2Library implements Library {
  public void load(Ruby runtime, boolean wrap) {
    RubyModule module = runtime.defineModule("JGeoIP2");
    Database.createRubyClass(runtime, module);
  }
}
