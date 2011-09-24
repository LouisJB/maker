#!/usr/bin/ruby -w

require 'find'
jars = []
modules=["maker", "caller", "plugin"]
modules.each do |m|
  Find.find("#{m}/lib") do |f|
    if f =~ /\.jar$/ then
      jars.unshift(f)
    end
  end
end

module_class_dirs = modules.collect do |m| "#{m}/classes/:#{m}/test-classes:#{m}/resources/" end
File.open("set-classpath.sh", "w") do |s|
  s.puts("export CLASSPATH=#{module_class_dirs.join(":")}:#{jars.join(":")}")
end

