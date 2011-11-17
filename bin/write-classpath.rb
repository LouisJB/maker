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
project_resource_dir="resources/"
module_class_dirs = modules.collect do |m| "#{m}/classes/:#{m}/test-classes:#{m}/resources/" end
project_jar="maker.jar"
File.open("set-classpath.sh", "w") do |s|
  #s.puts("export CLASSPATH=#{module_class_dirs.join(":")}:#{jars.join(":")}:#{project_resource_dir}:#{project_lib_dir}")
  s.puts("export CLASSPATH=#{project_jar}:#{jars.join(":")}:#{project_resource_dir}")
end

