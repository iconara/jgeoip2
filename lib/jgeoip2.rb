require 'jgeoip2/errors'
require 'jgeoip2/metadata'
require 'jgeoip2/jgeoip2'

Java::Jgeoip2::JGeoIP2Library.new.load(JRuby.runtime, false)
