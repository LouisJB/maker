#!/usr/bin/ruby -w

scala_home=ENV["SCALA_HOME"] || "/usr/local/scala/"
fsc = scala_home + "/bin/fsc"
require 'find'
src_files = []
["src", "tests"].each do |dir|
  Find.find(dir) do |f|
    if f =~ /.scala$/ then
      src_files.unshift(f)
    end
  end
end
jars = `ls jars`.split("\n").collect{|jar| "jars/#{jar}"}

cmd = "#{fsc} -cp #{jars.join(":")}:resources -d out #{src_files.join(" ")}"

puts cmd
puts `#{cmd}`
