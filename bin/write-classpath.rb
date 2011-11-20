#!/usr/bin/ruby -w

write_maker_classpath = ARGV[0] == '-m'
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
classpath="#{jars.join(":")}:#{project_resource_dir}:#{module_class_dirs.join(":")}"
if write_maker_classpath then
  claspath="out/:" + classpath
end
script_name = if write_maker_classpath then
  "set-maker-classpath.sh"
else
  "set-classpath.sh"
end

File.open(script_name, "w") do |s|
  s.puts("export CLASSPATH=#{classpath}")
end

