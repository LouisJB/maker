#!/usr/bin/ruby -w

require 'find'
puts `rm -rf out; mkdir out`
modules = ["utils", "plugin", "maker"]
source_files = []

modules.each do |m|
  Find.find("#{m}/src") do |f|
    if f =~ /\.scala$/ 
      source_files.unshift(f)
    end
  end
end

jars = Dir.glob("lib/*.jar")
puts jars

cmd = "fsc -classpath #{jars.join(":")} -d out/ #{source_files.join(" ")}"
puts cmd
puts `#{cmd}`
