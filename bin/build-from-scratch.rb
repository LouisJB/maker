#ke!/usr/bin/ruby -w

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
  Find.find("#{m}/tests") do |f|
    if f =~ /\.scala$/ 
      source_files.unshift(f)
    end
  end
end
Find.find(".") do |f|
  if f =~ /\.class/ 
    File.delete(f)
  end
end


jars = Dir.glob("maker-lib/*.jar")
raise "fsc not found - set SCALA_HOME" unless File.exist?("#{ENV["SCALA_HOME"]}/bin/fsc")
cmd = "$SCALA_HOME/bin/fsc -classpath #{jars.join(":")} -d out/ #{source_files.join(" ")}"
puts cmd
puts `#{cmd}`
