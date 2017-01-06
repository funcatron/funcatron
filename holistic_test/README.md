# Holistic Test

A Docker container and supporting scripts to

* Takes the parent directory of the Funcatron projects
* Ensure's there's no uncommitted code in the projects
* installs intf
* installs starter
* installs devshim
* Builds tron
* starts a Tron instance and a Runner instance
* builds each of the samples and uploads them to the tron
* Tests that each sample runs
* Creates a project with starter
* Compiles and uploads the starter project and insures it can run
* Kills the Tron and Runner instances
* Starts a dev-mode Tron instance
* Ensures that starter project works in devmode

