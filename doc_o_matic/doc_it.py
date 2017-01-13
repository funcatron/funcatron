#!/usr/bin/env python

import os
import subprocess
import commands
import re

subprocess.call(["rm", "-rf", "/newdata"])
subprocess.call(["cp", "-r", "/data", "/newdata"])
os.environ["LEIN_ROOT"]="true"

repos = ["funcatron", "intf", "starter", "devshim", "tron", "samples", "jvm_services"]

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


def slurp(file):
    subprocess.call(["ls", "-la"])
    try:
        with open(file, "r") as text_file:
            return text_file.read()
    except IOError as e:
        print e
        return None

cwd = os.getcwd()

# Reset everything to master
for f in repos:
    os.chdir(f)
    subprocess.call(["git", "reset", "--hard"])
    subprocess.call(["git", "checkout", "master"])
    os.chdir(cwd)

ignore_tags = []

os.chdir("funcatron")

ignore_tags_file = slurp(".ignore_tags")

if not ignore_tags_file is None:
    ignore_tags = ignore_tags_file.splitlines()

os.chdir(cwd)

versions = {}

# look for the tags in each of the repos
for f in repos:
    os.chdir(f)
    [code, str] = commands.getstatusoutput("git tag")
    x = str.splitlines()
    t_set = set(x)
    for ignore in ignore_tags:
        if ignore in t_set:
            t_set.remove(ignore)
    versions[f] = t_set
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

subprocess.call(["asciidoctor", "-r", "asciidoctor-diagram", "$(", "find", ".", "-name", "'*.adoc'", ")"])

subprocess.call(["rm", "$(", "find", ".", "-name", "'*.adoc'", ")"])


os.chdir("/")

subprocess.call(["tar", "-czvf", "/data/doc.tgz", "docout"])
