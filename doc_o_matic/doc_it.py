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

print ""
print "Outputting the documentation"

os.chdir("/docout")

for v in verSet:
    os.mkdir(slugify(v))

master = slurp("/newdata/funcatron/doc_o_matic/front_master.adoc")

version_master = slurp("/newdata/funcatron/doc_o_matic/version_master.adoc")

master = master.replace("$$VERSIONLIST$$", "\n".join(["* link:"+ slugify(line)+"/index.html["+line+"]" for line in verSet]))

spit("index.adoc", master)


for v in verSet:
    print "Working version ", v
    os.chdir("/newdata/funcatron")
    subprocess.call(["git", "reset", "--hard"])
    subprocess.call(["git", "checkout", "master"])
    projects = [p for p in repos if p in byVer[v]]
    for proj in projects:
        os.chdir("/newdata/"+proj)
        subprocess.call(["git", "reset", "--hard", "master"])
        res = subprocess.call(["git", "checkout", v])
        if res != 0:
            print "Failed to checkout branch ",v, " for project ", proj
            sys.exit(1)

    local_version_master = slurp("/newdata/funcatron/doc_o_matic/version_master.adoc")
    if local_version_master is None:
        local_version_master = version_master
    local_version_master = local_version_master.replace("$$VER$$", v)
    local_version_master = local_version_master.replace("$$PROJECTLIST$$",
                                                        "\n".join(["* link:"+ proj+"/index.html["+proj+"]" for
                                                                   proj in projects]))
    os.chdir("/docout/"+slugify(v))
    for proj in projects:
        os.mkdir(proj)
    spit("index.adoc", local_version_master)

print ""
print "Done spitting out the projects... now to asciidoctor them"

os.chdir("/docout")

os.system('asciidoctor -r asciidoctor-diagram $(find . -name "*.adoc")')

os.system("rm $(find . -name '*.adoc')")


print ""
print "Finished... making tarball"

os.chdir("/")

subprocess.call(["tar", "-czf", "/data/doc.tgz", "docout"])

stat_info = os.stat('/data/funcatron')
uid = stat_info.st_uid
gid = stat_info.st_gid

os.chown("/data/doc.tgz", uid, gid)
