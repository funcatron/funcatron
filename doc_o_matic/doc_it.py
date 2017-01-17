#!/usr/bin/env python

import os
import subprocess
import commands
import re
import errno

subprocess.call(["rm", "-rf", "/newdata"])
subprocess.call(["cp", "-r", "/data", "/newdata"])
os.environ["LEIN_ROOT"] = "true"

repos = ["funcatron", "intf", "starter", "devshim", "tron", "samples", "jvm_services"]

# Funcatron
## Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs

# intf
## Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs
## run JavaDocs and link to front matter doc

# Starter
## Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs

# Devshim
## Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs
## run JavaDocs and link to front matter doc

# Tron
## Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs
## run codox and link to front matter doc

# Samples
## Front matter that links to each sample. For each Sample:
### Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs
### run JavaDocs and link to front matter doc

# jvm_services
## Front matter that links to each sample. For each Sample:
### Find all .md and .adoc documents, Change slugified names (e.g., dev_intro) into better names (e.g., Dev Intro) and link from a front-matter doc to each of the docs
### run JavaDocs and link to front matter doc

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
        return None


def find_all_doc_files(root_dir):
    """
    In the current directory, return all the 
    :return: a list of all the documentation files in the directories
    """
    return [[dir, x, slurp(os.path.join(dir, x))] for
            dir, sub, files in os.walk(root_dir) for
            x in files
            if (x not in (slurp(dir + "/" + ".docignore") or "")) and
            (x.endswith(".md") or x.endswith(".adoc"))]


def find_frontmatter(doc_files, generic):
    """
    Given all the doc files, find the frontmatter.adoc file and return its contents or
    return the generic file
    :param doc_files: the list of files from find_all_doc_files
    :param generic: the generic frontmatter
    :return:
    """
    fm = [x for x in doc_files if x[1] == 'frontmatter.adoc']
    ret = None
    if fm:
        x = fm[0]
        ret = slurp(os.path.join(x[0], x[1]))

    return ret or generic


def slugified_to_nice(name):
    """
    Take a slugified filename like "dev_info.adoc" and turn it into a nice
    string like "Dev Info"
    :param name: the slugified thing
    :return: a nicer version
    """
    name = re.sub("\.\/info\/", "", name)
    name = re.sub("\.\/doc\/", "", name)
    return re.sub("[./\-_]", " ", re.sub("\.[^ .]*$", "", name)).lower().strip().title()

def end_with_html(name):
    """
    change the file extension to .html
    :param name: the thing with a file extension
    :return: the file.html
    """
    return re.sub("\.[^ .]*$", "", name) + ".html"

# http://stackoverflow.com/questions/600268/mkdir-p-functionality-in-python
def mkdir_p(path):
    try:
        os.makedirs(path)
    except OSError as exc:  # Python >2.5
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise

def path_and_file(file):
    """
    Split a file into a path and a filename
    :param file: the filename
    :return: [path, filename]
    """
    split = re.split("/", file)
    return "/".join(split[:-1]), split[-1]

def split_list(the_func, the_list):
    """
    Splits a list into two... one that matches the function, the other that doesn't
    :param the_func: the test function
    :param the_list: the input lsit
    :return: the first list matches the predicate, the second does
    """
    yes, no = [], []

    for x in the_list:
        if the_func(x):
            yes.append(x)
        else:
            no.append(x)

    return yes, no

def emit_proj_info(proj_name, source_dir, dest_dir, default_frontmatter):
    """
    Do all the frontmatter stuff for a source and dest dir

    :param proj_name: The name of the project
    :param source_dir: where to gather info
    :param dest_dir: where to spit out the files
    :param default_frontmatter: the default frontmatter file
    :return:
    """
    os.chdir(source_dir)
    to_copy = slurp(".doccopy") or ""

    files = find_all_doc_files(".")
    fm = find_frontmatter(files, default_frontmatter)
    fm = fm.replace("$$PROJ$$", proj_name.title())

    readme, files = split_list(lambda x: x[0] == "." and x[1].lower() == "readme.adoc", files)

    files = filter(lambda x: x[1] not in ["frontmatter.adoc"], files)

    os.chdir(dest_dir)

    for cp in to_copy.splitlines():
        print "cp is ", cp
        path, file = path_and_file(cp)
        mkdir_p(path)
        subprocess.call(["cp", source_dir + "/" + cp, cp])

    for dir, name, contents in files:
        mkdir_p(dir)
        spit(os.path.join(dir, name), contents)

    readme_text = "Project Information"

    if readme:
        readme_text = readme[0][2]

    spit("index.adoc",
    fm.replace("$$DOCLINKS$$",
    "\n".join(["* link:"+os.path.join(dir, end_with_html(file))+"["+slugified_to_nice(os.path.join(dir, file))+"]" for
               dir, file, q in files])).
         replace("$$README$$", readme_text))

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

print "Version"
print versions

print ""
print "Version Set"
print verSet

print ""
print "By Version"
print byVer

subprocess.call(["rm", "-rf", "/docout"])
subprocess.call(["mkdir", "/docout"])

print ""
print "Outputting the documentation"

os.chdir("/docout")

for v in verSet:
    os.mkdir(slugify(v))

version_master = slurp("/newdata/funcatron/doc_o_matic/version_master.adoc")

generic_frontmatter = slurp("/newdata/funcatron/doc_o_matic/generic_frontmatter.adoc")

generic_multilevel_frontmatter = slurp("/newdata/funcatron/doc_o_matic/generic_multilevel_frontmatter.adoc")

master = slurp("/newdata/funcatron/doc_o_matic/front_master.adoc")

master = master.replace("$$VERSIONLIST$$",
                        "\n".join(["* link:" + slugify(line) + "/index.html[" + line + "]" for line in verSet]))

spit("index.adoc", master)

for v in verSet:
    print "Working version ", v
    slug_v = slugify(v)
    os.chdir("/newdata/funcatron")
    subprocess.call(["git", "reset", "--hard"])
    subprocess.call(["git", "checkout", "master"])
    projects = [p for p in repos if p in byVer[v]]
    for proj in projects:
        os.chdir("/newdata/" + proj)
        subprocess.call(["git", "reset", "--hard", "master"])
        res = subprocess.call(["git", "checkout", v])
        if res != 0:
            print "Failed to checkout branch ", v, " for project ", proj
            sys.exit(1)

    local_version_master = slurp("/newdata/funcatron/doc_o_matic/version_master.adoc")
    if local_version_master is None:
        local_version_master = version_master
    local_version_master = local_version_master.replace("$$VER$$", v)
    local_version_master = local_version_master.replace("$$PROJECTLIST$$",
                                                        "\n".join(["* link:" + proj + "/index.html[" + proj + "]" for
                                                                   proj in projects]))
    os.chdir("/docout/" + slug_v)
    for proj in projects:
        os.mkdir(proj)

    spit("index.adoc", local_version_master)

    for proj in projects:
        emit_proj_info(proj, "/newdata/" + proj, "/docout/" + slug_v + "/" + proj, generic_frontmatter)

print ""
print "Done spitting out the projects... now to asciidoctor them"

os.chdir("/docout")

os.system('asciidoctor -a source-highlighter=pygments -r asciidoctor-diagram $(find . -name "*.adoc") ')

print ""
print "And running Markdown..."

for dir, sub, files in os.walk("."):
    for file in files:
        if file.endswith(".md"):
            subprocess.call(["pandoc", "-f", "markdown_github", "-s", "-o", os.path.join(dir, end_with_html(file)),
            os.path.join(dir, file)])

os.system("rm $(find . -name '*.adoc') $(find . -name '*.md') ")

print ""
print "Finished... making tarball"

os.chdir("/")

subprocess.call(["tar", "-czf", "/data/doc.tgz", "docout"])

stat_info = os.stat('/data/funcatron')
uid = stat_info.st_uid
gid = stat_info.st_gid

os.chown("/data/doc.tgz", uid, gid)
