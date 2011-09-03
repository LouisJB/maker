#!/usr/bin/ruby -w

require 'find'
src_files = []
Find.find("src") do |f|
  if f =~ /.scala$/ then
    src_files.unshift(f)
  end
end
jars = `ls jars`.split("\n")

cmd = "scalac -cp #{jars.join(":")}:resources -d out #{src_files.join(" ")}"

puts cmd
puts `#{cmd}`
