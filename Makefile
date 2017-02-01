image_build:
	sudo docker build -t test_tron .
image_build_strict:
	sudo docker build --no-cache -t test_tron .
image_run: image_rm
	sudo docker run --name test_tron_instance -i -t test_tron
image_rm:
	sudo docker rm -f test_tron_instance | cat

up: image_build image_run
test: image_build_strict image_run image_rm

.PHONY: test 
