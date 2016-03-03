# coding: utf-8

lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)

require 'jgeoip2/version'

Gem::Specification.new do |spec|
  spec.name = 'jgeoip2'
  spec.version = JGeoIP2::VERSION
  spec.authors = ['Theo Hultberg']
  spec.email = ['theo@iconara.net']
  spec.summary = %q{}
  spec.description = %q{}
  spec.homepage = 'https://github.com/iconara/jgeoip2'
  spec.license = 'BSD-3-Clause'

  spec.files = Dir['lib/**/*.{rb,jar}'] + Dir['bin/*'] + %w[LICENSE.txt README.md]
  spec.executables = %w[]
  spec.require_paths = %w[lib]

  spec.add_development_dependency 'bundler', '~> 1.11'
end
