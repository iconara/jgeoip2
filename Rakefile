require 'rake/javaextensiontask'
require 'rspec/core/rake_task'

task :default => :spec

namespace :gem do
  require 'bundler/gem_tasks'
end

Rake::JavaExtensionTask.new('jgeoip2', eval(File.read('jgeoip2.gemspec'))) do |ext|
  ext.ext_dir = 'ext/java'
  jruby_home = RbConfig::CONFIG['prefix']
  jars = ["#{jruby_home}/lib/jruby.jar"]
  ext.classpath = jars.map { |x| File.expand_path(x) }.join(':')
  ext.lib_dir = File.join(*['lib', 'jgeoip2', ENV['FAT_DIR']].compact)
  ext.source_version = '1.7'
  ext.target_version = '1.7'
end

RSpec::Core::RakeTask.new(:spec) do |r|
  options = File.readlines('.rspec').map(&:chomp)
  if (pattern = options.find { |o| o.start_with?('--pattern') })
    options.delete(pattern)
    r.pattern = pattern.sub(/^--pattern\s+(['"']?)(.+)\1$/, '\2')
  end
  r.ruby_opts, r.rspec_opts = options.partition { |o| o.start_with?('-I') }
end

task :spec => :compile
