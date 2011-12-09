#!/usr/bin/ruby -w

require 'find'
jars = []
modules=["maker", "plugin", "utils"]
lib_dirs = modules.collect{|m| "#{m}/lib"}.unshift("lib")
lib_dirs.each do |lib_dir|
  Find.find(lib_dir) do |f|
    if f =~ /\.jar$/ then
      jars.unshift(f)
    end
  end
end
project_resource_dir="lib/resources/"
module_class_dirs = modules.collect do |m| ["#{m}/classes/", "#{m}/test-classes", "#{m}/resources/"] end
all_files = [project_resource_dir] + jars + module_class_dirs.flatten
classpath = ((all_files.select do |f| File.exists?(f) end) .collect do |f| File.expand_path(f) end).join(":")

File.open("set-classpath.sh", "w") do |s|
  s.puts("export CLASSPATH=#{classpath}")
end

File.open("set-maker-classpath.sh", "w") do |s|
  s.puts("export CLASSPATH=#{File.expand_path("out")}:#{classpath}")
end
