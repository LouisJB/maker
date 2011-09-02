#!/usr/bin/ruby -w

require 'find'
jars = []
Find.find("jars") do |f|
  if f =~ /\.jar$/ then
    jars.unshift(f)
  end
end

File.open("set-classpath.sh", "w") do |s|
  s.puts("export CLASSPATH=resources/:out/:#{jars.join(":")}")
end

