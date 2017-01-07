#!/usr/bin/env python

import os
import subprocess
import commands
import re

subprocess.call(["rm", "-rf", "/newdata"])
subprocess.call(["cp", "-r", "/data", "/newdata"])
os.environ["LEIN_ROOT"]="true"

os.chdir("/newdata")

# From http://stackoverflow.com/questions/295135/turn-a-string-into-a-valid-filename
def slugify(value):
    """
    Normalizes string, converts to lowercase, removes non-alpha characters,
    and converts spaces to hyphens.
    """
    value = re.sub('[^\w\s-]', '', value).strip().lower()
    value = re.sub('[-\s]+', '-', value)
    return value

def spit(file, value):
    """
    Save a value into a file
    :param file: the file
    :param value:  the value
    :return:
    """
    with open(file, "w") as text_file:
        text_file.write(value)

files = [f for f in os.listdir(".") if not f.startswith(".") and
         os.path.isdir(f) and
         os.path.isdir(f+"/.git")]

cwd = os.getcwd()

# Reset everything to master
for f in files:
    os.chdir(f)
    subprocess.call(["git", "reset", "--hard"])
    subprocess.call(["git", "checkout", "master"])
    os.chdir(cwd)

# look for directories with '.docignore' and don't include them
files = [f for f in files if not os.path.isfile(f + "/.docignore")]

versions = {}

# look for the tags in each of the repos
for f in files:
    os.chdir(f)
    [code, str] = commands.getstatusoutput("git tag")
    x = str.splitlines()
    versions[f] = set(x)
    os.chdir(cwd)

# What are all the versions?
verSet = set([])

for v in versions.values():
    verSet |= v

verSet = list(verSet)

verSet.sort()

# Reverse sorted with "master" first
verSet.reverse()

verSet.insert(0, "master")

# Now, figure out all the repos that have a particular version
byVer = {}

for ver in verSet:
    lst = []
    byVer[ver] = lst
    for [k, v] in versions.items():
        if ver == 'master' or ver in v:
            lst.append(k)

print versions

print verSet

print byVer

subprocess.call(["rm", "-rf", "/docout"])
subprocess.call(["mkdir", "/docout"])

os.chdir("/docout")
for v in verSet:
    os.mkdir(slugify(v))

