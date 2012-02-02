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
files_for_maker_classpath=[File.expand_path("out")] + [project_resource_dir] + jars
files_for_classpath=[project_resource_dir] + jars + module_class_dirs.flatten
#all_files = [project_resource_dir] + jars + module_class_dirs.flatten
def make_classpath(files)
  ((files.select do |f| File.exists?(f) end) .collect do |f| File.expand_path(f) end).join(":")
end
#classpath = ((all_files.select do |f| File.exists?(f) end) .collect do |f| File.expand_path(f) end).join(":")

File.open("set-classpath.sh", "w") do |s|
  s.puts("export MAKER_CLASSPATH=#{make_classpath(files_for_classpath)}")
  s.puts("export CLASSPATH=$MAKER_CLASSPATH")
end

File.open("set-maker-classpath.sh", "w") do |s|
  s.puts("export MAKER_CLASSPATH=#{make_classpath(files_for_maker_classpath)}")
  s.puts("export CLASSPATH=$MAKER_CLASSPATH")
end
