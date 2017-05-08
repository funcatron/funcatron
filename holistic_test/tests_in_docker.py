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
import atexit
import sys
import getopt

try:
    opts, args = getopt.getopt(sys.argv[1:],"hcsv",["server="])
except getopt.GetoptError:
    print 'run_tests.sh [-h ][-c] [-v] [-s] [--server=http://localhost:8780]'
    sys.exit(2)

print 'Number of arguments:', len(sys.argv), 'arguments.'
print 'Argument List:', str(sys.argv)


subprocess.call(["rm", "-rf", "/newdata"])
subprocess.call(["mkdir", "/newdata"])
subprocess.call(["rm", "-rf", "/root/funcatron_bundles"])
subprocess.call(["cp", "-r", "/data/funcatron", "/newdata/"])
os.environ["LEIN_ROOT"]="true"

require_commit = True # set to False if we're in a development cycle
# require_commit = False

compile = True  # set to false to speed things up
# compile = False

http_server = 'http://localhost:8680' # might be different for different configs
# http_server = 'http://localhost:8780'

skip_scala = False

test_version = True # Make sure all the versions are correct

print "opts ", opts

for opt, arg in opts:
    if opt == "-c":
        require_commit = False
    elif opt == '-s':
        skip_scala = True
    elif opt == '-h':
        print 'run_tests.sh [-h ][-c] [-v] [--server=http://localhost:8780]'
        sys.exit(2)
    elif opt == "-v":
        test_version = False
    elif opt == "--server":
        http_server = arg

print "Running tests. Check commit: ", require_commit, " Check Version: ", test_version, " server ", http_server

def kill_java():
    subprocess.call(["killall", "java"])

atexit.register(kill_java)

def test_git_status():
    if not require_commit:
        return

    [code, str] = commands.getstatusoutput("git status -bs")

    if code != 0:
        print "Failed to get git status"
        sys.exit(code)

    str_lines = str.splitlines()

    if len(str_lines) > 1 or not str.startswith('## master...origin/master'):
        print str
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
    if not test_version:
        return

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
    if not test_version:
        return

    gradle = read_file("build.gradle").splitlines()
    func_line = [re.search('[0-9]+\.[0-9]+\.[0-9](-SNAPSHOT)?', aline).group(0) for aline in gradle if "funcatron" in aline]

    for the_ver in func_line:
        if the_ver != ver:
            print "", name, " file needs updating from version ", the_ver, " to ", ver
            sys.exit(1)

def test_http_sample(base_url_path):
    print "About to test sample"
    answer = requests.get(http_server + base_url_path + "/simple")

    print "Status code: ", answer.status_code

    if answer.status_code == 500:
        print "Trying one more time..."
        answer = requests.get(http_server + base_url_path + "/simple")
        print "Status code (#2): ", answer.status_code


    if answer.status_code != 200 or len(answer.json()["time"]) < 5:
        print "Got a bad answer from our app ", answer.status_code
        sys.exit(1)

def upload_and_enable(file_name, props):
    in_file = open(file_name, "rb")  # opening for [r]eading as [b]inary
    bytes = in_file.read()
    in_file.close()

    answer = requests.post('http://localhost:3000/api/v1/add_func', data=bytes)

    if answer.status_code != 200:
        print "Failed to upload func bundle, status ", answer.status_code
        sys.exit(1)

    uuid = answer.json()['sha']

    answer = requests.post('http://localhost:3000/api/v1/enable', json={'sha': uuid, 'props': props})

    time.sleep(2)

    if answer.status_code != 200:
        print "Failed to enable bundle, status ", answer.status_code
        sys.exit(1)

    return uuid


def compile_gradle(base_url_path, props = {}):
    if not compile:
        return

    code = subprocess.call(["./gradlew", "clean"])
    if code != 0:
        print "Failed Gradle Clean"
        sys.exit(code)

    code = subprocess.call(["./gradlew", "shadowJar"])
    if code != 0:
        print "Failed Gradle ShadowJar"
        sys.exit(code)

    file_name = "build/libs/" + [f for f in os.listdir('build/libs') if f.endswith('-all.jar')][0]

    uuid = upload_and_enable(file_name, props)

    test_http_sample(base_url_path)

    return uuid


### Begin actual code
def test_intf():
    os.chdir('/newdata/funcatron/intf')

    print("testing and installing intf")

    test_git_status()

    e = parse_xml(read_file('pom.xml'))

    intf_ver = e.find("./version").text

    print "We're working with version ", intf_ver

    if compile:
        code = subprocess.call(["mvn", "clean", "install"])
    else:
        code = 0

    if code != 0:
        print "Failed to install intf"
        sys.exit(code)

    return intf_ver

## Scala

def test_scala(intf_ver):
    print "Checking Scala sample"

    os.chdir("/newdata/funcatron/samples/scala")

    test_git_status()

    if test_version:

        sbt = read_file("build.sbt").splitlines()

        has_func = [re.search('[0-9]+\.[0-9]+\.[0-9](-SNAPSHOT)?', line).group(0) for line in sbt if "funcatron" in line]

        for num in has_func:
            if num != intf_ver:
                print "SBT file needs updating from version ", num, " to ", intf_ver
                sys.exit(1)

    if skip_scala:
        return ""

    code = subprocess.call(["sbt", "-v", "clean", "assembly"])
    if code != 0:
        print "Failed to compile sbt"
        sys.exit(code)

    uuid = upload_and_enable("target/scala-2.11/scala_sample-assembly-1.0.jar", {})

    test_http_sample("/sample/scala")

    return uuid


## Java Gradle

def test_java_gradle(intf_ver):
    print "Checking Java Gradle sample"

    os.chdir("/newdata/funcatron/samples/java-gradle")

    test_git_status()

    check_gradle_version(intf_ver, "Java Gradle Sample")

    compile_gradle("/sample/java_gradle")


## Groovy

def test_groovy(intf_ver):
    print "Checking Groovy sample"

    os.chdir("/newdata/funcatron/samples/groovy")

    test_git_status()

    check_gradle_version(intf_ver, "Groovy Sample")

    compile_gradle("/sample/groovy")

## Front End

def test_frontend_version(intf_ver):
    if not test_version:
        return

    print "Testing front end version"
    os.chdir("/newdata/funcatron/frontend")

    test_git_status()

    fel = read_file("funcatron.lua").splitlines()

    ver_line = [re.search('[0-9]+\.[0-9]+\.[0-9](-SNAPSHOT)?', line).group(0) for line in fel if line.startswith("funcatron.version")][0]

    if ver_line != intf_ver:
        print "Frontend wrong version ", ver_line
        sys.exit(1)

## Kotlin
def test_kotlin(intf_ver):
    print "Checking Kotlin sample"

    os.chdir("/newdata/funcatron/samples/kotlin")

    test_git_status()

    check_gradle_version(intf_ver, "Kotlin Sample")

    compile_gradle("/sample/kotlin")


def test_dev_shim(intf_ver):
    print "Testing DevShim"

    os.chdir("/newdata/funcatron/devshim")

    test_git_status()

    test_pom_deps(intf_ver, "devshim")

    if compile:
        code = subprocess.call(["mvn", "clean", "install"])
    else:
        code = 0

    if code != 0:
        print "Failed to install devshim"
        sys.exit(code)

def test_starter(intf_ver):
    print "Testing starter project"

    os.chdir("/newdata/funcatron/starter")

    test_git_status()

    test_pom_deps(intf_ver, "starter")


    os.chdir("/newdata/funcatron/starter/src/main/resources/archetype-resources")

    test_pom_deps(intf_ver, "starter archetype pom", only_deps=True)


    os.chdir("/newdata/funcatron/starter")


    if compile:
        code = subprocess.call(["mvn", "clean", "install"])
    else:
        code = 0

    if code != 0:
        print "Failed to install starter"
        sys.exit(code)

def test_tron(intf_ver):
    print "Testing tron"

    os.chdir("/newdata/funcatron/tron")

    test_git_status()

    proj = read_file('project.clj').splitlines()[0]

    proj = re.search('[0-9]+\.[0-9]+\.[0-9](-SNAPSHOT)?', proj).group(0)

    if test_version and proj != intf_ver:
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

    return tron_pid, runner_pid


## Java Sample
def test_java_sample(intf_ver):
    print "Testing java sample"

    os.chdir("/newdata/funcatron/samples/java")

    test_git_status()

    test_pom_deps(intf_ver, "Java sample", True)

    if compile:
        code = subprocess.call(["mvn", "clean", "package"])
    else:
        code = 0

    if code != 0:
        print "Failed to package Java sample"
        sys.exit(code)

    upload_and_enable("target/java_sample-0.1-SNAPSHOT-jar-with-dependencies.jar", {"foo": "bar"})

    test_http_sample("/sample/java")

## Java Spring Boot Sample
def test_spring_boot_sample(intf_ver):
    print "Testing java spring boot sample"

    os.chdir("/newdata/funcatron/samples/java-spring")

    test_git_status()

    test_pom_deps(intf_ver, "Java Spring boot sample", True)

    if compile:
        code = subprocess.call(["mvn", "clean", "package"])
    else:
        code = 0

    if code != 0:
        print "Failed to package Java spring boot sample"
        sys.exit(code)

    print "About to upload and enable sprint boot app"

    upload_and_enable("target/java_spring_sample-0.1-SNAPSHOT.jar", {"foo": "bar"})

    test_http_sample("/greeting")

## Clojure Sample
def test_clojure_sample(intf_ver):
    print "Testing Clojure sample"

    os.chdir("/newdata/funcatron/samples/clojure")

    test_git_status()

    proj_clj = read_file("project.clj").splitlines()

    if not intf_ver in proj_clj[0]:
        print "Clojure project.clj file needs proper version set"
        sys.exit(1)

    func_line = [re.search('[0-9]+\.[0-9]+\.[0-9](-SNAPSHOT)?', aline).group(0) for aline in proj_clj
                 if "funcatron" in aline and not ":url" in aline]

    for the_ver in func_line:
        if test_version and the_ver != intf_ver:
            print "Clojure project.clj file needs updating from version ", num, " to ", ver
            sys.exit(1)

    if compile:
        code = subprocess.call(["lein", "do", "clean,", "uberjar"])
    else:
        code = 0

    if code != 0:
        print "Failed to package Clojure sample"
        sys.exit(code)

    upload_and_enable("target/clojure_sample-" + intf_ver + "-standalone.jar", {"foo": "bar"})

    test_http_sample("/sample/clojure")

## Test the archetype
def test_archetype(intf_ver):
    print "Testing creation of and uploading a new project"

    os.chdir("/newdata")

    code = subprocess.call(["mvn",
                            "archetype:generate", "-B", "-DarchetypeGroupId=funcatron", "-DarchetypeArtifactId=starter",
                            "-DarchetypeVersion=" + intf_ver, "-DgroupId=my.stellar", "-DartifactId=thang",
                            "-Dversion=0.1.0",
                            "-DarchetypeRepository=https://clojars.org/repo"])

    if code != 0:
        print "Failed to create project from archetype", code
        sys.exit(code)

    os.chdir("/newdata/thang")

    code = subprocess.call(["mvn", "clean", "package"])

    if code != 0:
        print "Failed to package Java sample"
        sys.exit(code)

    upload_and_enable("target/thang-0.1.0-jar-with-dependencies.jar", {
        "db": {
            "type": "database",
            "classname": "org.h2.Driver",
            "url": "jdbc:h2:mem:db1"
        },

        "cache": {
            "type": "redis",
            "host": "localhost"
        }
    })

    answer = requests.get(http_server + "/api/sample")

    if answer.status_code != 200 or len(answer.json()["name"]) < 5:
        print "Got a bad answer from our app ", answer.status_code
        sys.exit(1)

    answer = requests.post(http_server + "/api/sample", json={"name": "David", "age": 52})

    if answer.status_code != 200 or answer.json()["age"] != 53:
        print "Got a bad answer from our app ", answer.status_code
        sys.exit(1)

def test_devmode(intf_ver):
    print "Testing dev mode"
    os.chdir("/newdata/thang")

    print "Firing up tron in dev mode"
    tron_pid = subprocess.Popen(["java", "-jar", "/newdata/funcatron/tron/target/uberjar/tron-" + intf_ver +
                                 "-standalone.jar", "--devmode"]).pid
    time.sleep(5)

    print "Firing up Maven exec mode"
    app_pid = subprocess.Popen(["mvn", "compile", "exec:java"]).pid

    time.sleep(5)
    answer = requests.get("http://localhost:3000/api/sample")

    if answer.status_code != 200 or len(answer.json()["name"]) < 5:
        print "Got a bad answer from our dev-time app ", answer.status_code
        sys.exit(1)

    os.kill(tron_pid, 9)
    os.kill(app_pid, 9)

def test_clojure_service(intf_ver):
    print "Testing Clojure Service"

    os.chdir("/newdata/funcatron/jvm_services/clojure")

    test_git_status()

    test_pom_deps(intf_ver, "Clojure Service")

    if compile:
        code = subprocess.call(["mvn", "clean", "install"])
    else:
        code = 0

    if code != 0:
        print "Failed to install Clojure Service"
        sys.exit(code)



def test_spring_boot_service(intf_ver):
    print "Testing Spring Boot Service"

    os.chdir("/newdata/funcatron/jvm_services/spring_boot")

    test_git_status()

    test_pom_deps(intf_ver, "Spring Boot Service")

    if compile:
        code = subprocess.call(["mvn", "clean", "install"])
    else:
        code = 0

    if code != 0:
        print "Failed to install Spring Boot Service"
        sys.exit(code)

def run_tests():

    # Test the core pieces
    intf_ver = test_intf()

    # Test Front end version
    test_frontend_version(intf_ver)

    # Test the jvm_services
    test_clojure_service(intf_ver)
    test_spring_boot_service(intf_ver)

    # Test other pieces
    test_dev_shim(intf_ver)
    test_starter(intf_ver)

    # Test the Tron and start the tron
    [tron_pid, runner_pid] = test_tron(intf_ver)

    # And the Archetype
    test_archetype(intf_ver)

    # now that the Tron is up, let's test the various samples
    test_spring_boot_sample(intf_ver)
    test_clojure_sample(intf_ver)
    test_scala(intf_ver)
    test_java_gradle(intf_ver)
    test_groovy(intf_ver)
    test_kotlin(intf_ver)
    test_java_sample(intf_ver)


    os.kill(runner_pid, 9)
    os.kill(tron_pid, 9)

    test_devmode(intf_ver)

run_tests()

print ""
print "*********"
print "Successfully Ran All Tests!"
print ""
