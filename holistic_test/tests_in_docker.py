#!/usr/bin/env python

import os
import commands
import sys
import subprocess
from StringIO import StringIO
import xml.etree.ElementTree as ET
import re
import time
import requests


compile = True  # set to false to test pom stuff
# compile = False

def test_git_status():
    [code, str] = commands.getstatusoutput("git status -bs")

    if code != 0:
        print "Failed to get git status"
        sys.exit(code)

    str_lines = str.splitlines()

    if len(str_lines) > 1 or not str.startswith('## master...origin/master'):
        print "repository must be on master and fully committed"
        sys.exit(1)


def read_file(name):
    with open(name, 'r') as myfile:
        data = myfile.read()
    return data


def parse_xml(xml):
    it = ET.iterparse(StringIO(xml))
    for _, el in it:
        if '}' in el.tag:
            el.tag = el.tag.split('}', 1)[1]  # strip all namespaces
    root = it.root
    return root


def test_pom_deps(intf_ver, mod_name, only_deps=False):
    the_dir = os.getcwd() + '/pom.xml'
    the_file = read_file(the_dir)
    xml = parse_xml(the_file)

    my_ver = xml.find("./version").text

    intf_dep = [item.find("./version").text for item in xml.findall("./dependencies/dependency")
                if item.find("./groupId").text == 'funcatron']

    if my_ver != intf_ver and not only_deps:
        print "Module ", mod_name, " version ", my_ver, " not same as intf ", intf_ver, " failing"
        sys.exit(1)

    for v in intf_dep:
        if v != intf_ver:
            print "Module ", mod_name, " funcatron dependency ", v, " not same as intf ", intf_ver, " failing"
            sys.exit(1)

def check_gradle_version(ver, name):
    gradle = read_file("build.gradle").splitlines()
    func_line = [re.search('[0-9]+\.[0-9]+\.[0-9]', aline).group(0) for aline in gradle if "funcatron" in aline]

    for the_ver in func_line:
        if the_ver != ver:
            print "", name, " file needs updating from version ", the_ver, " to ", ver
            sys.exit(1)


### Begin actual code

os.environ['HOME'] = "/m2"

os.environ['MAVEN_OPTS'] = "-Dmaven.repo.local=/m2/repository"

os.chdir('/data/intf')

print("testing and installing intf")

test_git_status()

e = parse_xml(read_file('pom.xml'))

intf_ver = e.find("./version").text

print "We're working with version ", intf_ver

if compile:
    code = subprocess.call(
        ["mvn", "--global-settings", "/data/funcatron/holistic_test/settings.xml", "clean", "install"])
else:
    code = 0

if code != 0:
    print "Failed to install intf"
    sys.exit(code)

## Scala

print "Checking Scala sample"

os.chdir("/data/samples/scala")

test_git_status()

sbt = read_file("build.sbt").splitlines()

has_func = [re.search('[0-9]+\.[0-9]+\.[0-9]', line).group(0) for line in sbt if "funcatron" in line]

for num in has_func:
    if num != intf_ver:
        print "SBT file needs updating from version ", num, " to ", intf_ver
        sys.exit(1)

## Java Gradle

print "Checking Java Gradle sample"

os.chdir("/data/samples/java-gradle")

test_git_status()

check_gradle_version(intf_ver, "Java Gradle Sample")

## Groovy

print "Checking Groovy sample"

os.chdir("/data/samples/groovy")

test_git_status()

check_gradle_version(intf_ver, "Groovy Sample")

## Kotlin

print "Checking Kotlin sample"

os.chdir("/data/samples/kotlin")

test_git_status()

check_gradle_version(intf_ver, "Kotlin Sample")

print "Testing DevShim"

os.chdir("/data/devshim")

test_git_status()

test_pom_deps(intf_ver, "devshim")

if compile:
    code = subprocess.call(
        ["mvn", "--global-settings", "/data/funcatron/holistic_test/settings.xml", "clean", "install"])
else:
    code = 0

if code != 0:
    print "Failed to install devshim"
    sys.exit(code)

print "Testing starter project"

os.chdir("/data/starter")

test_git_status()

test_pom_deps(intf_ver, "starter")


os.chdir("/data/starter/src/main/resources/archetype-resources")

test_pom_deps(intf_ver, "starter archetype pom", only_deps=True)


os.chdir("/data/starter")


if compile:
    code = subprocess.call(
        ["mvn", "--global-settings", "/data/funcatron/holistic_test/settings.xml", "clean", "install"])
else:
    code = 0

if code != 0:
    print "Failed to install starter"
    sys.exit(code)

print "Testing tron"

os.chdir("/data/tron")

# get rid of any turds in the directory (thank you, NOT, maven)
subprocess.call(["rm", "-rf", "?"])

test_git_status()

proj = read_file('project.clj').splitlines()[0]

proj = re.search('[0-9]+\.[0-9]+\.[0-9]', proj).group(0)

if proj != intf_ver:
    print "Tron is at version ", proj, " which is not compatible with intf ", intf_ver
    sys.exit(1)

if compile:
    code = subprocess.call(["lein", "do", "clean,", "uberjar"])
else:
    code = 0

if code != 0:
    print "Failed to build tron"
    sys.exit(0)

tron_pid = subprocess.Popen(["java", "-jar", "target/uberjar/tron-" + proj + "-standalone.jar", "--tron"]).pid

runner_pid =  subprocess.Popen(["java", "-jar", "target/uberjar/tron-" + proj + "-standalone.jar", "--runner"]).pid

print "Sleeping for 15 seconds to allow the Tron and the Runner to start"
# wait for everything to wake up
time.sleep(15)

data = requests.get('http://localhost:3000/api/v1/stats')

if data.status_code != 200:
    print "Failed to talk to the Tron, status code ", data.status_code
    sys.exit(1)

print "Answer from the tron: ", data.json()

## Java Sample

print "Testing java sample"

os.chdir("/data/samples/java")

test_git_status()

test_pom_deps(intf_ver, "Java sample", True)

if compile:
    code = subprocess.call(
        ["mvn", "--global-settings", "/data/funcatron/holistic_test/settings.xml", "clean", "package"])
else:
    code = 0

if code != 0:
    print "Failed to package Java sample"
    sys.exit(code)

in_file = open("target/java_sample-0.1-SNAPSHOT-jar-with-dependencies.jar", "rb")  # opening for [r]eading as [b]inary
bytes = in_file.read()
in_file.close()

answer = requests.post('http://localhost:3000/api/v1/add_func', data=bytes)

if answer.status_code != 200:
    print "Failed to upload func bundle, status ", answer.status_code
    sys.exit(1)

uuid = answer.json()['sha']

answer = requests.post('http://localhost:3000/api/v1/enable', json={'sha': uuid, 'props': {"foo": "bar"}})

time.sleep(2)

if answer.status_code != 200:
    print "Failed to enable bundle, status ", answer.status_code
    sys.exit(1)

answer = requests.get("http://localhost:8680/sample/java/simple")

if answer.status_code != 200 or len(answer.json()["time"]) < 5:
    print "Got a bad answer from our app ", answer.status_code
    sys.exit(1)

## Clojure Sample

print "Testing Clojure sample"

os.chdir("/data/samples/clojure")

test_git_status()

proj_clj = read_file("project.clj").splitlines()

if not intf_ver in proj_clj[0]:
    print "Clojure project.clj file needs proper version set"
    sys.exit(1)

func_line = [re.search('[0-9]+\.[0-9]+\.[0-9]', aline).group(0) for aline in proj_clj
             if "funcatron" in aline and not ":url" in aline]

for the_ver in func_line:
    if the_ver != intf_ver:
        print "Clojure project.clj file needs updating from version ", num, " to ", ver
        sys.exit(1)
#
# if compile:
#     code = subprocess.call(["lein", "do", "clean,", "uberjar"])
# else:
#     code = 0
#
# if code != 0:
#     print "Failed to package Clojure sample"
#     sys.exit(code)
#
# in_file = open("target/clojure_sample-" + intf_ver + "-standalone.jar", "rb")  # opening for [r]eading as [b]inary
# bytes = in_file.read()
# in_file.close()
#
# answer = requests.post('http://localhost:3000/api/v1/add_func', data=bytes)
#
# if answer.status_code != 200:
#     print "Failed to upload func bundle, status ", answer.status_code
#     sys.exit(1)
#
# uuid = answer.json()['sha']
#
# answer = requests.post('http://localhost:3000/api/v1/enable', json={'sha': uuid, 'props': {"foo": "bar"}})
#
# time.sleep(2)
#
# if answer.status_code != 200:
#     print "Failed to enable bundle, status ", answer.status_code
#     sys.exit(1)
#
# answer = requests.get("http://localhost:8680/sample/clojure/simple")
#
# if answer.status_code != 200 or len(answer.json()["time"]) < 5:
#     print "Got a bad answer from our app ", answer.status_code
#     sys.exit(1)

## Test the archetype

print "Testing creation of and uploading a new project"

os.chdir("/m2")

code = subprocess.call(["mvn", "--global-settings", "/data/funcatron/holistic_test/settings.xml",
                        "archetype:generate", "-B", "-DarchetypeGroupId=funcatron", "-DarchetypeArtifactId=starter",
                        "-DarchetypeVersion=" + intf_ver, "-DgroupId=my.stellar", "-DartifactId=thang",
                        "-Dversion=0.1.0",
                        "-DarchetypeRepository=https://clojars.org/repo"])

if code != 0:
    print "Failed to create project from archetype", code
    sys.exit(code)

os.chdir("/m2/thang")

code = subprocess.call(["mvn", "--global-settings", "/data/funcatron/holistic_test/settings.xml", "clean", "package"])

if code != 0:
    print "Failed to package Java sample"
    sys.exit(code)

in_file = open("target/thang-0.1.0-jar-with-dependencies.jar", "rb")  # opening for [r]eading as [b]inary
bytes = in_file.read()
in_file.close()

answer = requests.post('http://localhost:3000/api/v1/add_func', data=bytes)

if answer.status_code != 200:
    print "Failed to upload func bundle, status ", answer.status_code
    sys.exit(1)

uuid = answer.json()['sha']

answer = requests.post('http://localhost:3000/api/v1/enable', json={'sha': uuid, 'props': {
    "db": {
        "type": "database",
        "classname": "org.h2.Driver",
        "url": "jdbc:h2:mem:db1"
    },

    "cache": {
        "type": "redis",
        "host": "localhost"
    }
}})

if answer.status_code != 200:
    print "Failed to enable bundle, status ", answer.status_code
    sys.exit(1)

time.sleep(2)

answer = requests.get("http://localhost:8680/api/sample")

if answer.status_code != 200 or len(answer.json()["name"]) < 5:
    print "Got a bad answer from our app ", answer.status_code
    sys.exit(1)

answer = requests.post("http://localhost:8680/api/sample", json={"name": "David", "age": 52})

if answer.status_code != 200 or answer.json()["age"] != 53:
    print "Got a bad answer from our app ", answer.status_code
    sys.exit(1)



os.kill(runner_pid, 9)
os.kill(tron_pid, 9)
